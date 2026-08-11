package io.github.miklires.mauth.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.AuthManager;
import io.github.miklires.mauth.util.MessageUtil;

public class LoginCommand implements CommandExecutor {

    private final MAuth plugin;

    public LoginCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.login")) {
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

        if (args.length != 1) {
            msg.send(player, "auth.please-login");
            return true;
        }

        AuthManager.LoginResult r = plugin.getAuthManager().login(player, args[0]);
        switch (r) {
            case NOT_REGISTERED -> msg.send(player, "auth.please-register");
            case WRONG_PASSWORD -> msg.send(player, "auth.wrong-password");
            case LOCKED_OUT -> {
                long sec = plugin.getSessionManager().getLockoutRemainingSeconds(player.getName());
                msg.send(player, "auth.too-many-attempts", MessageUtil.ph("seconds", (int) sec));
            }
            case DB_ERROR -> player.sendMessage("§cОшибка БД, попробуй позже.");
            case DISCORD_REQUIRED -> {
                String code = plugin.getLinkCodeManager().generateCode(player.getName());
                player.kick(plugin.getMessageUtil().getPlain("auth.kick-discord-required",
                        MessageUtil.ph("code", code),
                        MessageUtil.ph("bot_name", plugin.getConfigManager().getDiscordBotName()),
                        MessageUtil.ph("server_name", plugin.getConfigManager().getDiscordServerName()),
                        MessageUtil.ph("discord_link", plugin.getConfigManager().getDiscordInviteLink())));
            }
            case SUCCESS -> msg.send(player, "auth.logged-in");
        }
        return true;
    }
}
