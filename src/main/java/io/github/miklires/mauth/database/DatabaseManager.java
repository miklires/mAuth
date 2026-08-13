package io.github.miklires.mauth.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.config.ConfigManager;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

public class DatabaseManager {

    public enum StorageType {
        H2, SQLITE, MYSQL, MARIADB, POSTGRESQL;

        static StorageType parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown storage type: " + value);
            }
        }
    }

    private final MAuth plugin;
    private HikariDataSource dataSource;
    private StorageType type;

    public DatabaseManager(MAuth plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        ConfigManager cfg = plugin.getConfigManager();
        type = StorageType.parse(cfg.getStorageType());

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(jdbcUrl(cfg));
        hc.setDriverClassName(driver());
        if (type != StorageType.H2 && type != StorageType.SQLITE) {
            hc.setUsername(cfg.getDbUser());
            hc.setPassword(cfg.getDbPassword());
        }
        hc.setMaximumPoolSize(type == StorageType.SQLITE ? 1 : Math.max(2, cfg.getDbPoolSize()));
        hc.setPoolName("mAuth-" + type.name().toLowerCase(Locale.ROOT));
        hc.setConnectionTimeout(10_000);
        hc.setMaxLifetime(1_800_000);
        if (type == StorageType.SQLITE) hc.setConnectionTestQuery("SELECT 1");

        dataSource = new HikariDataSource(hc);
        migrate();
    }

    private String jdbcUrl(ConfigManager cfg) {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create plugin data folder");
        }
        String path = new File(dir, "mauth").getAbsolutePath().replace('\\', '/');
        return switch (type) {
            case H2 -> "jdbc:h2:file:" + path + ";AUTO_SERVER=TRUE";
            case SQLITE -> "jdbc:sqlite:" + path + ".db";
            case MYSQL -> "jdbc:mysql://" + cfg.getDbHost() + ":" + cfg.getDbPort() + "/"
                    + cfg.getDbName() + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
            case MARIADB -> "jdbc:mariadb://" + cfg.getDbHost() + ":" + cfg.getDbPort() + "/" + cfg.getDbName();
            case POSTGRESQL -> "jdbc:postgresql://" + cfg.getDbHost() + ":" + cfg.getDbPort() + "/" + cfg.getDbName();
        };
    }

    private String driver() {
        return switch (type) {
            case H2 -> "org.h2.Driver";
            case SQLITE -> "org.sqlite.JDBC";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
        };
    }

    private void migrate() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS mauth_schema_version (version INTEGER NOT NULL)");
        }

        int version = schemaVersion();
        if (version < 1) {
            createTables();
            setSchemaVersion(1);
        }
        if (version < 2) {
            try (Connection c = getConnection()) {
                addColumn(c, "mauth_accounts", "last_location", "VARCHAR(255)");
                addColumn(c, "mauth_accounts", "last_password_reset_at", "BIGINT");
            }
            setSchemaVersion(2);
        }
        if (version < 3) {
            try (Connection c = getConnection()) {
                addColumn(c, "mauth_accounts", "registered_name", "VARCHAR(16)");
            }
            setSchemaVersion(3);
        }
        if (version < 4) {
            try (Connection c = getConnection()) {
                addColumn(c, "mauth_accounts", "bedrock_xuid", "VARCHAR(32)");
            }
            setSchemaVersion(4);
        }
        if (version < 5) {
            try (Connection c = getConnection()) {
                addColumn(c, "mauth_accounts", "totp_secret", "VARCHAR(255)");
                addColumn(c, "mauth_accounts", "recovery_codes", "VARCHAR(1024)");
            }
            setSchemaVersion(5);
        }
        if (version < 6) {
            try (Connection c = getConnection()) {
                addColumn(c, "mauth_accounts", "locked_until", "BIGINT");
            }
            createDiscordHistoryTable();
            setSchemaVersion(6);
        }
        if (version < 7) {
            try (Connection c = getConnection()) {
                addColumn(c, "mauth_accounts", "email", "VARCHAR(512)");
                addColumn(c, "mauth_accounts", "email_verified", "BOOLEAN NOT NULL DEFAULT FALSE");
            }
            setSchemaVersion(7);
        }
        if (version < 8) {
            try (Connection c = getConnection()) {
                addColumn(c, "mauth_accounts", "telegram_id", "VARCHAR(32)");
            }
            setSchemaVersion(8);
        }
        if (version < 9) {
            createKnownDevicesTable();
            setSchemaVersion(9);
        }
    }

    private int schemaVersion() throws SQLException {
        try (Connection c = getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT version FROM mauth_schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private void setSchemaVersion(int version) throws SQLException {
        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.executeUpdate("DELETE FROM mauth_schema_version");
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mauth_schema_version (version) VALUES (?)")) {
                ps.setInt(1, version);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    private void createTables() throws SQLException {
        String accounts = """
                CREATE TABLE IF NOT EXISTS mauth_accounts (
                    username VARCHAR(16) PRIMARY KEY,
                    registered_name VARCHAR(16),
                    password_hash VARCHAR(255) NOT NULL,
                    premium_uuid VARCHAR(36) UNIQUE,
                    bedrock_xuid VARCHAR(32) UNIQUE,
                    discord_id VARCHAR(32) UNIQUE,
                    whitelisted BOOLEAN NOT NULL DEFAULT FALSE,
                    premium_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    registered_at BIGINT NOT NULL,
                    last_login_at BIGINT,
                    last_ip VARCHAR(45),
                    last_location VARCHAR(255),
                    last_password_reset_at BIGINT,
                    totp_secret VARCHAR(255),
                    recovery_codes VARCHAR(1024),
                    locked_until BIGINT,
                    email VARCHAR(512),
                    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
                    telegram_id VARCHAR(32) UNIQUE
                )
                """;
        String knownIps = """
                CREATE TABLE IF NOT EXISTS mauth_known_ips (
                    username VARCHAR(16) NOT NULL,
                    ip VARCHAR(45) NOT NULL,
                    first_seen BIGINT NOT NULL,
                    last_seen BIGINT NOT NULL,
                    country_code VARCHAR(8),
                    PRIMARY KEY (username, ip)
                )
                """;
        String audit = """
                CREATE TABLE IF NOT EXISTS mauth_audit_log (
                    ts BIGINT NOT NULL,
                    username VARCHAR(16),
                    event VARCHAR(32) NOT NULL,
                    ip VARCHAR(45),
                    details VARCHAR(255)
                )
                """;
        String sessions = """
                CREATE TABLE IF NOT EXISTS mauth_sessions (
                    username VARCHAR(16) NOT NULL,
                    ip VARCHAR(45) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    created_at BIGINT NOT NULL,
                    expires_at BIGINT NOT NULL,
                    PRIMARY KEY (username, ip)
                )
                """;
        String discordHistory = discordHistorySql();
        String knownDevices = knownDevicesSql();
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(accounts);
            st.executeUpdate(knownIps);
            st.executeUpdate(audit);
            st.executeUpdate(sessions);
            st.executeUpdate(discordHistory);
            st.executeUpdate(knownDevices);
        }
    }

    private void createDiscordHistoryTable() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(discordHistorySql());
        }
    }

    private String discordHistorySql() {
        return """
                CREATE TABLE IF NOT EXISTS mauth_discord_history (
                    username VARCHAR(16) NOT NULL,
                    discord_id VARCHAR(32) NOT NULL,
                    linked_at BIGINT NOT NULL,
                    unlinked_at BIGINT
                )
                """;
    }

    private void createKnownDevicesTable() throws SQLException {
        try (Connection c = getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(knownDevicesSql());
        }
    }

    private String knownDevicesSql() {
        return """
                CREATE TABLE IF NOT EXISTS mauth_known_devices (
                    username VARCHAR(16) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    first_seen BIGINT NOT NULL,
                    last_seen BIGINT NOT NULL,
                    PRIMARY KEY (username, player_uuid)
                )
                """;
    }

    private void addColumn(Connection c, String table, String column, String definition) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        if (hasColumn(md, table, column) || hasColumn(md, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            return;
        }
        try (Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean hasColumn(DatabaseMetaData md, String table, String column) throws SQLException {
        try (ResultSet rs = md.getColumns(null, null, table, column)) {
            return rs.next();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public StorageType getType() {
        return type;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }
}
