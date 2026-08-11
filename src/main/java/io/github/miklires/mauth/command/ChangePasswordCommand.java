package io.github.miklires.mauth.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.AuthManager;
import io.github.miklires.mauth.auth.PasswordValidator;
import io.github.miklires.mauth.util.MessageUtil;

public class ChangePasswordCommand implements CommandExecutor {

    private final MAuth plugin;

    public ChangePasswordCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.changepassword")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        MessageUtil msg = plugin.getMessageUtil();

        if (!plugin.getSessionManager().isAuthenticated(player)) {
            msg.send(player, "auth.not-logged-in");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("§e/changepassword <старый> <новый>");
            return true;
        }

        String oldPw = args[0];
        String newPw = args[1];

        PasswordValidator.Result v = plugin.getPasswordValidator().validate(newPw, player.getName());
        switch (v) {
            case TOO_SHORT -> {
                msg.send(player, "auth.password-too-short",
                        MessageUtil.ph("count", plugin.getConfigManager().getMinPasswordLength()));
                return true;
            }
            case TOO_LONG -> {
                msg.send(player, "auth.password-too-long",
                        MessageUtil.ph("count", plugin.getConfigManager().getMaxPasswordLength()));
                return true;
            }
            case TOO_WEAK, TOO_SIMPLE -> {
                msg.send(player, "auth.password-too-weak");
                return true;
            }
            default -> {}
        }

        AuthManager.ChangePasswordResult r = plugin.getAuthManager().changePassword(player, oldPw, newPw);
        switch (r) {
            case NOT_REGISTERED -> msg.send(player, "auth.please-register");
            case WRONG_OLD -> msg.send(player, "auth.wrong-password");
            case DB_ERROR -> player.sendMessage("§cОшибка БД.");
            case SUCCESS -> msg.send(player, "auth.password-changed");
        }
        return true;
    }
}
