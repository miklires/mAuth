package io.github.miklires.mauth.velocity;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityProfileService {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public Lookup lookup(String username) {
        String key = username.toLowerCase();
        Entry entry = cache.get(key);
        long now = System.currentTimeMillis();
        if (entry != null && entry.expiresAt > now) return entry.lookup;
        Lookup result = request(username);
        if (result.result != Result.UNAVAILABLE) {
            cache.put(key, new Entry(result, now + (result.result == Result.PREMIUM ? 600_000L : 120_000L)));
        }
        return result;
    }

    private Lookup request(String username) {
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                java.util.regex.Matcher id = java.util.regex.Pattern.compile("\\\"id\\\":\\\"([0-9a-fA-F]{32})\\\"")
                        .matcher(response.body());
                return id.find() ? new Lookup(Result.PREMIUM, uuid(id.group(1)))
                        : new Lookup(Result.UNAVAILABLE, null);
            }
            if (response.statusCode() == 204 || response.statusCode() == 404) return new Lookup(Result.CRACKED, null);
            return new Lookup(Result.UNAVAILABLE, null);
        } catch (Exception e) {
            return new Lookup(Result.UNAVAILABLE, null);
        }
    }

    private java.util.UUID uuid(String value) {
        String dashed = value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20);
        return java.util.UUID.fromString(dashed);
    }

    public enum Result {
        PREMIUM, CRACKED, UNAVAILABLE
    }

    public record Lookup(Result result, java.util.UUID uuid) {
    }

    private record Entry(Lookup lookup, long expiresAt) {
    }
}
