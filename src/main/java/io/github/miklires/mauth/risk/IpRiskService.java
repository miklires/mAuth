package io.github.miklires.mauth.risk;

import io.github.miklires.mauth.MAuth;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class IpRiskService {

    private final MAuth plugin;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private IpRiskProvider provider;

    public IpRiskService(MAuth plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cache.clear();
        String selected = plugin.getConfigManager().getVpnProvider();
        if (selected.equals("none")) {
            provider = null;
            return;
        }
        if (selected.equals("http-json")) {
            provider = new HttpJsonRiskProvider(
                    plugin.getConfigManager().getVpnUrl(),
                    plugin.getConfigManager().getVpnApiKeyHeader(),
                    plugin.getConfigManager().getVpnApiKey(),
                    plugin.getConfigManager().getVpnBooleanField(),
                    plugin.getConfigManager().getVpnTimeoutMillis());
            return;
        }
        IpRiskProvider registered = plugin.getServer().getServicesManager().load(IpRiskProvider.class);
        if (registered != null && registered.name().equalsIgnoreCase(selected)) {
            provider = registered;
            return;
        }
        provider = ServiceLoader.load(IpRiskProvider.class, plugin.getClass().getClassLoader()).stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.name().equalsIgnoreCase(selected))
                .findFirst()
                .orElse(null);
        if (provider == null) plugin.getLogger().warning("VPN provider not found: " + selected);
    }

    public Decision check(String ip) {
        if (provider == null || ip == null || ip.isBlank()) return Decision.ALLOW;
        long now = System.currentTimeMillis();
        CacheEntry cached = cache.get(ip);
        IpRiskProvider.RiskResult result;
        if (cached != null && cached.expiresAt > now) {
            result = cached.result;
        } else {
            result = provider.check(ip);
            if (result.status() != IpRiskProvider.Status.UNAVAILABLE) {
                cache.put(ip, new CacheEntry(result,
                        now + plugin.getConfigManager().getVpnCacheSeconds() * 1_000L));
            }
        }
        return switch (result.status()) {
            case SAFE -> Decision.ALLOW;
            case RISKY -> plugin.getConfigManager().isVpnBlocking() ? Decision.BLOCK : Decision.ALLOW;
            case UNAVAILABLE -> plugin.getConfigManager().isVpnFailClosed() ? Decision.BLOCK : Decision.ALLOW;
        };
    }

    public enum Decision {
        ALLOW, BLOCK
    }

    private record CacheEntry(IpRiskProvider.RiskResult result, long expiresAt) {
    }
}
