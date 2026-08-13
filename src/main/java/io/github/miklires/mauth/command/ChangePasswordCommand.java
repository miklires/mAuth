package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.AuthManager;
import io.github.miklires.mauth.auth.PasswordValidator;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
            msg.send(player, "auth.change-password-usage");
            return true;
        }

        PasswordValidator.Result validation = plugin.getPasswordValidator().validate(args[1], player.getName());
        switch (validation) {
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
            default -> { }
        }

        String username = player.getName();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        plugin.getAuthManager().changePassword(username, ip, args[0], args[1]).whenComplete((result, error) ->
                plugin.getPluginScheduler().player(player, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        plugin.getLogger().severe("password change task failed: " + error.getMessage());
                        msg.send(player, "auth.database-error");
                        return;
                    }
                    handleResult(player, msg, result);
                }));
        return true;
    }

    private void handleResult(Player player, MessageUtil msg, AuthManager.ChangePasswordResult result) {
        switch (result) {
            case NOT_REGISTERED -> msg.send(player, "auth.please-register");
            case WRONG_OLD -> msg.send(player, "auth.wrong-password");
            case DB_ERROR -> msg.send(player, "auth.database-error");
            case SUCCESS -> msg.send(player, "auth.password-changed");
        }
    }
}
