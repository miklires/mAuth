package io.github.miklires.mauth.risk;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

public class HttpJsonRiskProvider implements IpRiskProvider {

    private final HttpClient client;
    private final String urlTemplate;
    private final String header;
    private final String apiKey;
    private final String booleanField;
    private final Duration timeout;

    public HttpJsonRiskProvider(String urlTemplate, String header, String apiKey,
                                String booleanField, int timeoutMillis) {
        this.urlTemplate = urlTemplate;
        this.header = header;
        this.apiKey = apiKey;
        this.booleanField = booleanField;
        this.timeout = Duration.ofMillis(Math.max(500, timeoutMillis));
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public String name() {
        return "http-json";
    }

    @Override
    public RiskResult check(String ip) {
        if (urlTemplate.isBlank() || !urlTemplate.contains("{ip}") || booleanField.isBlank()) {
            return RiskResult.unavailable("provider is not configured");
        }
        try {
            String encoded = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                            URI.create(urlTemplate.replace("{ip}", encoded)))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .GET();
            if (!header.isBlank() && !apiKey.isBlank()) builder.header(header, apiKey);
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return RiskResult.unavailable("http " + response.statusCode());
            }
            Boolean risky = readBoolean(response.body(), booleanField);
            if (risky == null) return RiskResult.unavailable("missing boolean field");
            return risky ? RiskResult.risky(booleanField) : RiskResult.safe();
        } catch (Exception e) {
            return RiskResult.unavailable(e.getClass().getSimpleName().toLowerCase(Locale.ROOT));
        }
    }

    static Boolean readBoolean(String json, String field) {
        String token = "\"" + field + "\"";
        int key = json.indexOf(token);
        if (key < 0) return null;
        int colon = json.indexOf(':', key + token.length());
        if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        if (json.regionMatches(true, start, "true", 0, 4)) return true;
        if (json.regionMatches(true, start, "false", 0, 5)) return false;
        return null;
    }
}
