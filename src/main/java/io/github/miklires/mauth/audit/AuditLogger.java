package io.github.miklires.mauth.audit;

import io.github.miklires.mauth.MAuth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AuditLogger {

    private final MAuth plugin;

    public AuditLogger(MAuth plugin) {
        this.plugin = plugin;
    }

    public void log(AuditEvent event, String username, String ip, String details) {
        if (!plugin.getConfigManager().isAuditEnabled()) return;
        plugin.getPluginScheduler().async(() ->
                writeNow(event, username, ip, details));
    }

    public void log(AuditEvent event, String username, String ip) {
        log(event, username, ip, null);
    }

    public void log(AuditEvent event, String username) {
        log(event, username, null, null);
    }

    private void writeNow(AuditEvent event, String username, String ip, String details) {
        String sql = "INSERT INTO mauth_audit_log (ts, username, event, ip, details) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis() / 1000);
            ps.setString(2, username != null ? username.toLowerCase() : null);
            ps.setString(3, event.name());
            ps.setString(4, ip);
            ps.setString(5, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("audit write failed: " + e.getMessage());
        }
    }

    public void purgeOld() {
        int days = plugin.getConfigManager().getAuditRetentionDays();
        if (days <= 0) return;
        long cutoff = System.currentTimeMillis() / 1000 - (long) days * 86400;
        String sql = "DELETE FROM mauth_audit_log WHERE ts < ?";
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, cutoff);
            int removed = ps.executeUpdate();
            if (removed > 0) {
                plugin.getLogger().info("audit: purged " + removed + " old entries");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("audit purge failed: " + e.getMessage());
        }
    }

    public List<LogEntry> getRecent(String username, int limit) throws SQLException {
        String sql = """
            SELECT ts, event, ip, details FROM mauth_audit_log
            WHERE username = ?
            ORDER BY ts DESC
            LIMIT ?
            """;
        List<LogEntry> result = new ArrayList<>();
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new LogEntry(
                            rs.getLong("ts"),
                            rs.getString("event"),
                            rs.getString("ip"),
                            rs.getString("details")
                    ));
                }
            }
        }
        return result;
    }

    public record LogEntry(long ts, String event, String ip, String details) {}
}
