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

    public Optional<Account> findByTelegramId(String telegramId) throws SQLException {
        String sql = "SELECT * FROM mauth_accounts WHERE telegram_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, telegramId);
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

    public Optional<Account> findByBedrockXuid(String xuid) throws SQLException {
        String sql = "SELECT * FROM mauth_accounts WHERE bedrock_xuid = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, xuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(map(rs));
            }
        }
        return Optional.empty();
    }

    public void insert(Account a) throws SQLException {
        String sql = "INSERT INTO mauth_accounts "
                + "(username, registered_name, password_hash, bedrock_xuid, registered_at, whitelisted) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.getUsername());
            ps.setString(2, a.getRegisteredName());
            ps.setString(3, a.getPasswordHash());
            ps.setString(4, a.getBedrockXuid());
            ps.setLong(5, a.getRegisteredAt().getEpochSecond());
            ps.setBoolean(6, a.isWhitelisted());
            ps.executeUpdate();
        }
    }

    public void update(Account a) throws SQLException {
        String sql = """
            UPDATE mauth_accounts SET
                password_hash = ?,
                premium_uuid = ?,
                bedrock_xuid = ?,
                discord_id = ?,
                whitelisted = ?,
                premium_enabled = ?,
                last_login_at = ?,
                last_ip = ?,
                last_location = ?,
                last_password_reset_at = ?,
                registered_name = ?,
                totp_secret = ?,
                recovery_codes = ?,
                locked_until = ?,
                email = ?,
                email_verified = ?,
                telegram_id = ?
            WHERE username = ?
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.getPasswordHash());
            ps.setString(2, a.getPremiumUuid() != null ? a.getPremiumUuid().toString() : null);
            ps.setString(3, a.getBedrockXuid());
            ps.setString(4, a.getDiscordId());
            ps.setBoolean(5, a.isWhitelisted());
            ps.setBoolean(6, a.isPremiumEnabled());
            if (a.getLastLoginAt() != null) ps.setLong(7, a.getLastLoginAt().getEpochSecond());
            else ps.setNull(7, java.sql.Types.BIGINT);
            ps.setString(8, a.getLastIp());
            ps.setString(9, a.getLastLocation());
            if (a.getLastPasswordResetAt() != null) ps.setLong(10, a.getLastPasswordResetAt().getEpochSecond());
            else ps.setNull(10, java.sql.Types.BIGINT);
            ps.setString(11, a.getRegisteredName());
            ps.setString(12, a.getTotpSecret());
            ps.setString(13, a.getRecoveryCodes());
            if (a.getLockedUntil() != null) ps.setLong(14, a.getLockedUntil().getEpochSecond());
            else ps.setNull(14, java.sql.Types.BIGINT);
            ps.setString(15, a.getEmail());
            ps.setBoolean(16, a.isEmailVerified());
            ps.setString(17, a.getTelegramId());
            ps.setString(18, a.getUsername());
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
        long lockedUntil = rs.getLong("locked_until");
        boolean lockedUntilWasNull = rs.wasNull();
        Account a = new Account(
                rs.getString("username"),
                rs.getString("registered_name"),
                rs.getString("password_hash"),
                premiumUuidStr != null ? UUID.fromString(premiumUuidStr) : null,
                rs.getString("bedrock_xuid"),
                rs.getString("discord_id"),
                rs.getBoolean("whitelisted"),
                rs.getBoolean("premium_enabled"),
                Instant.ofEpochSecond(rs.getLong("registered_at")),
                lastLoginWasNull ? null : Instant.ofEpochSecond(lastLogin),
                rs.getString("last_ip"),
                rs.getString("last_location"),
                rs.getString("totp_secret"),
                rs.getString("recovery_codes"),
                lockedUntilWasNull ? null : Instant.ofEpochSecond(lockedUntil),
                rs.getString("email"),
                rs.getBoolean("email_verified"),
                rs.getString("telegram_id")
        );
        if (!lastResetWasNull) {
            a.setLastPasswordResetAt(Instant.ofEpochSecond(lastReset));
        }
        return a;
    }
}
