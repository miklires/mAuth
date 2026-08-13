package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.audit.AuditEvent;
import io.github.miklires.mauth.auth.TotpService;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public class TotpCommand implements CommandExecutor {

    private final MAuth plugin;

    public TotpCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.2fa")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        if (plugin.getTotpService().hasPendingLogin(player.getUniqueId())) {
            if (args.length != 1) plugin.getMessageUtil().send(player, "totp.login-required");
            else verifyLogin(player, args[0]);
            return true;
        }
        if (!plugin.getSessionManager().isAuthenticated(player)) {
            plugin.getMessageUtil().send(player, "auth.not-logged-in");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("enable")) {
            enable(player);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
            confirm(player, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("disable")) {
            disable(player, args[1]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("recovery")) {
            regenerate(player, args[1]);
        } else {
            plugin.getMessageUtil().send(player, "totp.usage");
        }
        return true;
    }

    private void enable(Player player) {
        load(player, (account, error) -> {
            if (error != null || account == null) {
                plugin.getMessageUtil().send(player, "auth.database-error");
                return;
            }
            if (account.hasTotp()) {
                plugin.getMessageUtil().send(player, "totp.already-enabled");
                return;
            }
            TotpService.SetupView setup = plugin.getTotpService().startSetup(player.getUniqueId(), account);
            String issuer = plugin.getConfigManager().getTotpIssuer();
            String uri = "otpauth://totp/" + encode(issuer) + ":" + encode(player.getName())
                    + "?secret=" + setup.secret() + "&issuer=" + encode(issuer) + "&digits=6&period=30";
            plugin.getMessageUtil().send(player, "totp.setup-secret",
                    MessageUtil.ph("secret", setup.secret()));
            plugin.getMessageUtil().send(player, "totp.setup-uri", MessageUtil.ph("uri", uri));
            plugin.getMessageUtil().send(player, "totp.recovery-codes",
                    MessageUtil.ph("codes", String.join(" ", setup.recoveryCodes())));
            plugin.getMessageUtil().send(player, "totp.confirm-help");
        });
    }

    private void confirm(Player player, String code) {
        Account account = plugin.getTotpService().confirmSetup(player.getUniqueId(), code);
        if (account == null) {
            plugin.getMessageUtil().send(player, "totp.wrong-or-expired");
            return;
        }
        save(account, AuditEvent.TOTP_ENABLED, player, "totp.enabled");
    }

    private void disable(Player player, String password) {
        plugin.submit(player, () -> {
            try {
                Account account = plugin.getAccountRepository().findByUsername(player.getName()).orElse(null);
                if (account == null || !plugin.getPasswordHasher().verify(password, account.getPasswordHash())) {
                    return ActionResult.WRONG_PASSWORD;
                }
                if (!account.hasTotp()) return ActionResult.NOT_ENABLED;
                account.setTotpSecret(null);
                account.setRecoveryCodes(null);
                plugin.getAccountRepository().update(account);
                plugin.getSessionManager().invalidatePersistentSession(account.getUsername());
                plugin.getAuditLogger().log(AuditEvent.TOTP_DISABLED, account.getUsername());
                return ActionResult.SUCCESS;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (result, error) -> {
            if (error != null) plugin.getMessageUtil().send(player, "auth.database-error");
            else if (result == ActionResult.WRONG_PASSWORD) plugin.getMessageUtil().send(player, "auth.wrong-password");
            else if (result == ActionResult.NOT_ENABLED) plugin.getMessageUtil().send(player, "totp.not-enabled");
            else plugin.getMessageUtil().send(player, "totp.disabled");
        });
    }

    private void regenerate(Player player, String password) {
        plugin.submit(player, () -> {
            try {
                Account account = plugin.getAccountRepository().findByUsername(player.getName()).orElse(null);
                if (account == null || !plugin.getPasswordHasher().verify(password, account.getPasswordHash())) {
                    return new RecoveryResult(ActionResult.WRONG_PASSWORD, List.of());
                }
                if (!account.hasTotp()) return new RecoveryResult(ActionResult.NOT_ENABLED, List.of());
                List<String> codes = plugin.getTotpService().regenerateRecoveryCodes(account);
                plugin.getAccountRepository().update(account);
                return new RecoveryResult(ActionResult.SUCCESS, codes);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (result, error) -> {
            if (error != null) plugin.getMessageUtil().send(player, "auth.database-error");
            else if (result.status == ActionResult.WRONG_PASSWORD) plugin.getMessageUtil().send(player, "auth.wrong-password");
            else if (result.status == ActionResult.NOT_ENABLED) plugin.getMessageUtil().send(player, "totp.not-enabled");
            else plugin.getMessageUtil().send(player, "totp.recovery-codes",
                        MessageUtil.ph("codes", String.join(" ", result.codes)));
        });
    }

    private void verifyLogin(Player player, String code) {
        String ip = player.getAddress() == null ? null : player.getAddress().getAddress().getHostAddress();
        plugin.submit(player, () -> {
            TotpService.LoginVerification result = plugin.getTotpService()
                    .verifyLogin(player.getUniqueId(), code);
            if (result.account() != null) {
                plugin.getAuthManager().finishSecondFactor(result.account(), ip,
                        result.status() == TotpService.VerifyStatus.RECOVERY_USED);
            }
            return result;
        }, (result, error) -> {
            if (!player.isOnline()) return;
            if (error != null) {
                plugin.getMessageUtil().send(player, "auth.database-error");
                return;
            }
            switch (result.status()) {
                case SUCCESS, RECOVERY_USED -> {
                    plugin.getAuthManager().completeLogin(player, result.account());
                    plugin.getMessageUtil().send(player, result.status() == TotpService.VerifyStatus.RECOVERY_USED
                            ? "totp.recovery-used" : "auth.logged-in");
                }
                case WRONG -> plugin.getMessageUtil().send(player, "totp.wrong-code");
                case EXPIRED -> plugin.getMessageUtil().send(player, "totp.expired");
                case LOCKED -> player.kick(plugin.getMessageUtil().getPlain(player, "totp.locked"));
            }
        });
    }

    private void load(Player player, java.util.function.BiConsumer<Account, Throwable> callback) {
        plugin.submit(player, () -> {
            try {
                return plugin.getAccountRepository().findByUsername(player.getName()).orElse(null);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, callback);
    }

    private void save(Account account, AuditEvent event, Player player, String message) {
        plugin.submit(player, () -> {
            try {
                plugin.getAccountRepository().update(account);
                plugin.getAuditLogger().log(event, account.getUsername());
                return true;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (ignored, error) -> plugin.getMessageUtil().send(player,
                error == null ? message : "auth.database-error"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private enum ActionResult {
        SUCCESS, WRONG_PASSWORD, NOT_ENABLED
    }

    private record RecoveryResult(ActionResult status, List<String> codes) {
    }
}
