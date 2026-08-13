package io.github.miklires.mauth.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

public class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String algorithm;
    private final int bcryptCost;
    private final int memory;
    private final int iterations;
    private final int parallelism;

    public PasswordHasher(String algorithm, int bcryptCost, int memory, int iterations, int parallelism) {
        this.algorithm = algorithm.toLowerCase();
        if (!this.algorithm.equals("argon2id") && !this.algorithm.equals("bcrypt")) {
            throw new IllegalArgumentException("password algorithm must be argon2id or bcrypt");
        }
        if (bcryptCost < 4 || bcryptCost > 31) {
            throw new IllegalArgumentException("bcrypt cost must be 4-31, got " + bcryptCost);
        }
        if (memory < 8192 || iterations < 1 || parallelism < 1) {
            throw new IllegalArgumentException("invalid argon2 parameters");
        }
        this.bcryptCost = bcryptCost;
        this.memory = memory;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    public String hash(String plain) {
        if (algorithm.equals("bcrypt")) {
            return BCrypt.withDefaults().hashToString(bcryptCost, plain.toCharArray());
        }
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] result = argon2(plain, salt, memory, iterations, parallelism, 32);
        Base64.Encoder enc = Base64.getEncoder().withoutPadding();
        return "$argon2id$v=19$m=" + memory + ",t=" + iterations + ",p=" + parallelism
                + "$" + enc.encodeToString(salt) + "$" + enc.encodeToString(result);
    }

    public boolean verify(String plain, String hash) {
        if (hash == null || hash.isEmpty()) return false;
        if (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$")) {
            return BCrypt.verifyer().verify(plain.toCharArray(), hash).verified;
        }
        if (hash.startsWith("$SHA$")) return verifyAuthMeSha256(plain, hash);
        if (hash.startsWith("{MD5}")) return verifyDigest(plain, hash.substring(5), "MD5");
        if (hash.startsWith("{SHA256}")) return verifyDigest(plain, hash.substring(8), "SHA-256");
        if (hash.startsWith("{SHA512}")) return verifyDigest(plain, hash.substring(8), "SHA-512");
        if (!hash.startsWith("$argon2id$") && !hash.startsWith("$argon2i$")) return false;

        try {
            String[] parts = hash.split("\\$");
            if (parts.length != 6 || !parts[2].equals("v=19")) return false;
            int[] params = parseParams(parts[3]);
            byte[] salt = Base64.getDecoder().decode(parts[4]);
            byte[] expected = Base64.getDecoder().decode(parts[5]);
            if (salt.length < 8 || salt.length > 64 || expected.length < 16 || expected.length > 64) return false;
            int type = hash.startsWith("$argon2id$")
                    ? Argon2Parameters.ARGON2_id : Argon2Parameters.ARGON2_i;
            byte[] actual = argon2(plain, salt, params[0], params[1], params[2], expected.length, type);
            boolean ok = MessageDigest.isEqual(expected, actual);
            Arrays.fill(actual, (byte) 0);
            return ok;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean needsRehash(String hash) {
        if (hash == null) return true;
        if (algorithm.equals("bcrypt")) {
            if (!hash.startsWith("$2")) return true;
            try {
                return Integer.parseInt(hash.substring(4, 6)) != bcryptCost;
            } catch (RuntimeException e) {
                return true;
            }
        }
        if (!hash.startsWith("$argon2id$")) return true;
        try {
            String[] parts = hash.split("\\$");
            int[] params = parseParams(parts[3]);
            return params[0] != memory || params[1] != iterations || params[2] != parallelism;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private int[] parseParams(String raw) {
        String[] values = raw.split(",");
        if (values.length != 3) throw new IllegalArgumentException("bad argon2 parameters");
        int m = Integer.parseInt(values[0].substring(2));
        int t = Integer.parseInt(values[1].substring(2));
        int p = Integer.parseInt(values[2].substring(2));
        if (m < 8 || m > 262_144 || t < 1 || t > 10 || p < 1 || p > 16) {
            throw new IllegalArgumentException("bad argon2 parameters");
        }
        return new int[]{m, t, p};
    }

    private byte[] argon2(String plain, byte[] salt, int m, int t, int p, int length) {
        return argon2(plain, salt, m, t, p, length, Argon2Parameters.ARGON2_id);
    }

    private byte[] argon2(String plain, byte[] salt, int m, int t, int p, int length, int type) {
        Argon2Parameters params = new Argon2Parameters.Builder(type)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(m)
                .withIterations(t)
                .withParallelism(p)
                .withSalt(salt)
                .build();
        byte[] password = plain.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[length];
        try {
            Argon2BytesGenerator generator = new Argon2BytesGenerator();
            generator.init(params);
            generator.generateBytes(password, result);
            return result;
        } finally {
            Arrays.fill(password, (byte) 0);
        }
    }

    private boolean verifyAuthMeSha256(String plain, String hash) {
        String[] parts = hash.split("\\$");
        if (parts.length != 4 || !parts[1].equals("SHA")) return false;
        String first = digestHex(plain, "SHA-256");
        String actual = digestHex(first + parts[2], "SHA-256");
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.US_ASCII),
                parts[3].getBytes(StandardCharsets.US_ASCII));
    }

    private boolean verifyDigest(String plain, String expected, String algorithm) {
        String actual = digestHex(plain, algorithm);
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.US_ASCII),
                expected.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }

    private String digestHex(String value, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
