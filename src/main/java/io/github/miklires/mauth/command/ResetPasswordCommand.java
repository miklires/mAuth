package io.github.miklires.mauth.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.PasswordResetService;
import io.github.miklires.mauth.util.MessageUtil;

public class ResetPasswordCommand implements CommandExecutor {

    private final MAuth plugin;

    public ResetPasswordCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (!(sender instanceof ConsoleCommandSender)) {
            msg.send(sender, "admin.reset-console-only");
            return true;
        }
        if (!sender.hasPermission("mauth.command.reset")) {
            msg.send(sender, "admin.no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "admin.reset-usage");
            return true;
        }

        String target = args[0];
        plugin.submit(() -> plugin.getPasswordResetService().resetByUsername(target), (result, error) -> {
            if (error != null) {
                plugin.getLogger().severe("password reset task failed: " + error.getMessage());
                msg.send(sender, "auth.database-error");
                return;
            }
            switch (result.status) {
                case SUCCESS -> {
                    msg.send(sender, "admin.reset-success", MessageUtil.ph("player", target));
                    msg.send(sender, "admin.reset-password", MessageUtil.ph("password", result.newPassword));
                    msg.send(sender, "admin.reset-warning");
                }
                case NOT_FOUND -> msg.send(sender, "admin.reset-not-found");
                case DB_ERROR -> msg.send(sender, "auth.database-error");
                default -> msg.send(sender, "admin.unexpected-status",
                        MessageUtil.ph("status", result.status.name()));
            }
        });
        return true;
    }
}
