package io.github.miklires.mauth.velocity;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CoreAccountService {

    private static final Pattern UUID_FIELD = Pattern.compile("\\\"uuid\\\":\\\"([^\\\"]*)\\\"");
    private final VelocityConfig cfg;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public CoreAccountService(VelocityConfig cfg) {
        this.cfg = cfg;
    }

    public State lookup(String username) {
        if (cfg.coreApiToken().isBlank()) return State.unavailable();
        try {
            String auth = Base64.getEncoder().encodeToString(
                    ("mauth:" + cfg.coreApiToken()).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.coreApiUrl()
                            + "/api/premium?username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(4)).header("Authorization", "Basic " + auth).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return State.unavailable();
            String body = response.body();
            if (body.contains("\"exists\":false")) return new State(true, false, false, null);
            Matcher uuid = UUID_FIELD.matcher(body);
            UUID value = uuid.find() && !uuid.group(1).isEmpty() ? UUID.fromString(uuid.group(1)) : null;
            return new State(true, true, body.contains("\"enabled\":true"), value);
        } catch (Exception e) {
            return State.unavailable();
        }
    }

    public record State(boolean available, boolean exists, boolean premiumEnabled, UUID premiumUuid) {
        static State unavailable() { return new State(false, false, false, null); }
    }
}
