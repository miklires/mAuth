package io.github.miklires.mauth.command;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.captcha.CaptchaManager;
import io.github.miklires.mauth.util.MessageUtil;

import java.util.concurrent.CompletableFuture;

public class CaptchaCommand implements CommandExecutor {

    private final MAuth plugin;

    public CaptchaCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.captcha")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        MessageUtil msg = plugin.getMessageUtil();

        if (!plugin.getCaptchaManager().hasPending(player)) {
            msg.send(player, "captcha.no-pending");
            return true;
        }
        if (args.length != 1) {
            msg.send(player, "captcha.usage");
            return true;
        }

        CaptchaManager.CheckResult r = plugin.getCaptchaManager().check(player, args[0]);
        switch (r) {
            case OK -> {
                msg.send(player, "captcha.ok");
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return plugin.getAccountRepository().findByUsername(player.getName()).isPresent();
                    } catch (java.sql.SQLException e) {
                        throw new IllegalStateException(e);
                    }
                }, plugin.getAuthExecutor()).whenComplete((exists, error) ->
                        plugin.getPluginScheduler().player(player, () -> {
                            if (!player.isOnline()) return;
                            if (error != null) {
                                plugin.getLogger().warning("db error on captcha post-check: " + error.getMessage());
                                msg.send(player, "auth.database-error");
                                return;
                            }
                            msg.send(player, exists ? "auth.please-login" : "auth.please-register");
                        }));
            }
            case WRONG -> {
                CaptchaManager.CaptchaState state = plugin.getCaptchaManager().getState(player);
                int left = state != null ? state.getAttemptsLeft() : 0;
                msg.send(player, "captcha.wrong", MessageUtil.ph("attempts", left));
            }
            case OUT_OF_ATTEMPTS -> {
                String ip = player.getAddress() != null
                        ? player.getAddress().getAddress().getHostAddress() : null;
                plugin.getAuditLogger().log(
                        io.github.miklires.mauth.audit.AuditEvent.CAPTCHA_FAILED_OUT_OF_ATTEMPTS,
                        player.getName(), ip);
                player.kick(plugin.getMessageUtil().getPlain(player, "captcha.kick-out-of-attempts"));
            }
            case NO_CAPTCHA -> msg.send(player, "captcha.no-pending");
        }
        return true;
    }
}
