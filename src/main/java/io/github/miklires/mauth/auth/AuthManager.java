package io.github.miklires.mauth.auth;

import org.bukkit.entity.Player;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.audit.AuditEvent;
import io.github.miklires.mauth.model.Account;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public class AuthManager {

    public enum RegisterResult {
        SUCCESS, ALREADY_EXISTS, PASSWORD_MISMATCH, DB_ERROR
    }

    public enum LoginResult {
        SUCCESS, NOT_REGISTERED, WRONG_PASSWORD, LOCKED_OUT, DB_ERROR, DISCORD_REQUIRED
    }

    public enum ChangePasswordResult {
        SUCCESS, NOT_REGISTERED, WRONG_OLD, DB_ERROR
    }

    private final MAuth plugin;

    public AuthManager(MAuth plugin) {
        this.plugin = plugin;
    }

    public RegisterResult register(Player player, String password, String confirm) {
        if (!password.equals(confirm)) return RegisterResult.PASSWORD_MISMATCH;
        String username = player.getName().toLowerCase();
        try {
            if (plugin.getAccountRepository().findByUsername(username).isPresent()) {
                return RegisterResult.ALREADY_EXISTS;
            }
            String hash = plugin.getPasswordHasher().hash(password);
            Account a = new Account(username, hash);
            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
            a.setLastIp(ip);
            plugin.getAccountRepository().insert(a);
            if (ip != null) {
                plugin.getKnownIpRepository().recordIp(username, ip);
            }
            plugin.getAuditLogger().log(AuditEvent.REGISTER, username, ip);
            return RegisterResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on register: " + e.getMessage());
            return RegisterResult.DB_ERROR;
        }
    }

    public LoginResult login(Player player, String password) {
        String username = player.getName().toLowerCase();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        if (plugin.getSessionManager().isLockedOut(username)) {
            plugin.getAuditLogger().log(AuditEvent.LOGIN_FAIL_LOCKED_OUT, username, ip);
            return LoginResult.LOCKED_OUT;
        }
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty()) return LoginResult.NOT_REGISTERED;
            Account a = opt.get();
            if (!plugin.getPasswordHasher().verify(password, a.getPasswordHash())) {
                plugin.getSessionManager().recordFailedAttempt(username);
                plugin.getAuditLogger().log(AuditEvent.LOGIN_FAIL_WRONG_PASSWORD, username, ip);
                return LoginResult.WRONG_PASSWORD;
            }

            if (!a.hasDiscordLinked()) {
                plugin.getAuditLogger().log(AuditEvent.LOGIN_FAIL_DISCORD_REQUIRED, username, ip);
                return LoginResult.DISCORD_REQUIRED;
            }

            a.setLastLoginAt(Instant.now());
            a.setLastIp(ip);
            plugin.getAccountRepository().update(a);
            plugin.getSessionManager().markAuthenticated(player);

            if (ip != null) {
                plugin.getKnownIpRepository().recordIp(username, ip);
            }

            org.bukkit.Location saved = plugin.getLimboWorldManager().parseLocation(a.getLastLocation());
            org.bukkit.Location actual = plugin.getLimboWorldManager().returnFromLimbo(player, saved);
            if (saved == null && actual != null) {
                a.setLastLocation(plugin.getLimboWorldManager().serializeLocation(actual));
                plugin.getAccountRepository().update(a);
            }

            plugin.getAuditLogger().log(AuditEvent.LOGIN_SUCCESS, username, ip);
            plugin.getGeoIpService().checkAsync(username, ip, a.getDiscordId());
            return LoginResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on login: " + e.getMessage());
            return LoginResult.DB_ERROR;
        }
    }

    public ChangePasswordResult changePassword(Player player, String oldPw, String newPw) {
        String username = player.getName().toLowerCase();
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty()) return ChangePasswordResult.NOT_REGISTERED;
            Account a = opt.get();
            if (!plugin.getPasswordHasher().verify(oldPw, a.getPasswordHash())) {
                return ChangePasswordResult.WRONG_OLD;
            }
            a.setPasswordHash(plugin.getPasswordHasher().hash(newPw));
            plugin.getAccountRepository().update(a);
            plugin.getSessionManager().invalidatePersistentSession(username);
            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
            plugin.getAuditLogger().log(AuditEvent.PASSWORD_CHANGED, username, ip);
            return ChangePasswordResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on changepassword: " + e.getMessage());
            return ChangePasswordResult.DB_ERROR;
        }
    }
}
