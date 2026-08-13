package io.github.miklires.mauth.email;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.PasswordValidator;
import io.github.miklires.mauth.auth.SecretProtector;
import io.github.miklires.mauth.model.Account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class EmailRecoveryService {

    private static final Pattern ADDRESS = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$", Pattern.CASE_INSENSITIVE);
    private static final char[] CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final MAuth plugin;
    private final SecretProtector protector;
    private final SmtpClient smtp;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Pending> verification = new ConcurrentHashMap<>();
    private final Map<String, Pending> recovery = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    public EmailRecoveryService(MAuth plugin) {
        this.plugin = plugin;
        protector = new SecretProtector(plugin);
        smtp = new SmtpClient(plugin.getConfigManager());
    }

    public Status requestVerification(Account account, String rawAddress) {
        if (!plugin.getConfigManager().isEmailEnabled()) return Status.DISABLED;
        String address = rawAddress.trim().toLowerCase(Locale.ROOT);
        if (address.length() > 254 || !ADDRESS.matcher(address).matches()) return Status.INVALID_ADDRESS;
        String key = "verify:" + account.getUsername();
        if (limited(key)) return Status.RATE_LIMITED;
        String code = code();
        try {
            smtp.send(address, plugin.getConfigManager().getEmailVerificationSubject(),
                    plugin.getConfigManager().getEmailVerificationBody().replace("<code>", code));
            verification.put(account.getUsername(), new Pending(hash(code), address, expiresAt()));
            lastSent.put(key, System.currentTimeMillis());
            return Status.SENT;
        } catch (Exception e) {
            plugin.getLogger().warning("email verification failed: " + e.getMessage());
            return Status.SEND_FAILED;
        }
    }

    public Status confirmVerification(Account account, String code) {
        Pending pending = verification.get(account.getUsername());
        Status status = check(pending, code);
        if (status != Status.CONFIRMED) return status;
        account.setEmail(protector.encrypt(pending.address));
        account.setEmailVerified(true);
        try {
            plugin.getAccountRepository().update(account);
            verification.remove(account.getUsername());
            return Status.CONFIRMED;
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on email verification: " + e.getMessage());
            return Status.DB_ERROR;
        }
    }

    public Status remove(Account account) {
        account.setEmail(null);
        account.setEmailVerified(false);
        try {
            plugin.getAccountRepository().update(account);
            verification.remove(account.getUsername());
            recovery.remove(account.getUsername());
            return Status.REMOVED;
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on email removal: " + e.getMessage());
            return Status.DB_ERROR;
        }
    }

    public Status requestRecovery(String rawUsername) {
        if (!plugin.getConfigManager().isEmailEnabled()) return Status.DISABLED;
        String username = rawUsername.toLowerCase(Locale.ROOT);
        String key = "recover:" + username;
        if (limited(key)) return Status.ACCEPTED;
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty() || !opt.get().isEmailVerified() || opt.get().getEmail() == null) {
                return Status.ACCEPTED;
            }
            String code = code();
            String address = protector.decrypt(opt.get().getEmail());
            smtp.send(address, plugin.getConfigManager().getEmailRecoverySubject(),
                    plugin.getConfigManager().getEmailRecoveryBody()
                            .replace("<player>", opt.get().getRegisteredName())
                            .replace("<code>", code));
            recovery.put(username, new Pending(hash(code), null, expiresAt()));
            lastSent.put(key, System.currentTimeMillis());
            return Status.ACCEPTED;
        } catch (Exception e) {
            plugin.getLogger().warning("email recovery failed: " + e.getMessage());
            return Status.ACCEPTED;
        }
    }

    public ResetResult confirmRecovery(String rawUsername, String code, String password) {
        String username = rawUsername.toLowerCase(Locale.ROOT);
        Status token = check(recovery.get(username), code);
        if (token != Status.CONFIRMED) return new ResetResult(token, null);
        PasswordValidator.Result validation = plugin.getPasswordValidator().validate(password, username);
        if (validation != PasswordValidator.Result.OK) return new ResetResult(Status.INVALID_PASSWORD, validation);
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty()) return new ResetResult(Status.INVALID_CODE, null);
            Account account = opt.get();
            account.setPasswordHash(plugin.getPasswordHasher().hash(password));
            account.setLastPasswordResetAt(Instant.now());
            plugin.getAccountRepository().update(account);
            plugin.getSessionRepository().invalidate(username);
            plugin.getSessionManager().clearAccount(username);
            recovery.remove(username);
            return new ResetResult(Status.RESET, null);
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on email recovery: " + e.getMessage());
            return new ResetResult(Status.DB_ERROR, null);
        }
    }

    private Status check(Pending pending, String code) {
        if (pending == null) return Status.INVALID_CODE;
        if (pending.expiresAt < System.currentTimeMillis()) return Status.EXPIRED;
        return MessageDigest.isEqual(pending.codeHash, hash(code.trim().toUpperCase(Locale.ROOT)))
                ? Status.CONFIRMED : Status.INVALID_CODE;
    }

    private boolean limited(String key) {
        Long sent = lastSent.get(key);
        return sent != null && System.currentTimeMillis() - sent
                < plugin.getConfigManager().getEmailRateLimitSeconds() * 1000L;
    }

    private long expiresAt() {
        return System.currentTimeMillis() + plugin.getConfigManager().getEmailCodeTtlSeconds() * 1000L;
    }

    private String code() {
        StringBuilder value = new StringBuilder(8);
        for (int i = 0; i < 8; i++) value.append(CODE_CHARS[random.nextInt(CODE_CHARS.length)]);
        return value.toString();
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public enum Status {
        SENT, CONFIRMED, REMOVED, RESET, ACCEPTED, DISABLED, INVALID_ADDRESS,
        INVALID_CODE, INVALID_PASSWORD, EXPIRED, RATE_LIMITED, SEND_FAILED, DB_ERROR
    }

    public record ResetResult(Status status, PasswordValidator.Result passwordResult) {}

    private record Pending(byte[] codeHash, String address, long expiresAt) {}
}
