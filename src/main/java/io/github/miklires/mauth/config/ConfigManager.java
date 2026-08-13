package io.github.miklires.mauth.config;

import org.bukkit.configuration.file.FileConfiguration;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.DiscordMode;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigManager {

    private static final int CONFIG_VERSION = 2;
    private final MAuth plugin;

    public ConfigManager(MAuth plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void load() {
        plugin.saveDefaultConfig();
        int version = cfg().getInt("config-version", 0);
        if (version < CONFIG_VERSION) {
            cfg().options().copyDefaults(true);
            cfg().set("config-version", CONFIG_VERSION);
            plugin.saveConfig();
            if (version > 0) plugin.getLogger().info("config updated to version " + CONFIG_VERSION);
        } else if (version > CONFIG_VERSION) {
            plugin.getLogger().warning("config is from a newer mAuth version");
        }
    }

    public void reload() {
        plugin.reloadConfig();
        load();
    }

    public String getStorageType() { return cfg().getString("storage.type", "h2").toLowerCase(); }
    public String getDbFile() { return cfg().getString("storage.file", "mauth").trim(); }
    public String getJdbcUrl() { return cfg().getString("storage.jdbc-url", "").trim(); }
    public String getDbHost() { return cfg().getString("storage.host", "localhost"); }
    public int getDbPort() { return range("storage.port", 3306, 1, 65_535); }
    public String getDbName() { return cfg().getString("storage.name", "mauth"); }
    public String getDbUser() { return cfg().getString("storage.user", "mauth"); }
    public String getDbPassword() { return cfg().getString("storage.password", ""); }
    public int getDbPoolSize() { return range("storage.pool-size", 10, 1, 64); }
    public long getDbConnectionTimeout() { return range("storage.connection-timeout-millis", 10_000, 250, 120_000); }
    public long getDbMaxLifetime() { return range("storage.max-lifetime-millis", 1_800_000, 30_000, 7_200_000); }
    public String getDefaultLanguage() { return cfg().getString("language.default", "en_US"); }
    public boolean isPerPlayerLanguage() { return cfg().getBoolean("language.per-player", true); }

    public DiscordMode getDiscordMode() {
        return DiscordMode.parse(cfg().getString("discord.mode", "disabled"));
    }
    public boolean isDiscordEnabled() { return getDiscordMode() != DiscordMode.DISABLED; }
    public long getDiscordRequiredAfter() { return cfg().getLong("discord.required-for-new-after", 0); }
    public String getDiscordToken() { return cfg().getString("discord.token", ""); }
    public String getDiscordBotName() { return cfg().getString("discord.bot-name", "Bot#0000"); }
    public String getDiscordInviteLink() { return cfg().getString("discord.invite-link", ""); }
    public String getDiscordServerName() { return cfg().getString("discord.server-name", "Server"); }
    public String getDiscordGuildId() { return cfg().getString("discord.guild-id", ""); }
    public String getDiscordPlayerRoleId() { return cfg().getString("discord.player-role-id", ""); }
    public int getLinkCodeTtl() { return range("discord.link-code-ttl-seconds", 300, 30, 3600); }
    public boolean isDiscordShowIp() { return cfg().getBoolean("discord.show-ip", false); }

    public int getBcryptCost() { return range("security.bcrypt-cost", 12, 4, 16); }
    public String getPasswordAlgorithm() { return cfg().getString("security.password-algorithm", "argon2id"); }
    public int getArgon2Memory() { return range("security.argon2-memory-kib", 65_536, 8_192, 262_144); }
    public int getArgon2Iterations() { return range("security.argon2-iterations", 3, 1, 10); }
    public int getArgon2Parallelism() { return range("security.argon2-parallelism", 1, 1, 16); }
    public int getMinPasswordLength() { return range("security.min-password-length", 8, 4, 128); }
    public int getMaxPasswordLength() { return Math.max(getMinPasswordLength(), range("security.max-password-length", 64, 4, 512)); }
    public int getMaxLoginAttempts() { return range("security.max-login-attempts", 5, 1, 100); }
    public int getLoginAttemptWindow() { return range("security.login-attempt-window-seconds", 300, 1, 86_400); }
    public int getLockoutSeconds() { return range("security.lockout-seconds", 600, 1, 604_800); }
    public int getSessionTtl() { return range("security.session-ttl-seconds", 3600, 0, 2_592_000); }
    public boolean isFreezeOnJoin() { return cfg().getBoolean("security.freeze-on-join", true); }
    public int getAuthTimeout() { return range("security.auth-timeout-seconds", 60, 0, 3_600); }
    public int getMaxAccountsPerIp() { return Math.max(0, cfg().getInt("security.max-accounts-per-ip", 3)); }
    public boolean isUsernameCaseEnforced() { return cfg().getBoolean("security.enforce-username-case", true); }
    public String getNewIpPolicy() { return policy("security.auth-policy.new-ip", "captcha", "allow", "captcha", "deny"); }
    public String getNewDevicePolicy() { return policy("security.auth-policy.new-device", "notify", "allow", "notify", "deny"); }
    public Set<String> getAllowedCommands() {
        return cfg().getStringList("security.allowed-commands").stream()
                .map(s -> s.toLowerCase(Locale.ROOT).replaceFirst("^/", ""))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isWhitelistEnabled() { return cfg().getBoolean("whitelist.enabled", false); }
    public String getKickMessage() { return cfg().getString("whitelist.kick-message", "Not whitelisted"); }

    public boolean isLimboEnabled() { return cfg().getBoolean("limbo.enabled", true); }
    public boolean isLimboInventoryHidden() { return cfg().getBoolean("limbo.hide-inventory", true); }
    public String getLimboWorldName() { return cfg().getString("limbo.world-name", "limbo"); }
    public double getLimboX() { return cfg().getDouble("limbo.spawn.x", 0.5); }
    public double getLimboY() { return cfg().getDouble("limbo.spawn.y", 100.0); }
    public double getLimboZ() { return cfg().getDouble("limbo.spawn.z", 0.5); }
    public float getLimboYaw() { return (float) cfg().getDouble("limbo.spawn.yaw", 0); }
    public float getLimboPitch() { return (float) cfg().getDouble("limbo.spawn.pitch", 0); }

    public String getCaptchaMode() { return cfg().getString("captcha.mode", "auto").toLowerCase(); }
    public int getCaptchaLength() { return range("captcha.length", 6, 4, 16); }
    public int getCaptchaMaxAttempts() { return range("captcha.max-attempts", 3, 1, 20); }
    public int getFloodThreshold() { return range("captcha.flood-threshold", 10, 2, 100_000); }
    public int getFloodWindowSeconds() { return range("captcha.flood-window-seconds", 60, 1, 3_600); }
    public int getFloodCooldownSeconds() { return range("captcha.flood-cooldown-seconds", 300, 1, 86_400); }
    public boolean isAttackWhitelistMode() { return cfg().getBoolean("captcha.attack-whitelist-mode", true); }
    public List<String> getBlockedUsernamePatterns() {
        return cfg().getStringList("captcha.blocked-username-patterns");
    }

    public boolean isAuditEnabled() { return cfg().getBoolean("audit.enabled", true); }
    public int getAuditRetentionDays() { return range("audit.retention-days", 90, 1, 3_650); }

    public boolean isGeoIpEnabled() { return cfg().getBoolean("geoip.enabled", true); }
    public String getGeoIpDatabase() { return cfg().getString("geoip.database", "GeoLite2-City.mmdb"); }
    public boolean isGeoIpDownloadEnabled() { return cfg().getBoolean("geoip.download-on-missing", false); }
    public String getGeoIpDownloadUrl() { return cfg().getString("geoip.download-url", ""); }
    public boolean isGeoIpNotifyDiscord() { return cfg().getBoolean("geoip.notify-discord", true); }
    public boolean isFloodgateEnabled() { return cfg().getBoolean("floodgate.enabled", true); }
    public boolean isFloodgateAutoRegister() { return cfg().getBoolean("floodgate.auto-register", true); }
    public String getProxySharedSecret() { return cfg().getString("proxy.shared-secret", ""); }
    public String getTotpIssuer() { return cfg().getString("security.totp-issuer", "mAuth"); }
    public String getVpnProvider() { return cfg().getString("vpn.provider", "none").toLowerCase(); }
    public String getVpnUrl() { return cfg().getString("vpn.http.url", ""); }
    public String getVpnApiKeyHeader() { return cfg().getString("vpn.http.api-key-header", "Authorization"); }
    public String getVpnApiKey() { return cfg().getString("vpn.http.api-key", ""); }
    public String getVpnBooleanField() { return cfg().getString("vpn.http.boolean-field", "proxy"); }
    public int getVpnTimeoutMillis() { return range("vpn.http.timeout-millis", 3_000, 250, 60_000); }
    public int getVpnCacheSeconds() { return Math.max(30, cfg().getInt("vpn.cache-seconds", 3600)); }
    public boolean isVpnBlocking() { return cfg().getBoolean("vpn.block", true); }
    public boolean isVpnFailClosed() { return cfg().getBoolean("vpn.fail-closed", false); }
    public boolean isWebEnabled() { return cfg().getBoolean("web.enabled", false); }
    public String getWebBind() { return cfg().getString("web.bind", "127.0.0.1"); }
    public int getWebPort() { return range("web.port", 8_765, 1, 65_535); }
    public boolean isEmailEnabled() { return cfg().getBoolean("email.enabled", false); }
    public String getEmailHost() { return cfg().getString("email.smtp.host", "localhost"); }
    public int getEmailPort() { return cfg().getInt("email.smtp.port", 587); }
    public String getEmailUsername() { return cfg().getString("email.smtp.username", ""); }
    public String getEmailPassword() { return cfg().getString("email.smtp.password", ""); }
    public boolean isEmailStartTls() { return cfg().getBoolean("email.smtp.starttls", true); }
    public boolean isEmailSsl() { return cfg().getBoolean("email.smtp.ssl", false); }
    public int getEmailTimeoutMillis() { return Math.max(1000, cfg().getInt("email.smtp.timeout-millis", 5000)); }
    public String getEmailEhlo() { return cfg().getString("email.smtp.ehlo", "localhost"); }
    public String getEmailFromAddress() { return cfg().getString("email.from-address", "mauth@localhost"); }
    public String getEmailFromName() { return cfg().getString("email.from-name", "mAuth"); }
    public int getEmailCodeTtlSeconds() { return Math.max(60, cfg().getInt("email.code-ttl-seconds", 600)); }
    public int getEmailRateLimitSeconds() { return Math.max(30, cfg().getInt("email.rate-limit-seconds", 300)); }
    public String getEmailVerificationSubject() { return cfg().getString("email.verification-subject", "mAuth email verification"); }
    public String getEmailVerificationBody() { return cfg().getString("email.verification-body", "Verification code: <code>"); }
    public String getEmailRecoverySubject() { return cfg().getString("email.recovery-subject", "mAuth password recovery"); }
    public String getEmailRecoveryBody() { return cfg().getString("email.recovery-body", "Recovery code for <player>: <code>"); }
    public boolean isTelegramEnabled() { return cfg().getBoolean("telegram.enabled", false); }
    public String getTelegramToken() { return cfg().getString("telegram.token", ""); }
    public int getTelegramPollTimeout() { return Math.max(5, Math.min(50, cfg().getInt("telegram.poll-timeout-seconds", 25))); }
    public boolean isMetricsEnabled() { return cfg().getBoolean("metrics.enabled", true); }
    public int getBstatsId() { return Math.max(0, cfg().getInt("metrics.bstats-id", 0)); }
    public boolean isUpdateCheckEnabled() { return cfg().getBoolean("updates.enabled", true); }
    public String getModrinthProjectId() { return cfg().getString("updates.modrinth-project-id", "").trim(); }
    public int getUpdateIntervalHours() { return range("updates.interval-hours", 24, 1, 720); }
    public int getUpdateTimeoutMillis() { return range("updates.timeout-millis", 8000, 500, 60_000); }

    private String policy(String path, String fallback, String... values) {
        String value = cfg().getString(path, fallback).toLowerCase();
        for (String allowed : values) if (value.equals(allowed)) return value;
        plugin.getLogger().warning("invalid policy " + path + ": " + value);
        return fallback;
    }

    private int range(String path, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, cfg().getInt(path, fallback)));
    }
}
