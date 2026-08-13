package io.github.miklires.mauth.geoip;

import com.maxmind.db.Reader;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import io.github.miklires.mauth.MAuth;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

public class GeoIpResolver implements AutoCloseable {

    private final MAuth plugin;
    private volatile DatabaseReader reader;
    private boolean warnedMissing;

    public GeoIpResolver(MAuth plugin) {
        this.plugin = plugin;
    }

    public Optional<GeoInfo> resolve(String ip) {
        if (ip == null || ip.isEmpty()) return Optional.empty();
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (isLocalIp(address)) return Optional.of(new GeoInfo("LOCAL", "Local network", null));
            DatabaseReader db = open();
            if (db == null) return Optional.empty();
            Optional<CityResponse> result = db.tryCity(address);
            if (result.isEmpty()) return Optional.empty();
            CityResponse response = result.get();
            String code = response.country().isoCode();
            if (code == null) return Optional.empty();
            String country = response.country().name();
            String city = response.city().name();
            return Optional.of(new GeoInfo(code, country != null ? country : code, city));
        } catch (Exception e) {
            plugin.getLogger().warning("geoip lookup failed: " + e.getMessage());
            return Optional.empty();
        }
    }

    private synchronized DatabaseReader open() {
        if (reader != null) return reader;
        File file = databaseFile();
        if (!file.exists() && plugin.getConfigManager().isGeoIpDownloadEnabled()) download(file);
        if (!file.exists()) {
            if (!warnedMissing) {
                plugin.getLogger().warning("geoip database not found: " + file.getAbsolutePath());
                warnedMissing = true;
            }
            return null;
        }
        try {
            reader = new DatabaseReader.Builder(file).fileMode(Reader.FileMode.MEMORY).build();
            plugin.getLogger().info("geoip database loaded: " + file.getName());
            return reader;
        } catch (Exception e) {
            plugin.getLogger().warning("cannot load geoip database: " + e.getMessage());
            return null;
        }
    }

    private File databaseFile() {
        File configured = new File(plugin.getConfigManager().getGeoIpDatabase());
        return configured.isAbsolute() ? configured : new File(plugin.getDataFolder(), configured.getPath());
    }

    private void download(File target) {
        String url = plugin.getConfigManager().getGeoIpDownloadUrl();
        if (url == null || url.isBlank()) return;
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            if (target.getParentFile() != null) Files.createDirectories(target.getParentFile().toPath());
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(2)).GET().build();
            HttpResponse<java.nio.file.Path> response = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofFile(tmp.toPath()));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(tmp.toPath());
                plugin.getLogger().warning("geoip download returned " + response.statusCode());
                return;
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("geoip database downloaded");
        } catch (Exception e) {
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignored) {}
            plugin.getLogger().warning("geoip download failed: " + e.getMessage());
        }
    }

    static boolean isLocalIp(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()) return true;
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    @Override
    public void close() {
        if (reader == null) return;
        try {
            reader.close();
        } catch (Exception e) {
            plugin.getLogger().warning("cannot close geoip database: " + e.getMessage());
        }
        reader = null;
    }

    public record GeoInfo(String code, String name, String city) {}
}
