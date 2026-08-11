package io.github.miklires.mauth.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.model.Account;

import java.sql.SQLException;
import java.util.Optional;

public class PlayerJoinListener implements Listener {

    private final MAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public PlayerJoinListener(MAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String username = event.getName().toLowerCase();
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty()) return;
            if (!plugin.getConfigManager().isWhitelistEnabled()) return;
            Account a = opt.get();
            if (!a.isWhitelisted()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                        mm.deserialize(plugin.getConfigManager().getKickMessage()));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on prelogin: " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    mm.deserialize("<red>Ошибка сервера, попробуй позже."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        String username = player.getName().toLowerCase();
        plugin.getCaptchaManager().getFloodDetector().recordConnection();

        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);

            if (opt.isEmpty()) {
                plugin.getLimboWorldManager().sendToLimbo(player);
                if (plugin.getCaptchaManager().shouldRequireForRegistration()) {
                    String captcha = plugin.getCaptchaManager().generateFor(player);
                    plugin.getMessageUtil().send(player, "captcha.welcome",
                            io.github.miklires.mauth.util.MessageUtil.ph("code", captcha));
                } else {
                    plugin.getMessageUtil().send(player, "auth.please-register");
                }
                scheduleAuthTimeout(player);
                return;
            }

            Account a = opt.get();

            if (a.isPremiumEnabled() && a.getPremiumUuid() != null
                    && a.getPremiumUuid().equals(player.getUniqueId())) {
                plugin.getSessionManager().markAuthenticated(player,
                        io.github.miklires.mauth.api.PlayerAuthenticatedEvent.AuthReason.SESSION);
                plugin.getMessageUtil().send(player, "auth.logged-in");
                return;
            }

            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
            if (ip != null && plugin.getSessionManager().hasValidIpSession(username, ip, player.getUniqueId())) {
                plugin.getSessionManager().markAuthenticated(player,
                        io.github.miklires.mauth.api.PlayerAuthenticatedEvent.AuthReason.SESSION);
                org.bukkit.Location saved = plugin.getLimboWorldManager().parseLocation(a.getLastLocation());
                org.bukkit.Location actual = plugin.getLimboWorldManager().returnFromLimbo(player, saved);
                if (saved == null && actual != null) {
                    a.setLastLocation(plugin.getLimboWorldManager().serializeLocation(actual));
                    plugin.getAccountRepository().update(a);
                }
                plugin.getMessageUtil().send(player, "auth.logged-in");
                return;
            }

            plugin.getLimboWorldManager().sendToLimbo(player);

            if (plugin.getCaptchaManager().shouldRequireForLogin(username, ip)) {
                String captcha = plugin.getCaptchaManager().generateFor(player);
                boolean isFlood = plugin.getCaptchaManager().getFloodDetector().isFloodActive();
                plugin.getMessageUtil().send(player,
                        isFlood ? "captcha.flood" : "captcha.new-ip",
                        io.github.miklires.mauth.util.MessageUtil.ph("code", captcha));
            } else {
                plugin.getMessageUtil().send(player, "auth.please-login");
            }
            scheduleAuthTimeout(player);
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on join: " + e.getMessage());
            player.kick(mm.deserialize("<red>Ошибка сервера."));
        }
    }

    private void scheduleAuthTimeout(org.bukkit.entity.Player player) {
        int timeout = plugin.getConfigManager().getAuthTimeout();
        if (timeout <= 0) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !plugin.getSessionManager().isAuthenticated(player)) {
                player.kick(plugin.getMessageUtil().getPlain("auth.auth-timeout"));
            }
        }, timeout * 20L);
    }
}
