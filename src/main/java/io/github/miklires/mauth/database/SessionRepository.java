package io.github.miklires.mauth.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

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
        String sql = """
            INSERT INTO mauth_sessions (username, ip, player_uuid, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_uuid = VALUES(player_uuid),
                created_at = VALUES(created_at),
                expires_at = VALUES(expires_at)
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, ip);
            ps.setString(3, playerUuid.toString());
            ps.setLong(4, now);
            ps.setLong(5, expires);
            ps.executeUpdate();
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

    public int purgeExpired() throws SQLException {
        long now = System.currentTimeMillis() / 1000;
        String sql = "DELETE FROM mauth_sessions WHERE expires_at < ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, now);
            return ps.executeUpdate();
        }
    }
}
