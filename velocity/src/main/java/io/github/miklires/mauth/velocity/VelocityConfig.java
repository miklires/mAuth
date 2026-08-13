package io.github.miklires.mauth.velocity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public record VelocityConfig(String sharedSecret, Set<String> loginServers, String targetServer,
                             PremiumMode premiumMode, boolean allowCrackedOnLookupFailure,
                             String coreApiUrl, String coreApiToken, ConflictPolicy conflictPolicy) {

    public static VelocityConfig load(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("config.properties");
        Properties properties = new Properties();
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            }
        }
        String secret = properties.getProperty("shared-secret", "").trim();
        if (secret.isEmpty()) secret = generateSecret();
        properties.setProperty("shared-secret", secret);
        properties.putIfAbsent("login-servers", "auth");
        properties.putIfAbsent("target-server", "lobby");
        properties.putIfAbsent("premium-mode", "auto");
        properties.putIfAbsent("allow-cracked-on-lookup-failure", "true");
        properties.putIfAbsent("core-api-url", "http://127.0.0.1:8765");
        properties.putIfAbsent("core-api-token", "");
        properties.putIfAbsent("premium-conflict-policy", "block");
        try (var writer = Files.newBufferedWriter(file)) {
            properties.store(writer, "mAuth Velocity");
        }
        Set<String> loginServers = new LinkedHashSet<>();
        Arrays.stream(properties.getProperty("login-servers").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(loginServers::add);
        if (loginServers.isEmpty()) throw new IllegalArgumentException("login-servers is empty");
        return new VelocityConfig(
                secret,
                Set.copyOf(loginServers),
                properties.getProperty("target-server").trim(),
                PremiumMode.parse(properties.getProperty("premium-mode")),
                Boolean.parseBoolean(properties.getProperty("allow-cracked-on-lookup-failure")),
                properties.getProperty("core-api-url").replaceAll("/+$", ""),
                properties.getProperty("core-api-token").trim(),
                ConflictPolicy.parse(properties.getProperty("premium-conflict-policy")));
    }

    private static String generateSecret() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public enum PremiumMode {
        AUTO, DISABLED;

        static PremiumMode parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("premium-mode must be auto or disabled");
            }
        }
    }

    public enum ConflictPolicy {
        BLOCK, RENAME, TAKE_OVER;

        static ConflictPolicy parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("premium-conflict-policy must be block, rename or take-over");
            }
        }
    }
}
