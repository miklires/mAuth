package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.AuthManager;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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

        String username = player.getName();
        if (plugin.getSessionManager().isLockedOut(username)) {
            long seconds = plugin.getSessionManager().getLockoutRemainingSeconds(username);
            msg.send(player, "auth.too-many-attempts", MessageUtil.ph("seconds", (int) seconds));
            return true;
        }
        if (!plugin.getSessionManager().beginLogin(player.getUniqueId())) {
            msg.send(player, "auth.login-pending");
            return true;
        }
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        plugin.getAuthManager().login(username, ip, player.getUniqueId(), args[0]).whenComplete((attempt, error) -> {
            plugin.getSessionManager().endLogin(player.getUniqueId());
            plugin.getPluginScheduler().player(player, () -> {
                if (!player.isOnline()) return;
                if (error != null) {
                    plugin.getLogger().severe("login task failed: " + error.getMessage());
                    msg.send(player, "auth.database-error");
                    return;
                }
                handleResult(player, msg, attempt);
            });
        });
        return true;
    }

    private void handleResult(Player player, MessageUtil msg, AuthManager.LoginAttempt attempt) {
        if (attempt.newIp()) msg.send(player, "auth.new-ip-notice");
        if (attempt.newDevice() && plugin.getConfigManager().getNewDevicePolicy().equals("notify")) {
            msg.send(player, "auth.new-device-notice");
        }
        switch (attempt.result()) {
            case NOT_REGISTERED -> msg.send(player, "auth.please-register");
            case WRONG_PASSWORD -> {
                plugin.getSessionManager().recordFailedAttempt(player.getName());
                msg.send(player, "auth.wrong-password");
            }
            case LOCKED_OUT -> {
                long seconds = plugin.getSessionManager().getLockoutRemainingSeconds(player.getName());
                msg.send(player, "auth.too-many-attempts", MessageUtil.ph("seconds", (int) seconds));
            }
            case DB_ERROR -> msg.send(player, "auth.database-error");
            case DISCORD_REQUIRED -> {
                String code = plugin.getLinkCodeManager().generateCode(player.getName());
                player.kick(plugin.getMessageUtil().getPlain(player, "auth.kick-discord-required",
                        MessageUtil.ph("code", code),
                        MessageUtil.ph("bot_name", plugin.getConfigManager().getDiscordBotName()),
                        MessageUtil.ph("server_name", plugin.getConfigManager().getDiscordServerName()),
                        MessageUtil.ph("discord_link", plugin.getConfigManager().getDiscordInviteLink())));
            }
            case TOTP_REQUIRED -> {
                plugin.getTotpService().beginLogin(player.getUniqueId(), attempt.account());
                msg.send(player, "totp.login-required");
            }
            case VPN_BLOCKED -> msg.send(player, "auth.vpn-blocked");
            case DEVICE_BLOCKED -> msg.send(player, "auth.new-device-denied");
            case SUCCESS -> {
                plugin.getAuthManager().completeLogin(player, attempt.account());
                msg.send(player, "auth.logged-in");
            }
        }
    }
}
