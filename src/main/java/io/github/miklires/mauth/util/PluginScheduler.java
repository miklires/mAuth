package io.github.miklires.mauth.util;

import io.github.miklires.mauth.MAuth;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public class PluginScheduler {

    private final MAuth plugin;

    public PluginScheduler(MAuth plugin) {
        this.plugin = plugin;
    }

    public void global(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    public void player(Player player, Runnable task) {
        player.getScheduler().execute(plugin, task, null, 1L);
    }

    public void playerLater(Player player, Runnable task, long ticks) {
        player.getScheduler().execute(plugin, task, null, Math.max(1L, ticks));
    }

    public void async(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduled -> task.run());
    }

    public void asyncTimer(Runnable task, long delay, long period, TimeUnit unit) {
        plugin.getServer().getAsyncScheduler().runAtFixedRate(plugin, scheduled -> task.run(),
                delay, period, unit);
    }
}
