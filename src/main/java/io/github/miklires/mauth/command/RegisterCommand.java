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

        String pw = args[0];
        String confirm = args[1];

        PasswordValidator.Result v = plugin.getPasswordValidator().validate(pw, player.getName());
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

        AuthManager.RegisterResult r = plugin.getAuthManager().register(player, pw, confirm);
        switch (r) {
            case PASSWORD_MISMATCH -> msg.send(player, "auth.passwords-mismatch");
            case ALREADY_EXISTS -> msg.send(player, "auth.please-login");
            case DB_ERROR -> player.sendMessage("§cОшибка БД, попробуй позже.");
            case SUCCESS -> {
                plugin.getSessionManager().markAuthenticated(player);
                String code = plugin.getLinkCodeManager().generateCode(player.getName());
                player.kick(plugin.getMessageUtil().getPlain("auth.kick-after-register",
                        MessageUtil.ph("code", code),
                        MessageUtil.ph("bot_name", plugin.getConfigManager().getDiscordBotName()),
                        MessageUtil.ph("server_name", plugin.getConfigManager().getDiscordServerName()),
                        MessageUtil.ph("discord_link", plugin.getConfigManager().getDiscordInviteLink())));
            }
        }
        return true;
    }
}
