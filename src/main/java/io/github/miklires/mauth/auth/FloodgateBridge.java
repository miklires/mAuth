package io.github.miklires.mauth.auth;

import io.github.miklires.mauth.MAuth;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public class FloodgateBridge {

    private final MAuth plugin;
    private Method getInstance;
    private Method isFloodgatePlayer;
    private Method getPlayer;
    private Method getXuid;

    public FloodgateBridge(MAuth plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        if (!plugin.getConfigManager().isFloodgateEnabled()
                || plugin.getServer().getPluginManager().getPlugin("floodgate") == null) {
            return;
        }
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Class<?> player = Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer");
            getInstance = api.getMethod("getInstance");
            isFloodgatePlayer = api.getMethod("isFloodgatePlayer", UUID.class);
            getPlayer = api.getMethod("getPlayer", UUID.class);
            getXuid = player.getMethod("getXuid");
            plugin.getLogger().info("Floodgate integration enabled");
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Floodgate API is incompatible: " + e.getMessage());
            getInstance = null;
        }
    }

    public Optional<String> getXuid(UUID uuid) {
        if (getInstance == null) return Optional.empty();
        try {
            Object api = getInstance.invoke(null);
            if (!(boolean) isFloodgatePlayer.invoke(api, uuid)) return Optional.empty();
            Object player = getPlayer.invoke(api, uuid);
            if (player == null) return Optional.empty();
            String xuid = (String) getXuid.invoke(player);
            return xuid == null || xuid.isBlank() ? Optional.empty() : Optional.of(xuid);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().warning("Floodgate lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }
}
