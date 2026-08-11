package io.github.miklires.mauth.geoip;

import io.github.miklires.mauth.MAuth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class GeoIpResolver {

    private final MAuth plugin;
    private final HttpClient client;

    public GeoIpResolver(MAuth plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Optional<GeoInfo> resolve(String ip) {
        if (ip == null || ip.isEmpty()) return Optional.empty();
        if (isLocalIp(ip)) return Optional.of(new GeoInfo("LOCAL", "Local network"));

        String url = plugin.getConfigManager().getGeoIpEndpoint().replace("{ip}", ip);
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(plugin.getConfigManager().getGeoIpTimeoutSeconds()))
                    .header("User-Agent", "mAuth-plugin")
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                plugin.getLogger().warning("geoip api returned " + resp.statusCode() + " for " + ip);
                return Optional.empty();
            }
            return parse(resp.body());
        } catch (Exception e) {
            plugin.getLogger().warning("geoip lookup for " + ip + " failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<GeoInfo> parse(String json) {
        String status = extractJsonString(json, "status");
        if (!"success".equals(status)) return Optional.empty();
        String code = extractJsonString(json, "countryCode");
        String name = extractJsonString(json, "country");
        if (code == null) return Optional.empty();
        return Optional.of(new GeoInfo(code, name != null ? name : code));
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    private boolean isLocalIp(String ip) {
        return ip.startsWith("127.")
                || ip.startsWith("10.")
                || ip.startsWith("192.168.")
                || ip.startsWith("172.16.") || ip.startsWith("172.17.") || ip.startsWith("172.18.")
                || ip.startsWith("172.19.") || ip.startsWith("172.2") || ip.startsWith("172.30.")
                || ip.startsWith("172.31.")
                || ip.equals("::1")
                || ip.startsWith("fc") || ip.startsWith("fd")
                || ip.equals("localhost");
    }

    public record GeoInfo(String code, String name) {}
}
