package io.github.miklires.mauth.importer;

import io.github.miklires.mauth.database.DatabaseManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Locale;

public class AccountImporter {

    private final DatabaseManager database;

    public AccountImporter(DatabaseManager database) {
        this.database = database;
    }

    public ImportResult run(ImportProfile profile, boolean dryRun) throws SQLException {
        try (Connection source = connect(profile);
             Statement statement = source.createStatement();
             ResultSet rows = statement.executeQuery(select(profile));
             Connection target = database.getConnection()) {
            target.setAutoCommit(false);
            ImportResult result;
            try {
                result = copy(rows, target, profile);
                if (dryRun) target.rollback();
                else target.commit();
            } catch (SQLException | RuntimeException e) {
                target.rollback();
                throw e;
            }
            return result;
        }
    }

    private Connection connect(ImportProfile profile) throws SQLException {
        if (profile.user().isBlank()) return DriverManager.getConnection(profile.jdbcUrl());
        return DriverManager.getConnection(profile.jdbcUrl(), profile.user(), profile.password());
    }

    private String select(ImportProfile profile) {
        return "SELECT " + profile.usernameColumn() + " AS mauth_username, "
                + expression(profile.realNameColumn()) + " AS mauth_real_name, "
                + profile.passwordColumn() + " AS mauth_password, "
                + expression(profile.ipColumn()) + " AS mauth_ip, "
                + expression(profile.registeredColumn()) + " AS mauth_registered "
                + "FROM " + profile.table();
    }

    private String expression(String column) {
        return column.isBlank() ? "NULL" : column;
    }

    private ImportResult copy(ResultSet rows, Connection target, ImportProfile profile) throws SQLException {
        int scanned = 0;
        int imported = 0;
        int skipped = 0;
        int invalid = 0;
        while (rows.next()) {
            scanned++;
            ImportedAccount account = map(rows, profile);
            if (account == null) {
                invalid++;
                continue;
            }
            if (exists(target, account.username)) {
                skipped++;
                continue;
            }
            insert(target, account);
            imported++;
        }
        return new ImportResult(scanned, imported, skipped, invalid);
    }

    private ImportedAccount map(ResultSet rows, ImportProfile profile) throws SQLException {
        String rawName = trim(rows.getString("mauth_username"));
        String realName = trim(rows.getString("mauth_real_name"));
        String hash = trim(rows.getString("mauth_password"));
        if (rawName == null || hash == null) return null;
        String registeredName = realName == null ? rawName : realName;
        if (!registeredName.matches("[A-Za-z0-9_]{3,16}")) return null;
        if (hash.length() > 255) return null;
        String ip = trim(rows.getString("mauth_ip"));
        if (ip != null && ip.length() > 45) ip = null;
        long registeredAt = timestamp(rows.getObject("mauth_registered"));
        return new ImportedAccount(registeredName.toLowerCase(Locale.ROOT), registeredName,
                profile.hashFormat().prepare(hash), ip, registeredAt);
    }

    private String trim(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private long timestamp(Object value) {
        if (value == null) return Instant.now().getEpochSecond();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().getEpochSecond();
        if (value instanceof java.util.Date date) return date.toInstant().getEpochSecond();
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw > 10_000_000_000L ? raw / 1_000L : raw;
        }
        try {
            long raw = Long.parseLong(value.toString());
            return raw > 10_000_000_000L ? raw / 1_000L : raw;
        } catch (NumberFormatException e) {
            return Instant.now().getEpochSecond();
        }
    }

    private boolean exists(Connection target, String username) throws SQLException {
        try (PreparedStatement ps = target.prepareStatement(
                "SELECT 1 FROM mauth_accounts WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void insert(Connection target, ImportedAccount account) throws SQLException {
        try (PreparedStatement ps = target.prepareStatement("""
                INSERT INTO mauth_accounts
                    (username, registered_name, password_hash, whitelisted, premium_enabled,
                     registered_at, last_ip)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, account.username);
            ps.setString(2, account.registeredName);
            ps.setString(3, account.passwordHash);
            ps.setBoolean(4, false);
            ps.setBoolean(5, false);
            ps.setLong(6, account.registeredAt);
            ps.setString(7, account.ip);
            ps.executeUpdate();
        }
        if (account.ip == null) return;
        try (PreparedStatement ps = target.prepareStatement("""
                INSERT INTO mauth_known_ips (username, ip, first_seen, last_seen)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setString(1, account.username);
            ps.setString(2, account.ip);
            ps.setLong(3, account.registeredAt);
            ps.setLong(4, account.registeredAt);
            ps.executeUpdate();
        }
    }

    public record ImportResult(int scanned, int imported, int skipped, int invalid) {
    }

    private record ImportedAccount(String username, String registeredName, String passwordHash,
                                   String ip, long registeredAt) {
    }
}
