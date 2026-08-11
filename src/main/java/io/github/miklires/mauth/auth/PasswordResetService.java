package io.github.miklires.mauth.auth;

import org.bukkit.Bukkit;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.audit.AuditEvent;
import io.github.miklires.mauth.model.Account;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class PasswordResetService {

    private static final String CHARS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final long RATE_LIMIT_SECONDS = 24 * 3600L;

    private final SecureRandom random = new SecureRandom();
    private final MAuth plugin;

    public PasswordResetService(MAuth plugin) {
        this.plugin = plugin;
    }

    public ResetResult resetByUsername(String username) {
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty()) return new ResetResult(Status.NOT_FOUND, null);
            return doReset(opt.get(), false);
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on reset: " + e.getMessage());
            return new ResetResult(Status.DB_ERROR, null);
        }
    }

    public ResetResult resetByDiscordId(String discordId) {
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByDiscordId(discordId);
            if (opt.isEmpty()) return new ResetResult(Status.NOT_FOUND, null);
            Account a = opt.get();

            if (isOnline(a.getUsername())) {
                return new ResetResult(Status.PLAYER_ONLINE, null);
            }
            if (a.getLastPasswordResetAt() != null) {
                long elapsed = System.currentTimeMillis() / 1000 - a.getLastPasswordResetAt().getEpochSecond();
                if (elapsed < RATE_LIMIT_SECONDS) {
                    long left = RATE_LIMIT_SECONDS - elapsed;
                    return new ResetResult(Status.RATE_LIMITED, null, left);
                }
            }

            return doReset(a, true);
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on reset: " + e.getMessage());
            return new ResetResult(Status.DB_ERROR, null);
        }
    }

    private ResetResult doReset(Account a, boolean trackTimestamp) throws SQLException {
        String newPassword = generatePassword(12);
        a.setPasswordHash(plugin.getPasswordHasher().hash(newPassword));
        if (trackTimestamp) {
            a.setLastPasswordResetAt(Instant.now());
        }
        plugin.getAccountRepository().update(a);
        plugin.getSessionManager().invalidatePersistentSession(a.getUsername());
        plugin.getAuditLogger().log(
                trackTimestamp ? AuditEvent.PASSWORD_RESET_DISCORD : AuditEvent.PASSWORD_RESET_CONSOLE,
                a.getUsername(), null);
        plugin.getLogger().info("password reset for " + a.getUsername()
                + (trackTimestamp ? " (discord)" : " (console)"));
        return new ResetResult(Status.SUCCESS, newPassword, a.getUsername());
    }

    private boolean isOnline(String username) {
        return Bukkit.getOnlinePlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(username));
    }

    private String generatePassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public enum Status {
        SUCCESS, NOT_FOUND, PLAYER_ONLINE, RATE_LIMITED, DB_ERROR
    }

    public static class ResetResult {
        public final Status status;
        public final String newPassword;
        public final String username;
        public final long retryAfterSeconds;

        public ResetResult(Status status, String newPassword) {
            this(status, newPassword, null, 0);
        }

        public ResetResult(Status status, String newPassword, String username) {
            this(status, newPassword, username, 0);
        }

        public ResetResult(Status status, String newPassword, long retryAfterSeconds) {
            this(status, newPassword, null, retryAfterSeconds);
        }

        public ResetResult(Status status, String newPassword, String username, long retryAfterSeconds) {
            this.status = status;
            this.newPassword = newPassword;
            this.username = username;
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }
}
