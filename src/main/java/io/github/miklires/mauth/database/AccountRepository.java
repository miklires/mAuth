package io.github.miklires.mauth.database;

import io.github.miklires.mauth.model.Account;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AccountRepository {

    private final DatabaseManager db;

    public AccountRepository(DatabaseManager db) {
        this.db = db;
    }

    public Optional<Account> findByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM mauth_accounts WHERE username = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<Account> findByDiscordId(String discordId) throws SQLException {
        String sql = "SELECT * FROM mauth_accounts WHERE discord_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public Optional<Account> findByPremiumUuid(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM mauth_accounts WHERE premium_uuid = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public void insert(Account a) throws SQLException {
        String sql = "INSERT INTO mauth_accounts (username, password_hash, registered_at, whitelisted) VALUES (?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.getUsername());
            ps.setString(2, a.getPasswordHash());
            ps.setLong(3, a.getRegisteredAt().getEpochSecond());
            ps.setBoolean(4, a.isWhitelisted());
            ps.executeUpdate();
        }
    }

    public void update(Account a) throws SQLException {
        String sql = """
            UPDATE mauth_accounts SET
                password_hash = ?,
                premium_uuid = ?,
                discord_id = ?,
                whitelisted = ?,
                premium_enabled = ?,
                last_login_at = ?,
                last_ip = ?,
                last_location = ?,
                last_password_reset_at = ?
            WHERE username = ?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.getPasswordHash());
            ps.setString(2, a.getPremiumUuid() != null ? a.getPremiumUuid().toString() : null);
            ps.setString(3, a.getDiscordId());
            ps.setBoolean(4, a.isWhitelisted());
            ps.setBoolean(5, a.isPremiumEnabled());
            if (a.getLastLoginAt() != null) ps.setLong(6, a.getLastLoginAt().getEpochSecond());
            else ps.setNull(6, java.sql.Types.BIGINT);
            ps.setString(7, a.getLastIp());
            ps.setString(8, a.getLastLocation());
            if (a.getLastPasswordResetAt() != null) ps.setLong(9, a.getLastPasswordResetAt().getEpochSecond());
            else ps.setNull(9, java.sql.Types.BIGINT);
            ps.setString(10, a.getUsername());
            ps.executeUpdate();
        }
    }

    public boolean delete(String username) throws SQLException {
        String sql = "DELETE FROM mauth_accounts WHERE username = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            return ps.executeUpdate() > 0;
        }
    }

    public List<String> listWhitelisted() throws SQLException {
        List<String> result = new ArrayList<>();
        String sql = "SELECT username FROM mauth_accounts WHERE whitelisted = TRUE ORDER BY username";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(rs.getString(1));
        }
        return result;
    }

    private Account map(ResultSet rs) throws SQLException {
        String premiumUuidStr = rs.getString("premium_uuid");
        long lastLogin = rs.getLong("last_login_at");
        boolean lastLoginWasNull = rs.wasNull();
        long lastReset = rs.getLong("last_password_reset_at");
        boolean lastResetWasNull = rs.wasNull();
        Account a = new Account(
                rs.getString("username"),
                rs.getString("password_hash"),
                premiumUuidStr != null ? UUID.fromString(premiumUuidStr) : null,
                rs.getString("discord_id"),
                rs.getBoolean("whitelisted"),
                rs.getBoolean("premium_enabled"),
                Instant.ofEpochSecond(rs.getLong("registered_at")),
                lastLoginWasNull ? null : Instant.ofEpochSecond(lastLogin),
                rs.getString("last_ip"),
                rs.getString("last_location")
        );
        if (!lastResetWasNull) {
            a.setLastPasswordResetAt(Instant.ofEpochSecond(lastReset));
        }
        return a;
    }
}
