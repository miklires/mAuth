package io.github.miklires.mauth.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.miklires.mauth.MAuth;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebPanelServer implements AutoCloseable {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MAuth plugin;
    private HttpServer server;
    private ExecutorService executor;
    private byte[] authorization;

    public WebPanelServer(MAuth plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().isWebEnabled()) return;
        String token = token();
        authorization = ("Basic " + Base64.getEncoder().encodeToString(
                ("mauth:" + token).getBytes(StandardCharsets.UTF_8))).getBytes(StandardCharsets.US_ASCII);
        try {
            server = HttpServer.create(new InetSocketAddress(
                    plugin.getConfigManager().getWebBind(), plugin.getConfigManager().getWebPort()), 32);
            server.createContext("/", this::handleIndex);
            server.createContext("/api/status", this::handleStatus);
            server.createContext("/api/premium", this::handlePremium);
            server.createContext("/api/sessions/terminate", this::handleTerminate);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.start();
            plugin.getLogger().info("web panel listening on " + plugin.getConfigManager().getWebBind()
                    + ":" + plugin.getConfigManager().getWebPort());
        } catch (IOException e) {
            plugin.getLogger().warning("cannot start web panel: " + e.getMessage());
        }
    }

    private String token() {
        String token = plugin.getConfig().getString("web.token", "");
        if (token != null && !token.isBlank()) return token;
        byte[] value = new byte[32];
        new SecureRandom().nextBytes(value);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        plugin.getConfig().set("web.token", token);
        plugin.saveConfig();
        plugin.getLogger().info("generated web panel token in config.yml");
        return token;
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        if (!exchange.getRequestMethod().equals("GET")) {
            respond(exchange, 405, "text/plain", "method not allowed");
            return;
        }
        try {
            Dashboard dashboard = dashboard();
            respond(exchange, 200, "text/html; charset=utf-8", html(dashboard));
        } catch (SQLException e) {
            respond(exchange, 500, "text/plain", "database error");
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        if (!exchange.getRequestMethod().equals("GET")) {
            respond(exchange, 405, "application/json", "{\"error\":\"method_not_allowed\"}");
            return;
        }
        try {
            Dashboard dashboard = dashboard();
            String json = "{\"accounts\":" + dashboard.accountCount
                    + ",\"sessions\":" + dashboard.sessionCount + "}";
            respond(exchange, 200, "application/json", json);
        } catch (SQLException e) {
            respond(exchange, 500, "application/json", "{\"error\":\"database\"}");
        }
    }

    private void handleTerminate(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        if (!exchange.getRequestMethod().equals("POST")
                || !"true".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("X-mAuth-Confirm"))) {
            respond(exchange, 405, "application/json", "{\"error\":\"confirmation_required\"}");
            return;
        }
        String username = query(exchange, "username");
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            respond(exchange, 400, "application/json", "{\"error\":\"invalid_username\"}");
            return;
        }
        try {
            plugin.getSessionRepository().invalidate(username);
            plugin.getSessionManager().forgetAll(username);
            plugin.getPluginScheduler().global(() -> {
                var player = plugin.getServer().getPlayerExact(username);
                if (player != null) {
                    plugin.getPluginScheduler().player(player, () -> {
                        plugin.getSessionManager().clear(player);
                        player.kick(plugin.getMessageUtil().getPlain(player, "sessions.all-terminated"));
                    });
                }
            });
            respond(exchange, 200, "application/json", "{\"ok\":true}");
        } catch (SQLException e) {
            respond(exchange, 500, "application/json", "{\"error\":\"database\"}");
        }
    }

    private void handlePremium(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) return;
        if (!exchange.getRequestMethod().equals("GET")) {
            respond(exchange, 405, "application/json", "{\"error\":\"method_not_allowed\"}");
            return;
        }
        String username = query(exchange, "username");
        if (username == null || !username.matches("[A-Za-z0-9_]{3,16}")) {
            respond(exchange, 400, "application/json", "{\"error\":\"invalid_username\"}");
            return;
        }
        try {
            var account = plugin.getAccountRepository().findByUsername(username);
            if (account.isEmpty()) {
                respond(exchange, 200, "application/json", "{\"exists\":false}");
                return;
            }
            var value = account.get();
            String uuid = value.getPremiumUuid() == null ? "" : value.getPremiumUuid().toString();
            respond(exchange, 200, "application/json", "{\"exists\":true,\"enabled\":"
                    + value.isPremiumEnabled() + ",\"uuid\":\"" + uuid + "\"}");
        } catch (SQLException e) {
            respond(exchange, 500, "application/json", "{\"error\":\"database\"}");
        }
    }

    private Dashboard dashboard() throws SQLException {
        int accountCount;
        int sessionCount;
        List<AccountRow> accounts = new ArrayList<>();
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement countAccounts = connection.prepareStatement("SELECT COUNT(*) FROM mauth_accounts");
             ResultSet accountCountResult = countAccounts.executeQuery()) {
            accountCountResult.next();
            accountCount = accountCountResult.getInt(1);
            try (PreparedStatement countSessions = connection.prepareStatement(
                    "SELECT COUNT(*) FROM mauth_sessions WHERE expires_at >= ?")) {
                countSessions.setLong(1, System.currentTimeMillis() / 1_000L);
                try (ResultSet sessionCountResult = countSessions.executeQuery()) {
                    sessionCountResult.next();
                    sessionCount = sessionCountResult.getInt(1);
                }
            }
            try (PreparedStatement list = connection.prepareStatement(
                    "SELECT username, registered_name, last_login_at, locked_until "
                            + "FROM mauth_accounts ORDER BY registered_at DESC LIMIT 100");
                 ResultSet rows = list.executeQuery()) {
                while (rows.next()) {
                    long lastLogin = rows.getLong("last_login_at");
                    boolean noLogin = rows.wasNull();
                    long locked = rows.getLong("locked_until");
                    boolean noLock = rows.wasNull();
                    accounts.add(new AccountRow(rows.getString("username"),
                            rows.getString("registered_name"),
                            noLogin ? null : lastLogin,
                            noLock ? null : locked));
                }
            }
        }
        return new Dashboard(accountCount, sessionCount, accounts);
    }

    private String html(Dashboard dashboard) {
        StringBuilder rows = new StringBuilder();
        for (AccountRow account : dashboard.accounts) {
            String name = account.registeredName == null ? account.username : account.registeredName;
            String lastLogin = account.lastLogin == null ? "never"
                    : DATE_FORMAT.format(Instant.ofEpochSecond(account.lastLogin));
            String status = account.lockedUntil != null
                    && account.lockedUntil > System.currentTimeMillis() / 1_000L ? "locked" : "active";
            rows.append("<tr><td>").append(escape(name)).append("</td><td>")
                    .append(escape(lastLogin)).append("</td><td>").append(status)
                    .append("</td><td><button onclick=\"terminate('")
                    .append(escapeJs(account.username)).append("')\">Terminate sessions</button></td></tr>");
        }
        return """
                <!doctype html><html><head><meta charset="utf-8"><title>mAuth</title>
                <style>body{font:15px system-ui;background:#111;color:#eee;max-width:1000px;margin:40px auto}
                table{width:100%%;border-collapse:collapse}td,th{padding:10px;border-bottom:1px solid #333;text-align:left}
                button{background:#b33;color:white;border:0;padding:7px 10px;border-radius:5px}</style></head><body>
                <h1>mAuth</h1><p>Accounts: %d &nbsp; Active sessions: %d</p>
                <table><thead><tr><th>Account</th><th>Last login</th><th>Status</th><th>Action</th></tr></thead>
                <tbody>%s</tbody></table><script>
                async function terminate(name){if(!confirm('Terminate all sessions for '+name+'?'))return;
                const r=await fetch('/api/sessions/terminate?username='+encodeURIComponent(name),
                {method:'POST',headers:{'X-mAuth-Confirm':'true'}});if(r.ok)location.reload();else alert('Request failed');}
                </script></body></html>
                """.formatted(dashboard.accountCount, dashboard.sessionCount, rows);
    }

    private boolean authorized(HttpExchange exchange) throws IOException {
        String supplied = exchange.getRequestHeaders().getFirst("Authorization");
        boolean valid = supplied != null && MessageDigest.isEqual(authorization,
                supplied.getBytes(StandardCharsets.US_ASCII));
        if (valid) return true;
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"mAuth\"");
        respond(exchange, 401, "text/plain", "authentication required");
        return false;
    }

    private String query(HttpExchange exchange, String key) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) return null;
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && URLDecoder.decode(parts[0], StandardCharsets.UTF_8).equals(key)) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.sendResponseHeaders(status, data.length);
        try (var output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    @Override
    public void close() {
        if (server != null) server.stop(1);
        if (executor != null) executor.close();
    }

    private record Dashboard(int accountCount, int sessionCount, List<AccountRow> accounts) {
    }

    private record AccountRow(String username, String registeredName, Long lastLogin, Long lockedUntil) {
    }
}
