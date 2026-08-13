package io.github.miklires.mauth.discord;

import io.github.miklires.mauth.MAuth;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LinkCodeManager {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final MAuth plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, CodeEntry> codes = new HashMap<>();

    public LinkCodeManager(MAuth plugin) {
        this.plugin = plugin;
    }

    public synchronized String generateCode(String username) {
        purgeExpired();
        String lowerUsername = username.toLowerCase();

        for (Map.Entry<String, CodeEntry> e : codes.entrySet()) {
            if (e.getValue().username.equals(lowerUsername)) {
                return e.getKey();
            }
        }

        String code;
        do {
            code = randomCode();
        } while (codes.containsKey(code));
        codes.put(code, new CodeEntry(lowerUsername, System.currentTimeMillis()));
        return code;
    }

    public synchronized Optional<String> consumeCode(String code) {
        purgeExpired();
        CodeEntry e = codes.remove(code.toUpperCase());
        if (e == null) return Optional.empty();
        long ttlMs = plugin.getConfigManager().getLinkCodeTtl() * 1000L;
        if (System.currentTimeMillis() - e.createdAt > ttlMs) return Optional.empty();
        return Optional.of(e.username);
    }

    private void purgeExpired() {
        long ttlMs = plugin.getConfigManager().getLinkCodeTtl() * 1000L;
        long now = System.currentTimeMillis();
        codes.entrySet().removeIf(e -> now - e.getValue().createdAt > ttlMs);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static class CodeEntry {
        final String username;
        final long createdAt;
        CodeEntry(String username, long createdAt) {
            this.username = username;
            this.createdAt = createdAt;
        }
    }
}
