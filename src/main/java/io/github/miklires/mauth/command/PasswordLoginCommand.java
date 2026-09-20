package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.audit.AuditEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

public class PasswordLoginCommand implements CommandExecutor {

    private final MAuth plugin;

    public PasswordLoginCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.passwordlogin")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        if (!plugin.getSessionManager().isAuthenticated(player)) {
            plugin.getMessageUtil().send(player, "auth.not-logged-in");
            return true;
        }
        plugin.submit(player, () -> enable(player.getName()), (changed, error) -> {
            if (!player.isOnline()) return;
            if (error != null) {
                plugin.getMessageUtil().send(player, "auth.database-error");
            } else {
                plugin.getMessageUtil().send(player,
                        changed ? "license.password-login-enabled" : "license.already-password-login");
            }
        });
        return true;
    }

    private boolean enable(String username) {
        try {
            var account = plugin.getAccountRepository().findByUsername(username).orElse(null);
            if (account == null || !account.isPremiumEnabled()) return false;
            account.setPremiumEnabled(false);
            account.setPremiumUuid(null);
            plugin.getAccountRepository().update(account);
            plugin.getSessionManager().invalidatePersistentSession(username);
            plugin.getAuditLogger().log(AuditEvent.LICENSE_DISABLED, username);
            return true;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
