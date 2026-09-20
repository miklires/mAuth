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

public record VelocityConfig(String sharedSecret, Set<String> loginServers, String targetServer) {

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
        properties.keySet().removeIf(key -> !Set.of(
                "shared-secret", "login-servers", "target-server").contains(key.toString()));
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
                properties.getProperty("target-server").trim());
    }

    private static String generateSecret() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
