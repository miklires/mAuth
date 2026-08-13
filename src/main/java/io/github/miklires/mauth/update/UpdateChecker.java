package io.github.miklires.mauth.update;

import io.github.miklires.mauth.MAuth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpdateChecker {

    private static final Pattern VERSION = Pattern.compile("\\\"version_number\\\":\\\"([^\\\"]+)\\\"");
    private final MAuth plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public UpdateChecker(MAuth plugin) {
        this.plugin = plugin;
    }

    public void check() {
        String project = plugin.getConfigManager().getModrinthProjectId();
        if (!plugin.getConfigManager().isUpdateCheckEnabled() || project.isBlank()) return;
        try {
            String encoded = URLEncoder.encode(project, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.modrinth.com/v2/project/" + encoded + "/version"))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "miklires/mAuth/" + plugin.getPluginMeta().getVersion())
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().warning("update check returned " + response.statusCode());
                return;
            }
            Matcher matcher = VERSION.matcher(response.body());
            if (!matcher.find()) return;
            String latest = matcher.group(1);
            String current = plugin.getPluginMeta().getVersion();
            if (!latest.equals(current)) plugin.getLogger().info("mAuth " + latest + " is available on Modrinth");
        } catch (Exception e) {
            plugin.getLogger().warning("update check failed: " + e.getMessage());
        }
    }
}
