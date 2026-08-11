package io.github.miklires.mauth.auth;

import io.github.miklires.mauth.MAuth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PasswordValidator {

    public enum Result {
        OK, TOO_SHORT, TOO_LONG, TOO_WEAK, TOO_SIMPLE
    }

    private final MAuth plugin;
    private final Set<String> blacklist = new HashSet<>();

    public PasswordValidator(MAuth plugin) {
        this.plugin = plugin;
        loadBlacklist();
    }

    private void loadBlacklist() {
        File file = new File(plugin.getDataFolder(), "passwords-blacklist.txt");
        if (!file.exists()) {
            plugin.saveResource("passwords-blacklist.txt", false);
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    blacklist.add(trimmed);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("cannot load blacklist: " + e.getMessage());
        }
    }

    public Result validate(String password, String username) {
        int min = plugin.getConfigManager().getMinPasswordLength();
        int max = plugin.getConfigManager().getMaxPasswordLength();

        if (password.length() < min) return Result.TOO_SHORT;
        if (password.length() > max) return Result.TOO_LONG;

        String lower = password.toLowerCase();
        if (blacklist.contains(lower)) return Result.TOO_WEAK;
        if (username != null && lower.equals(username.toLowerCase())) return Result.TOO_SIMPLE;
        if (isTrivialPattern(lower)) return Result.TOO_SIMPLE;

        return Result.OK;
    }

    private boolean isTrivialPattern(String pw) {
        if (pw.chars().distinct().count() == 1) return true;
        boolean ascending = true, descending = true;
        for (int i = 1; i < pw.length(); i++) {
            if (pw.charAt(i) != pw.charAt(i - 1) + 1) ascending = false;
            if (pw.charAt(i) != pw.charAt(i - 1) - 1) descending = false;
        }
        return ascending || descending;
    }
}
