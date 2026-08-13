package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TelegramCommand implements CommandExecutor {

    private final MAuth plugin;

    public TelegramCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.telegram")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        if (!plugin.getSessionManager().isAuthenticated(player)) {
            plugin.getMessageUtil().send(player, "auth.not-logged-in");
            return true;
        }
        if (!plugin.getConfigManager().isTelegramEnabled()) {
            plugin.getMessageUtil().send(player, "telegram.disabled");
            return true;
        }
        plugin.submit(player, () -> {
            try {
                return plugin.getAccountRepository().findByUsername(player.getName())
                        .map(a -> a.hasTelegramLinked() ? null
                                : plugin.getLinkCodeManager().generateCode(a.getUsername())).orElse(null);
            } catch (Exception e) {
                plugin.getLogger().severe("telegram link code failed: " + e.getMessage());
                return null;
            }
        }, (code, error) -> {
            if (code == null) plugin.getMessageUtil().send(player, "telegram.already-linked");
            else plugin.getMessageUtil().send(player, "telegram.link-code", MessageUtil.ph("code", code));
        });
        return true;
    }
}
