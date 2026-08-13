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
                    .timeout(Duration.ofMillis(plugin.getConfigManager().getUpdateTimeoutMillis()))
                    .header("User-Agent", "miklires/mAuth/" + plugin.getPluginMeta().getVersion())
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.getLogger().warning("update check returned " + response.statusCode());
                return;
            }
            Matcher matcher = VERSION.matcher(response.body());
            String current = plugin.getPluginMeta().getVersion();
            String latest = current;
            while (matcher.find()) {
                String found = matcher.group(1);
                if (isNewer(found, latest)) latest = found;
            }
            if (isNewer(latest, current)) plugin.getLogger().info("mAuth " + latest + " is available on Modrinth");
        } catch (Exception e) {
            plugin.getLogger().warning("update check failed: " + e.getMessage());
        }
    }

    static boolean isNewer(String candidate, String current) {
        Version left = Version.parse(candidate);
        Version right = Version.parse(current);
        if (left == null || right == null) return false;
        int number = left.compareNumbers(right);
        if (number != 0) return number > 0;
        if (left.preRelease == null) return right.preRelease != null;
        if (right.preRelease == null) return false;
        return comparePreRelease(left.preRelease, right.preRelease) > 0;
    }

    private static int comparePreRelease(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            if (i >= a.length) return -1;
            if (i >= b.length) return 1;
            boolean an = a[i].matches("\\d+");
            boolean bn = b[i].matches("\\d+");
            int compared;
            if (an && bn) compared = Long.compare(Long.parseLong(a[i]), Long.parseLong(b[i]));
            else if (an != bn) compared = an ? -1 : 1;
            else compared = a[i].compareToIgnoreCase(b[i]);
            if (compared != 0) return compared;
        }
        return 0;
    }

    private record Version(long major, long minor, long patch, String preRelease) {
        static Version parse(String raw) {
            Matcher matcher = Pattern.compile(
                    "^v?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([0-9A-Za-z.-]+))?(?:\\+.*)?$")
                    .matcher(raw.trim());
            if (!matcher.matches()) return null;
            try {
                return new Version(Long.parseLong(matcher.group(1)), number(matcher.group(2)),
                        number(matcher.group(3)), matcher.group(4));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static long number(String value) {
            return value == null ? 0 : Long.parseLong(value);
        }

        int compareNumbers(Version other) {
            int result = Long.compare(major, other.major);
            if (result == 0) result = Long.compare(minor, other.minor);
            if (result == 0) result = Long.compare(patch, other.patch);
            return result;
        }
    }
}
