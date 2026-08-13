package io.github.miklires.mauth.config;

import org.bukkit.configuration.file.FileConfiguration;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.DiscordMode;

import java.util.List;

public class ConfigManager {

    private final MAuth plugin;

    public ConfigManager(MAuth plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public String getStorageType() { return cfg().getString("storage.type", "h2").toLowerCase(); }
    public String getDbHost() { return cfg().getString("storage.host", "localhost"); }
    public int getDbPort() { return cfg().getInt("storage.port", 3306); }
    public String getDbName() { return cfg().getString("storage.name", "mauth"); }
    public String getDbUser() { return cfg().getString("storage.user", "mauth"); }
    public String getDbPassword() { return cfg().getString("storage.password", ""); }
    public int getDbPoolSize() { return cfg().getInt("storage.pool-size", 10); }
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
    public int getLinkCodeTtl() { return cfg().getInt("discord.link-code-ttl-seconds", 300); }
    public boolean isDiscordShowIp() { return cfg().getBoolean("discord.show-ip", false); }

    public int getBcryptCost() { return cfg().getInt("security.bcrypt-cost", 12); }
    public String getPasswordAlgorithm() { return cfg().getString("security.password-algorithm", "argon2id"); }
    public int getArgon2Memory() { return cfg().getInt("security.argon2-memory-kib", 65536); }
    public int getArgon2Iterations() { return cfg().getInt("security.argon2-iterations", 3); }
    public int getArgon2Parallelism() { return cfg().getInt("security.argon2-parallelism", 1); }
    public int getMinPasswordLength() { return cfg().getInt("security.min-password-length", 8); }
    public int getMaxPasswordLength() { return cfg().getInt("security.max-password-length", 64); }
    public int getMaxLoginAttempts() { return cfg().getInt("security.max-login-attempts", 5); }
    public int getLoginAttemptWindow() { return cfg().getInt("security.login-attempt-window-seconds", 300); }
    public int getLockoutSeconds() { return cfg().getInt("security.lockout-seconds", 600); }
    public int getSessionTtl() { return cfg().getInt("security.session-ttl-seconds", 3600); }
    public boolean isFreezeOnJoin() { return cfg().getBoolean("security.freeze-on-join", true); }
    public int getAuthTimeout() { return cfg().getInt("security.auth-timeout-seconds", 60); }
    public int getMaxAccountsPerIp() { return Math.max(0, cfg().getInt("security.max-accounts-per-ip", 3)); }
    public boolean isUsernameCaseEnforced() { return cfg().getBoolean("security.enforce-username-case", true); }
    public String getNewIpPolicy() { return policy("security.auth-policy.new-ip", "captcha", "allow", "captcha", "deny"); }
    public String getNewDevicePolicy() { return policy("security.auth-policy.new-device", "notify", "allow", "notify", "deny"); }

    public boolean isWhitelistEnabled() { return cfg().getBoolean("whitelist.enabled", false); }
    public String getKickMessage() { return cfg().getString("whitelist.kick-message", "Not whitelisted"); }

    public String getLimboWorldName() { return cfg().getString("limbo.world-name", "limbo"); }

    public String getCaptchaMode() { return cfg().getString("captcha.mode", "auto").toLowerCase(); }
    public int getCaptchaLength() { return cfg().getInt("captcha.length", 6); }
    public int getCaptchaMaxAttempts() { return cfg().getInt("captcha.max-attempts", 3); }
    public int getFloodThreshold() { return cfg().getInt("captcha.flood-threshold", 10); }
    public int getFloodWindowSeconds() { return cfg().getInt("captcha.flood-window-seconds", 60); }
    public int getFloodCooldownSeconds() { return cfg().getInt("captcha.flood-cooldown-seconds", 300); }
    public boolean isAttackWhitelistMode() { return cfg().getBoolean("captcha.attack-whitelist-mode", true); }
    public List<String> getBlockedUsernamePatterns() {
        return cfg().getStringList("captcha.blocked-username-patterns");
    }

    public boolean isAuditEnabled() { return cfg().getBoolean("audit.enabled", true); }
    public int getAuditRetentionDays() { return cfg().getInt("audit.retention-days", 90); }

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
    public int getVpnTimeoutMillis() { return cfg().getInt("vpn.http.timeout-millis", 3000); }
    public int getVpnCacheSeconds() { return Math.max(30, cfg().getInt("vpn.cache-seconds", 3600)); }
    public boolean isVpnBlocking() { return cfg().getBoolean("vpn.block", true); }
    public boolean isVpnFailClosed() { return cfg().getBoolean("vpn.fail-closed", false); }
    public boolean isWebEnabled() { return cfg().getBoolean("web.enabled", false); }
    public String getWebBind() { return cfg().getString("web.bind", "127.0.0.1"); }
    public int getWebPort() { return cfg().getInt("web.port", 8765); }
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
    public int getBstatsId() { return Math.max(0, cfg().getInt("metrics.bstats-id", 0)); }
    public boolean isUpdateCheckEnabled() { return cfg().getBoolean("updates.enabled", true); }
    public String getModrinthProjectId() { return cfg().getString("updates.modrinth-project-id", "").trim(); }

    private String policy(String path, String fallback, String... values) {
        String value = cfg().getString(path, fallback).toLowerCase();
        for (String allowed : values) if (value.equals(allowed)) return value;
        plugin.getLogger().warning("invalid policy " + path + ": " + value);
        return fallback;
    }
}
