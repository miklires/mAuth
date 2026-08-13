package io.github.miklires.mauth.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class KnownDeviceRepository {

    private final DatabaseManager db;

    public KnownDeviceRepository(DatabaseManager db) {
        this.db = db;
    }

    public boolean record(String username, UUID uuid) throws SQLException {
        String key = username.toLowerCase();
        String value = uuid.toString();
        long now = System.currentTimeMillis() / 1_000L;
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            boolean known;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM mauth_known_devices WHERE username = ? AND player_uuid = ?")) {
                ps.setString(1, key);
                ps.setString(2, value);
                try (ResultSet rs = ps.executeQuery()) {
                    known = rs.next();
                }
            }
            if (known) {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE mauth_known_devices SET last_seen = ? WHERE username = ? AND player_uuid = ?")) {
                    ps.setLong(1, now);
                    ps.setString(2, key);
                    ps.setString(3, value);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO mauth_known_devices (username, player_uuid, first_seen, last_seen) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, key);
                    ps.setString(2, value);
                    ps.setLong(3, now);
                    ps.setLong(4, now);
                    ps.executeUpdate();
                }
            }
            c.commit();
            return !known;
        }
    }

    public boolean isKnown(String username, UUID uuid) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM mauth_known_devices WHERE username = ? AND player_uuid = ?")) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public int count(String username) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM mauth_known_devices WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
