package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.AuthManager;
import io.github.miklires.mauth.auth.DiscordMode;
import io.github.miklires.mauth.auth.PasswordValidator;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RegisterCommand implements CommandExecutor {

    private final MAuth plugin;

    public RegisterCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.register")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        MessageUtil msg = plugin.getMessageUtil();

        if (plugin.getSessionManager().isAuthenticated(player)) {
            msg.send(player, "auth.already-logged-in");
            return true;
        }
        if (plugin.getCaptchaManager().hasPending(player)) {
            msg.send(player, "captcha.required");
            return true;
        }
        if (args.length != 2) {
            msg.send(player, "auth.please-register");
            return true;
        }

        String password = args[0];
        PasswordValidator.Result validation = plugin.getPasswordValidator().validate(password, player.getName());
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
        plugin.getAuthManager().register(username, ip, password, args[1]).whenComplete((result, error) ->
                plugin.getPluginScheduler().player(player, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        plugin.getLogger().severe("register task failed: " + error.getMessage());
                        msg.send(player, "auth.database-error");
                        return;
                    }
                    handleResult(player, msg, result);
                }));
        return true;
    }

    private void handleResult(Player player, MessageUtil msg, AuthManager.RegisterResult result) {
        switch (result) {
            case PASSWORD_MISMATCH -> msg.send(player, "auth.passwords-mismatch");
            case ALREADY_EXISTS -> msg.send(player, "auth.please-login");
            case TOO_MANY_ACCOUNTS -> msg.send(player, "auth.too-many-accounts");
            case DB_ERROR -> msg.send(player, "auth.database-error");
            case SUCCESS -> {
                plugin.getSessionManager().markAuthenticated(player);
                DiscordMode mode = plugin.getConfigManager().getDiscordMode();
                if (mode.requiresForRegistration()) {
                    String code = plugin.getLinkCodeManager().generateCode(player.getName());
                    player.kick(plugin.getMessageUtil().getPlain(player, "auth.kick-after-register",
                            MessageUtil.ph("code", code),
                            MessageUtil.ph("bot_name", plugin.getConfigManager().getDiscordBotName()),
                            MessageUtil.ph("server_name", plugin.getConfigManager().getDiscordServerName()),
                            MessageUtil.ph("discord_link", plugin.getConfigManager().getDiscordInviteLink())));
                } else {
                    plugin.getLimboWorldManager().returnFromLimbo(player, null);
                    msg.send(player, "auth.registered");
                }
            }
        }
    }
}
