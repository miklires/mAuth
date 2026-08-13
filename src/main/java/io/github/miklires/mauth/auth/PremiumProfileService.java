package io.github.miklires.mauth.auth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class PremiumProfileService {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicLong nextRequestAt = new AtomicLong();

    public Lookup lookup(String username) {
        String key = username.toLowerCase();
        CacheEntry cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now) return cached.lookup;
        if (!reserveRequest(now)) return new Lookup(Status.RATE_LIMITED, null);

        Lookup result = request(username);
        long ttl = result.status == Status.FOUND ? 600_000L : 120_000L;
        if (result.status != Status.UNAVAILABLE && result.status != Status.RATE_LIMITED) {
            cache.put(key, new CacheEntry(result, now + ttl));
        }
        return result;
    }

    private boolean reserveRequest(long now) {
        while (true) {
            long allowed = nextRequestAt.get();
            if (now < allowed) return false;
            if (nextRequestAt.compareAndSet(allowed, now + 1_000L)) return true;
        }
    }

    private Lookup request(String username) {
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204 || response.statusCode() == 404) {
                return new Lookup(Status.NOT_FOUND, null);
            }
            if (response.statusCode() == 429) return new Lookup(Status.RATE_LIMITED, null);
            if (response.statusCode() != 200) return new Lookup(Status.UNAVAILABLE, null);
            String id = jsonString(response.body(), "id");
            return id == null ? new Lookup(Status.UNAVAILABLE, null)
                    : new Lookup(Status.FOUND, parseUuid(id));
        } catch (Exception e) {
            return new Lookup(Status.UNAVAILABLE, null);
        }
    }

    private String jsonString(String json, String key) {
        String token = "\"" + key + "\"";
        int keyAt = json.indexOf(token);
        if (keyAt < 0) return null;
        int colon = json.indexOf(':', keyAt + token.length());
        int start = json.indexOf('"', colon + 1);
        int end = start < 0 ? -1 : json.indexOf('"', start + 1);
        return start < 0 || end < 0 ? null : json.substring(start + 1, end);
    }

    private UUID parseUuid(String raw) {
        String value = raw.replace("-", "");
        if (value.length() != 32) throw new IllegalArgumentException("invalid profile uuid");
        return UUID.fromString(value.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    public enum Status {
        FOUND, NOT_FOUND, RATE_LIMITED, UNAVAILABLE
    }

    public record Lookup(Status status, UUID uuid) {
    }

    private record CacheEntry(Lookup lookup, long expiresAt) {
    }
}
