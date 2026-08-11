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
            sender.sendMessage("§e/mauthreset <игрок>");
            return true;
        }

        String target = args[0];
        PasswordResetService.ResetResult result = plugin.getPasswordResetService().resetByUsername(target);

        switch (result.status) {
            case SUCCESS -> {
                msg.send(sender, "admin.reset-success", MessageUtil.ph("player", target));
                sender.sendMessage("§a§lНовый пароль: §f" + result.newPassword);
                sender.sendMessage("§7Передай игроку безопасным способом. Пароль больше не будет показан.");
            }
            case NOT_FOUND -> msg.send(sender, "admin.reset-not-found");
            case DB_ERROR -> sender.sendMessage("§cОшибка БД.");
            default -> sender.sendMessage("§cНеожиданный статус: " + result.status);
        }
        return true;
    }
}
