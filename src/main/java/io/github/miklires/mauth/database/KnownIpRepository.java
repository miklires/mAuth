package io.github.miklires.mauth.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class KnownIpRepository {

    private final DatabaseManager db;

    public KnownIpRepository(DatabaseManager db) {
        this.db = db;
    }

    public boolean isKnown(String username, String ip) throws SQLException {
        String sql = "SELECT 1 FROM mauth_known_ips WHERE username = ? AND ip = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, ip);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void recordIp(String username, String ip) throws SQLException {
        long now = System.currentTimeMillis() / 1000;
        String sql = """
            INSERT INTO mauth_known_ips (username, ip, first_seen, last_seen)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE last_seen = ?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, ip);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    public Optional<String> getCountry(String username, String ip) throws SQLException {
        String sql = "SELECT country_code FROM mauth_known_ips WHERE username = ? AND ip = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String code = rs.getString(1);
                    return code != null ? Optional.of(code) : Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    public void setCountry(String username, String ip, String countryCode) throws SQLException {
        String sql = "UPDATE mauth_known_ips SET country_code = ? WHERE username = ? AND ip = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, countryCode);
            ps.setString(2, username.toLowerCase());
            ps.setString(3, ip);
            ps.executeUpdate();
        }
    }

    public List<String> getKnownCountries(String username) throws SQLException {
        List<String> countries = new ArrayList<>();
        String sql = "SELECT DISTINCT country_code FROM mauth_known_ips WHERE username = ? AND country_code IS NOT NULL";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) countries.add(rs.getString(1));
            }
        }
        return countries;
    }
}
