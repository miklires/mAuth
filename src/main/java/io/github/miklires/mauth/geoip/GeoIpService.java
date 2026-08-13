package io.github.miklires.mauth.geoip;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.api.DiscordIntegration;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class GeoIpService implements AutoCloseable {

    private final MAuth plugin;
    private final GeoIpResolver resolver;

    public GeoIpService(MAuth plugin) {
        this.plugin = plugin;
        this.resolver = new GeoIpResolver(plugin);
    }

    public void checkAsync(String username, String ip, String discordId) {
        if (!plugin.getConfigManager().isGeoIpEnabled()) return;
        if (ip == null) return;

        plugin.getPluginScheduler().async(() -> checkNow(username, ip, discordId));
    }

    private void checkNow(String username, String ip, String discordId) {
        try {
            Optional<String> cached = plugin.getKnownIpRepository().getCountry(username, ip);
            if (cached.isPresent()) {
                return;
            }

            Optional<GeoIpResolver.GeoInfo> geo = resolver.resolve(ip);
            if (geo.isEmpty()) return;

            GeoIpResolver.GeoInfo info = geo.get();
            if ("LOCAL".equals(info.code())) {
                plugin.getKnownIpRepository().setCountry(username, ip, "LOCAL");
                return;
            }

            List<String> knownCountries = plugin.getKnownIpRepository().getKnownCountries(username);
            boolean isNewCountry = !knownCountries.isEmpty() && !knownCountries.contains(info.code());

            plugin.getKnownIpRepository().setCountry(username, ip, info.code());

            DiscordIntegration discord = plugin.getServer().getServicesManager().load(DiscordIntegration.class);
            if (isNewCountry && discordId != null
                    && plugin.getConfigManager().isGeoIpNotifyDiscord()
                    && discord != null && discord.isReady()) {
                discord.notifyNewCountry(discordId, username, ip,
                        new DiscordIntegration.LocationInfo(info.code(), info.name(), info.city()));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("geoip db error for " + username + ": " + e.getMessage());
        }
    }

    @Override
    public void close() {
        resolver.close();
    }
}
