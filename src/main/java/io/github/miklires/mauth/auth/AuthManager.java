package io.github.miklires.mauth.auth;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.audit.AuditEvent;
import io.github.miklires.mauth.model.Account;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class AuthManager {

    public enum RegisterResult {
        SUCCESS, ALREADY_EXISTS, PASSWORD_MISMATCH, TOO_MANY_ACCOUNTS, DB_ERROR
    }

    public enum LoginResult {
        SUCCESS, NOT_REGISTERED, WRONG_PASSWORD, LOCKED_OUT, DB_ERROR, DISCORD_REQUIRED,
        TOTP_REQUIRED, VPN_BLOCKED, DEVICE_BLOCKED
    }

    public enum ChangePasswordResult {
        SUCCESS, NOT_REGISTERED, WRONG_OLD, DB_ERROR
    }

    private final MAuth plugin;
    private final Object registrationLock = new Object();

    public AuthManager(MAuth plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<RegisterResult> register(String username, String ip,
                                                       String password, String confirm) {
        return CompletableFuture.supplyAsync(
                () -> registerNow(username, ip, password, confirm), plugin.getAuthExecutor());
    }

    private RegisterResult registerNow(String username, String ip, String password, String confirm) {
        if (!password.equals(confirm)) return RegisterResult.PASSWORD_MISMATCH;
        String registeredName = username;
        username = username.toLowerCase();
        try {
            if (plugin.getAccountRepository().findByUsername(username).isPresent()) {
                return RegisterResult.ALREADY_EXISTS;
            }
            String hash = plugin.getPasswordHasher().hash(password);
            synchronized (registrationLock) {
                if (plugin.getAccountRepository().findByUsername(username).isPresent()) {
                    return RegisterResult.ALREADY_EXISTS;
                }
                int limit = plugin.getConfigManager().getMaxAccountsPerIp();
                if (ip != null && limit > 0
                        && plugin.getKnownIpRepository().countAccounts(ip) >= limit) {
                    return RegisterResult.TOO_MANY_ACCOUNTS;
                }
                Account a = new Account(registeredName, hash);
                a.setLastIp(ip);
                plugin.getAccountRepository().insert(a);
                if (ip != null) {
                    plugin.getKnownIpRepository().recordIp(username, ip);
                }
            }
            plugin.getAuditLogger().log(AuditEvent.REGISTER, username, ip);
            return RegisterResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on register: " + e.getMessage());
            return RegisterResult.DB_ERROR;
        }
    }

    public CompletableFuture<LoginAttempt> login(String username, String ip, java.util.UUID uuid, String password) {
        return CompletableFuture.supplyAsync(
                () -> loginNow(username, ip, uuid, password), plugin.getAuthExecutor());
    }

    private LoginAttempt loginNow(String username, String ip, java.util.UUID uuid, String password) {
        String enteredName = username;
        username = username.toLowerCase();
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty()) return new LoginAttempt(LoginResult.NOT_REGISTERED, null, false, false);
            Account a = opt.get();
            if (!plugin.getPasswordHasher().verify(password, a.getPasswordHash())) {
                plugin.getAuditLogger().log(AuditEvent.LOGIN_FAIL_WRONG_PASSWORD, username, ip);
                return new LoginAttempt(LoginResult.WRONG_PASSWORD, null, false, false);
            }

            if (plugin.getIpRiskService().check(ip)
                    == io.github.miklires.mauth.risk.IpRiskService.Decision.BLOCK) {
                plugin.getAuditLogger().log(AuditEvent.LOGIN_FAIL_VPN, username, ip);
                return new LoginAttempt(LoginResult.VPN_BLOCKED, null, false, false);
            }

            boolean newIp = ip != null && !plugin.getKnownIpRepository().isKnown(username, ip)
                    && plugin.getKnownIpRepository().countForAccount(username) > 0;
            boolean knownDevice = plugin.getKnownDeviceRepository().isKnown(username, uuid);
            boolean newDevice = !knownDevice && plugin.getKnownDeviceRepository().count(username) > 0;
            if (newDevice && plugin.getConfigManager().getNewDevicePolicy().equals("deny")) {
                return new LoginAttempt(LoginResult.DEVICE_BLOCKED, null, newIp, true);
            }
            plugin.getKnownDeviceRepository().record(username, uuid);

            if (plugin.getPasswordHasher().needsRehash(a.getPasswordHash())) {
                a.setPasswordHash(plugin.getPasswordHasher().hash(password));
            }
            if (a.getRegisteredName() == null) {
                a.setRegisteredName(enteredName);
            }

            DiscordMode mode = plugin.getConfigManager().getDiscordMode();
            if (!a.hasDiscordLinked()
                    && mode.requiresLink(a.getRegisteredAt(), plugin.getConfigManager().getDiscordRequiredAfter())) {
                plugin.getAuditLogger().log(AuditEvent.LOGIN_FAIL_DISCORD_REQUIRED, username, ip);
                return new LoginAttempt(LoginResult.DISCORD_REQUIRED, null, newIp, newDevice);
            }

            if (a.hasTotp()) return new LoginAttempt(LoginResult.TOTP_REQUIRED, a, newIp, newDevice);

            recordSuccessfulLogin(a, ip, false);
            return new LoginAttempt(LoginResult.SUCCESS, a, newIp, newDevice);
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on login: " + e.getMessage());
            return new LoginAttempt(LoginResult.DB_ERROR, null, false, false);
        }
    }

    public void finishSecondFactor(Account account, String ip, boolean recoveryUsed) {
        try {
            recordSuccessfulLogin(account, ip, recoveryUsed);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void recordSuccessfulLogin(Account account, String ip, boolean recoveryUsed) throws SQLException {
        account.setLastLoginAt(Instant.now());
        account.setLastIp(ip);
        plugin.getAccountRepository().update(account);
        if (ip != null) plugin.getKnownIpRepository().recordIp(account.getUsername(), ip);
        plugin.getAuditLogger().log(AuditEvent.LOGIN_SUCCESS, account.getUsername(), ip);
        if (recoveryUsed) {
            plugin.getAuditLogger().log(AuditEvent.TOTP_RECOVERY_USED, account.getUsername(), ip);
        }
    }

    public void completeLogin(Player player, Account account) {
        completeLogin(player, account, io.github.miklires.mauth.api.PlayerAuthenticatedEvent.AuthReason.LOGIN);
    }

    public void completeLogin(Player player, Account account,
                              io.github.miklires.mauth.api.PlayerAuthenticatedEvent.AuthReason reason) {
        plugin.getFloodgateBridge().getXuid(player.getUniqueId()).ifPresent(xuid -> {
            if (account.getBedrockXuid() != null) return;
            account.setBedrockXuid(xuid);
            CompletableFuture.runAsync(() -> {
                try {
                    plugin.getAccountRepository().update(account);
                } catch (SQLException e) {
                    account.setBedrockXuid(null);
                    plugin.getLogger().warning("cannot link Floodgate account: " + e.getMessage());
                }
            }, plugin.getAuthExecutor());
        });
        plugin.getSessionManager().markAuthenticated(player, reason);
        Location protectedLocation = plugin.getPlayerStateStore().restore(player);
        Location saved = protectedLocation != null ? protectedLocation
                : plugin.getLimboWorldManager().parseLocation(account.getLastLocation());
        Location actual = plugin.getLimboWorldManager().returnFromLimbo(player, saved);
        if (actual != null && (protectedLocation != null || account.getLastLocation() == null)) {
            account.setLastLocation(plugin.getLimboWorldManager().serializeLocation(actual));
            CompletableFuture.runAsync(() -> {
                try {
                    plugin.getAccountRepository().update(account);
                } catch (SQLException e) {
                    plugin.getLogger().warning("cannot save login location: " + e.getMessage());
                }
            }, plugin.getAuthExecutor());
        }
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        plugin.getGeoIpService().checkAsync(account.getUsername(), ip, account.getDiscordId());
    }

    public CompletableFuture<ChangePasswordResult> changePassword(String username, String ip,
                                                                   String oldPw, String newPw) {
        return CompletableFuture.supplyAsync(
                () -> changePasswordNow(username, ip, oldPw, newPw), plugin.getAuthExecutor());
    }

    private ChangePasswordResult changePasswordNow(String username, String ip,
                                                    String oldPw, String newPw) {
        username = username.toLowerCase();
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
            plugin.getAuditLogger().log(AuditEvent.PASSWORD_CHANGED, username, ip);
            return ChangePasswordResult.SUCCESS;
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on changepassword: " + e.getMessage());
            return ChangePasswordResult.DB_ERROR;
        }
    }

    public record LoginAttempt(LoginResult result, Account account, boolean newIp, boolean newDevice) {}
}
