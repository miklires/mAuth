package io.github.miklires.mauth.bungee;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public record BungeeConfig(String sharedSecret, Set<String> loginServers, String targetServer) {

    public static BungeeConfig load(File directory) throws IOException {
        Files.createDirectories(directory.toPath());
        File file = new File(directory, "config.properties");
        Properties properties = new Properties();
        if (file.exists()) {
            try (var reader = Files.newBufferedReader(file.toPath())) {
                properties.load(reader);
            }
        }
        String secret = properties.getProperty("shared-secret", "").trim();
        if (secret.isEmpty()) secret = generateSecret();
        properties.setProperty("shared-secret", secret);
        properties.putIfAbsent("login-servers", "auth");
        properties.putIfAbsent("target-server", "lobby");
        try (var writer = Files.newBufferedWriter(file.toPath())) {
            properties.store(writer, "mAuth Bungee");
        }
        Set<String> servers = new LinkedHashSet<>();
        Arrays.stream(properties.getProperty("login-servers").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .forEach(servers::add);
        if (servers.isEmpty()) throw new IllegalArgumentException("login-servers is empty");
        return new BungeeConfig(secret, Set.copyOf(servers),
                properties.getProperty("target-server").trim());
    }

    private static String generateSecret() {
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
