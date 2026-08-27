package io.github.miklires.mauth.auth;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.util.MessageUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns authentication deadlines and their optional countdown boss bars. */
public final class AuthTimeoutManager {

    private final MAuth plugin;
    private final Map<UUID, Countdown> countdowns = new ConcurrentHashMap<>();

    public AuthTimeoutManager(MAuth plugin) {
        this.plugin = plugin;
    }

    public void startLogin(Player player) {
        start(player, false, plugin.getConfigManager().getLoginTimeout());
    }

    public void startRegistration(Player player) {
        start(player, true, plugin.getConfigManager().getRegistrationTimeout());
    }

    private void start(Player player, boolean registration, int totalSeconds) {
        cancel(player);
        if (totalSeconds <= 0) return;

        BossBar bar = null;
        if (plugin.getConfigManager().isAuthBossBarEnabled()) {
            bar = BossBar.bossBar(title(player, registration, totalSeconds), 1.0f,
                    color(plugin.getConfigManager().getAuthBossBarColor()),
                    overlay(plugin.getConfigManager().getAuthBossBarOverlay()));
            player.showBossBar(bar);
        }

        Countdown countdown = new Countdown(UUID.randomUUID(),
                System.nanoTime() + totalSeconds * 1_000_000_000L,
                totalSeconds, registration, bar);
        countdowns.put(player.getUniqueId(), countdown);
        tick(player, countdown);
    }

    private void tick(Player player, Countdown expected) {
        Countdown current = countdowns.get(player.getUniqueId());
        if (current == null || !current.id.equals(expected.id)) return;
        if (!player.isOnline() || plugin.getSessionManager().isAuthenticated(player)) {
            cancel(player);
            return;
        }

        long nanos = Math.max(0L, current.deadlineNanos - System.nanoTime());
        int remaining = (int) Math.ceil(nanos / 1_000_000_000.0);
        if (remaining <= 0) {
            cancel(player);
            player.kick(plugin.getMessageUtil().getPlain(player, "auth.auth-timeout"));
            return;
        }

        if (current.bar != null) {
            current.bar.name(title(player, current.registration, remaining));
            current.bar.progress(Math.max(0.0f, Math.min(1.0f,
                    remaining / (float) current.totalSeconds)));
        }
        plugin.getPluginScheduler().playerLater(player, () -> tick(player, expected), 20L);
    }

    public void cancel(Player player) {
        Countdown removed = countdowns.remove(player.getUniqueId());
        if (removed != null && removed.bar != null) player.hideBossBar(removed.bar);
    }

    public void close() {
        for (Player player : plugin.getServer().getOnlinePlayers()) cancel(player);
        countdowns.clear();
    }

    private Component title(Player player, boolean registration, int seconds) {
        String key = registration ? "auth.bossbar-register" : "auth.bossbar-login";
        return plugin.getMessageUtil().getPlain(player, key, MessageUtil.ph("seconds", seconds));
    }

    private BossBar.Color color(String configured) {
        try {
            return BossBar.Color.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            plugin.getLogger().warning("invalid auth bossbar color: " + configured + "; using RED");
            return BossBar.Color.RED;
        }
    }

    private BossBar.Overlay overlay(String configured) {
        try {
            return BossBar.Overlay.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            plugin.getLogger().warning("invalid auth bossbar overlay: " + configured + "; using PROGRESS");
            return BossBar.Overlay.PROGRESS;
        }
    }

    private record Countdown(UUID id, long deadlineNanos, int totalSeconds,
                             boolean registration, BossBar bar) {
    }
}
