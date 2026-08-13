package io.github.miklires.mauth.auth;

import io.github.miklires.mauth.model.Account;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecretProtector protector;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<UUID, Setup> setups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PendingLogin> logins = new ConcurrentHashMap<>();

    public TotpService(SecretProtector protector) {
        this.protector = protector;
    }

    public SetupView startSetup(UUID playerId, Account account) {
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        String encoded = encodeBase32(secret);
        List<String> codes = recoveryCodes();
        setups.put(playerId, new Setup(account, encoded, codes,
                System.currentTimeMillis() + 300_000L));
        return new SetupView(encoded, List.copyOf(codes));
    }

    public Account confirmSetup(UUID playerId, String code) {
        Setup setup = setups.get(playerId);
        if (setup == null || setup.expiresAt < System.currentTimeMillis()) {
            setups.remove(playerId);
            return null;
        }
        if (!verifyTotp(setup.secret, code, System.currentTimeMillis())) return null;
        setups.remove(playerId);
        setup.account.setTotpSecret(protector.encrypt(setup.secret));
        setup.account.setRecoveryCodes(hashCodes(setup.recoveryCodes));
        return setup.account;
    }

    public void beginLogin(UUID playerId, Account account) {
        logins.put(playerId, new PendingLogin(account, System.currentTimeMillis() + 120_000L, 5));
    }

    public List<String> regenerateRecoveryCodes(Account account) {
        List<String> codes = recoveryCodes();
        account.setRecoveryCodes(hashCodes(codes));
        return List.copyOf(codes);
    }

    public LoginVerification verifyLogin(UUID playerId, String code) {
        PendingLogin pending = logins.get(playerId);
        if (pending == null || pending.expiresAt < System.currentTimeMillis()) {
            logins.remove(playerId);
            return new LoginVerification(VerifyStatus.EXPIRED, null);
        }
        String secret = protector.decrypt(pending.account.getTotpSecret());
        if (verifyTotp(secret, code, System.currentTimeMillis())) {
            logins.remove(playerId);
            return new LoginVerification(VerifyStatus.SUCCESS, pending.account);
        }
        if (consumeRecoveryCode(pending.account, code)) {
            logins.remove(playerId);
            return new LoginVerification(VerifyStatus.RECOVERY_USED, pending.account);
        }
        pending.attempts--;
        if (pending.attempts <= 0) {
            logins.remove(playerId);
            return new LoginVerification(VerifyStatus.LOCKED, null);
        }
        return new LoginVerification(VerifyStatus.WRONG, null);
    }

    public boolean hasPendingLogin(UUID playerId) {
        PendingLogin pending = logins.get(playerId);
        return pending != null && pending.expiresAt >= System.currentTimeMillis();
    }

    public void clear(UUID playerId) {
        setups.remove(playerId);
        logins.remove(playerId);
    }

    public boolean verifyTotp(String secret, String code, long nowMillis) {
        if (code == null || !code.matches("\\d{6}")) return false;
        byte[] key = decodeBase32(secret);
        long counter = nowMillis / 30_000L;
        int supplied = Integer.parseInt(code);
        for (long offset = -1; offset <= 1; offset++) {
            if (generate(key, counter + offset) == supplied) return true;
        }
        return false;
    }

    private int generate(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = (hash[offset] & 0x7f) << 24
                    | (hash[offset + 1] & 0xff) << 16
                    | (hash[offset + 2] & 0xff) << 8
                    | hash[offset + 3] & 0xff;
            return binary % 1_000_000;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean consumeRecoveryCode(Account account, String supplied) {
        String normalized = supplied == null ? "" : supplied.replace("-", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z2-9]{10}")) return false;
        String hash = digest(normalized);
        List<String> hashes = new ArrayList<>();
        if (account.getRecoveryCodes() != null && !account.getRecoveryCodes().isBlank()) {
            hashes.addAll(List.of(account.getRecoveryCodes().split(",")));
        }
        boolean removed = hashes.removeIf(value -> MessageDigest.isEqual(
                value.getBytes(StandardCharsets.US_ASCII), hash.getBytes(StandardCharsets.US_ASCII)));
        if (removed) account.setRecoveryCodes(String.join(",", hashes));
        return removed;
    }

    private List<String> recoveryCodes() {
        List<String> result = new ArrayList<>();
        while (result.size() < 8) {
            byte[] value = new byte[7];
            random.nextBytes(value);
            String raw = encodeBase32(value).substring(0, 10);
            String code = raw.substring(0, 5) + "-" + raw.substring(5);
            if (!result.contains(code)) result.add(code);
        }
        return result;
    }

    private String hashCodes(List<String> codes) {
        return codes.stream()
                .map(code -> digest(code.replace("-", "")))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String encodeBase32(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = buffer << 8 | value & 0xff;
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(BASE32.charAt(buffer >> bits & 31));
            }
        }
        if (bits > 0) result.append(BASE32.charAt(buffer << 5 - bits & 31));
        return result.toString();
    }

    private byte[] decodeBase32(String value) {
        String normalized = value.replace("=", "").toUpperCase(Locale.ROOT);
        byte[] result = new byte[normalized.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : normalized.toCharArray()) {
            int digit = BASE32.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("invalid base32 secret");
            buffer = buffer << 5 | digit;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                result[index++] = (byte) (buffer >> bits);
            }
        }
        return result;
    }

    public record SetupView(String secret, List<String> recoveryCodes) {
    }

    public record LoginVerification(VerifyStatus status, Account account) {
    }

    public enum VerifyStatus {
        SUCCESS, RECOVERY_USED, WRONG, EXPIRED, LOCKED
    }

    private record Setup(Account account, String secret, List<String> recoveryCodes, long expiresAt) {
    }

    private static class PendingLogin {
        final Account account;
        final long expiresAt;
        int attempts;

        PendingLogin(Account account, long expiresAt, int attempts) {
            this.account = account;
            this.expiresAt = expiresAt;
            this.attempts = attempts;
        }
    }
}
