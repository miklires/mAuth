package io.github.miklires.mauth.captcha;

import org.bukkit.entity.Player;
import io.github.miklires.mauth.MAuth;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaManager {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final MAuth plugin;
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, CaptchaState> states = new ConcurrentHashMap<>();
    private final FloodDetector floodDetector;

    public CaptchaManager(MAuth plugin) {
        this.plugin = plugin;
        this.floodDetector = new FloodDetector(plugin);
    }

    public FloodDetector getFloodDetector() {
        return floodDetector;
    }

    public boolean shouldRequireForRegistration() {
        String mode = plugin.getConfigManager().getCaptchaMode();
        return switch (mode) {
            case "off" -> false;
            case "always" -> true;
            case "auto" -> floodDetector.isFloodActive();
            default -> false;
        };
    }

    public boolean shouldRequireForLogin(String username, String ip) {
        String mode = plugin.getConfigManager().getCaptchaMode();
        if (mode.equals("off")) return false;
        if (mode.equals("always")) return true;
        if (floodDetector.isFloodActive()) return true;
        if (ip == null) return false;
        try {
            return !plugin.getKnownIpRepository().isKnown(username, ip);
        } catch (java.sql.SQLException e) {
            plugin.getLogger().warning("db error on captcha ip check: " + e.getMessage());
            return false;
        }
    }

    public String generateFor(Player player) {
        String code = randomCode(plugin.getConfigManager().getCaptchaLength());
        states.put(player.getUniqueId(), new CaptchaState(code, plugin.getConfigManager().getCaptchaMaxAttempts()));
        return code;
    }

    public CaptchaState getState(Player player) {
        return states.get(player.getUniqueId());
    }

    public boolean hasPending(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    public CheckResult check(Player player, String input) {
        CaptchaState state = states.get(player.getUniqueId());
        if (state == null) return CheckResult.NO_CAPTCHA;
        if (state.code.equalsIgnoreCase(input)) {
            states.remove(player.getUniqueId());
            return CheckResult.OK;
        }
        state.attemptsLeft--;
        if (state.attemptsLeft <= 0) {
            states.remove(player.getUniqueId());
            return CheckResult.OUT_OF_ATTEMPTS;
        }
        return CheckResult.WRONG;
    }

    public void clear(Player player) {
        states.remove(player.getUniqueId());
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public enum CheckResult {
        OK, WRONG, OUT_OF_ATTEMPTS, NO_CAPTCHA
    }

    public static class CaptchaState {
        final String code;
        int attemptsLeft;

        CaptchaState(String code, int attemptsLeft) {
            this.code = code;
            this.attemptsLeft = attemptsLeft;
        }

        public String getCode() { return code; }
        public int getAttemptsLeft() { return attemptsLeft; }
    }
}
