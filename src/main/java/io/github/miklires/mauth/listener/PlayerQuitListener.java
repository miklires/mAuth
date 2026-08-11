package io.github.miklires.mauth.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.model.Account;

import java.sql.SQLException;
import java.util.Optional;

public class PlayerQuitListener implements Listener {

    private final MAuth plugin;

    public PlayerQuitListener(MAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        boolean wasAuthenticated = plugin.getSessionManager().isAuthenticated(player);
        plugin.getSessionManager().clear(player);
        plugin.getCaptchaManager().clear(player);

        if (!wasAuthenticated) return;
        if (plugin.getLimboWorldManager().isInLimbo(player)) return;

        String username = player.getName().toLowerCase();
        String serialized = plugin.getLimboWorldManager().serializeLocation(player.getLocation());

        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty()) return;
            Account a = opt.get();
            a.setLastLocation(serialized);
            plugin.getAccountRepository().update(a);
        } catch (SQLException e) {
            plugin.getLogger().warning("cannot save position for " + username + ": " + e.getMessage());
        }
    }
}
