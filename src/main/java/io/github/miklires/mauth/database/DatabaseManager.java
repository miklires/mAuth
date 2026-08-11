package io.github.miklires.mauth.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.config.ConfigManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private final MAuth plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(MAuth plugin) {
        this.plugin = plugin;
    }

    public void initialize() throws SQLException {
        ConfigManager cfg = plugin.getConfigManager();

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:mysql://" + cfg.getDbHost() + ":" + cfg.getDbPort() + "/"
                + cfg.getDbName() + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        hc.setUsername(cfg.getDbUser());
        hc.setPassword(cfg.getDbPassword());
        hc.setMaximumPoolSize(cfg.getDbPoolSize());
        hc.setPoolName("mAuth-Pool");
        hc.setConnectionTimeout(10_000);
        hc.setMaxLifetime(1_800_000);
        hc.setLeakDetectionThreshold(15_000);
        hc.addDataSourceProperty("cachePrepStmts", "true");
        hc.addDataSourceProperty("prepStmtCacheSize", "250");
        hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        dataSource = new HikariDataSource(hc);

        createSchema();
    }

    private void createSchema() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS mauth_accounts (
                username VARCHAR(16) PRIMARY KEY,
                password_hash VARCHAR(120) NOT NULL,
                premium_uuid VARCHAR(36) NULL UNIQUE,
                discord_id VARCHAR(32) NULL UNIQUE,
                whitelisted BOOLEAN NOT NULL DEFAULT FALSE,
                premium_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                registered_at BIGINT NOT NULL,
                last_login_at BIGINT NULL,
                last_ip VARCHAR(45) NULL,
                last_location VARCHAR(255) NULL,
                INDEX idx_discord (discord_id),
                INDEX idx_premium (premium_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            migrateLastLocationColumn(conn);
            createKnownIpsTable(conn);
            createAuditLogTable(conn);
            createSessionsTable(conn);
        }
    }

    private void createSessionsTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS mauth_sessions (
                username VARCHAR(16) NOT NULL,
                ip VARCHAR(45) NOT NULL,
                player_uuid VARCHAR(36) NOT NULL,
                created_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                PRIMARY KEY (username, ip),
                INDEX idx_expires (expires_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void createAuditLogTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS mauth_audit_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                ts BIGINT NOT NULL,
                username VARCHAR(16),
                event VARCHAR(32) NOT NULL,
                ip VARCHAR(45),
                details VARCHAR(255),
                INDEX idx_user_ts (username, ts),
                INDEX idx_event_ts (event, ts),
                INDEX idx_ts (ts)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void createKnownIpsTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS mauth_known_ips (
                username VARCHAR(16) NOT NULL,
                ip VARCHAR(45) NOT NULL,
                first_seen BIGINT NOT NULL,
                last_seen BIGINT NOT NULL,
                country_code VARCHAR(8) NULL,
                PRIMARY KEY (username, ip),
                INDEX idx_username (username)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """;
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void migrateLastLocationColumn(Connection conn) throws SQLException {
        addColumnIfMissing(conn, "last_location", "VARCHAR(255) NULL");
        addColumnIfMissing(conn, "last_password_reset_at", "BIGINT NULL");
    }

    private void addColumnIfMissing(Connection conn, String column, String definition) throws SQLException {
        String check = """
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'mauth_accounts'
              AND COLUMN_NAME = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(check)) {
            ps.setString(1, column);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("ALTER TABLE mauth_accounts ADD COLUMN "
                                + column + " " + definition);
                    }
                }
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
