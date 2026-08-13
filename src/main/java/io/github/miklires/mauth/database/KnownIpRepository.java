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
        String key = username.toLowerCase();
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            int changed;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE mauth_known_ips SET last_seen = ? WHERE username = ? AND ip = ?")) {
                ps.setLong(1, now);
                ps.setString(2, key);
                ps.setString(3, ip);
                changed = ps.executeUpdate();
            }
            if (changed == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO mauth_known_ips (username, ip, first_seen, last_seen) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, key);
                    ps.setString(2, ip);
                    ps.setLong(3, now);
                    ps.setLong(4, now);
                    ps.executeUpdate();
                }
            }
            c.commit();
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

    public List<KnownIp> list(String username, int limit) throws SQLException {
        String sql = "SELECT ip, first_seen, last_seen, country_code FROM mauth_known_ips "
                + "WHERE username = ? ORDER BY last_seen DESC LIMIT ?";
        List<KnownIp> result = new ArrayList<>();
        try (Connection c = db.getConnection();
            PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new KnownIp(
                            rs.getString("ip"),
                            rs.getLong("first_seen"),
                            rs.getLong("last_seen"),
                            rs.getString("country_code")));
                }
            }
        }
        return result;
    }

    public int countAccounts(String ip) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT username) FROM mauth_known_ips WHERE ip = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countForAccount(String username) throws SQLException {
        try (Connection c = db.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM mauth_known_ips WHERE username = ?")) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public record KnownIp(String ip, long firstSeen, long lastSeen, String countryCode) {
    }
}
