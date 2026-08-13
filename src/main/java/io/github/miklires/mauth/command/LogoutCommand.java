package io.github.miklires.mauth.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.util.MessageUtil;

public class LogoutCommand implements CommandExecutor {

    private final MAuth plugin;

    public LogoutCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.logout")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        MessageUtil msg = plugin.getMessageUtil();

        if (!plugin.getSessionManager().isAuthenticated(player)) {
            msg.send(player, "auth.not-logged-in");
            return true;
        }
        plugin.getSessionManager().clear(player);
        plugin.getSessionManager().invalidatePersistentSession(player.getName());
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.LOGOUT, player.getName(), ip);
        msg.send(player, "auth.logged-out");
        player.kick(plugin.getMessageUtil().getPlain(player, "auth.logged-out"));
        return true;
    }
}
