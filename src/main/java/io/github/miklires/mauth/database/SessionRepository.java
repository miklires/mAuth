package io.github.miklires.mauth.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class SessionRepository {

    private final DatabaseManager db;

    public SessionRepository(DatabaseManager db) {
        this.db = db;
    }

    public boolean hasValid(String username, String ip, UUID playerUuid) throws SQLException {
        String sql = """
            SELECT player_uuid, expires_at FROM mauth_sessions
            WHERE username = ? AND ip = ?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String storedUuid = rs.getString("player_uuid");
                long expiresAt = rs.getLong("expires_at");
                long now = System.currentTimeMillis() / 1000;
                if (expiresAt < now) return false;
                if (!storedUuid.equals(playerUuid.toString())) return false;
                return true;
            }
        }
    }

    public void save(String username, String ip, UUID playerUuid, int ttlSeconds) throws SQLException {
        long now = System.currentTimeMillis() / 1000;
        long expires = now + ttlSeconds;
        String key = username.toLowerCase();
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            int changed;
            String update = "UPDATE mauth_sessions SET player_uuid = ?, created_at = ?, expires_at = ? "
                    + "WHERE username = ? AND ip = ?";
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setString(1, playerUuid.toString());
                ps.setLong(2, now);
                ps.setLong(3, expires);
                ps.setString(4, key);
                ps.setString(5, ip);
                changed = ps.executeUpdate();
            }
            if (changed == 0) {
                String insert = "INSERT INTO mauth_sessions "
                        + "(username, ip, player_uuid, created_at, expires_at) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(insert)) {
                    ps.setString(1, key);
                    ps.setString(2, ip);
                    ps.setString(3, playerUuid.toString());
                    ps.setLong(4, now);
                    ps.setLong(5, expires);
                    ps.executeUpdate();
                }
            }
            c.commit();
        }
    }

    public void invalidate(String username) throws SQLException {
        String sql = "DELETE FROM mauth_sessions WHERE username = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.executeUpdate();
        }
    }

    public boolean delete(String username, String ip) throws SQLException {
        String sql = "DELETE FROM mauth_sessions WHERE username = ? AND ip = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, ip);
            return ps.executeUpdate() > 0;
        }
    }

    public List<SessionRecord> list(String username, int limit) throws SQLException {
        String sql = "SELECT ip, player_uuid, created_at, expires_at FROM mauth_sessions "
                + "WHERE username = ? AND expires_at >= ? ORDER BY created_at DESC LIMIT ?";
        List<SessionRecord> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setLong(2, System.currentTimeMillis() / 1_000L);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new SessionRecord(
                            rs.getString("ip"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getLong("created_at"),
                            rs.getLong("expires_at")));
                }
            }
        }
        return result;
    }

    public int purgeExpired() throws SQLException {
        long now = System.currentTimeMillis() / 1000;
        String sql = "DELETE FROM mauth_sessions WHERE expires_at < ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, now);
            return ps.executeUpdate();
        }
    }

    public record SessionRecord(String ip, UUID playerUuid, long createdAt, long expiresAt) {
    }
}
