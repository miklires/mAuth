package io.github.miklires.mauth.config;

import org.bukkit.configuration.file.FileConfiguration;
import io.github.miklires.mauth.MAuth;

public class ConfigManager {

    private final MAuth plugin;

    public ConfigManager(MAuth plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public String getDbHost() { return cfg().getString("database.host", "localhost"); }
    public int getDbPort() { return cfg().getInt("database.port", 3306); }
    public String getDbName() { return cfg().getString("database.name", "mauth"); }
    public String getDbUser() { return cfg().getString("database.user", "mauth"); }
    public String getDbPassword() { return cfg().getString("database.password", ""); }
    public int getDbPoolSize() { return cfg().getInt("database.pool-size", 10); }

    public boolean isDiscordEnabled() { return cfg().getBoolean("discord.enabled", true); }
    public String getDiscordToken() { return cfg().getString("discord.token", ""); }
    public String getDiscordBotName() { return cfg().getString("discord.bot-name", "Bot#0000"); }
    public String getDiscordInviteLink() { return cfg().getString("discord.invite-link", ""); }
    public String getDiscordServerName() { return cfg().getString("discord.server-name", "Server"); }
    public String getDiscordGuildId() { return cfg().getString("discord.guild-id", ""); }
    public String getDiscordPlayerRoleId() { return cfg().getString("discord.player-role-id", ""); }
    public int getLinkCodeTtl() { return cfg().getInt("discord.link-code-ttl-seconds", 300); }

    public int getBcryptCost() { return cfg().getInt("security.bcrypt-cost", 12); }
    public int getMinPasswordLength() { return cfg().getInt("security.min-password-length", 8); }
    public int getMaxPasswordLength() { return cfg().getInt("security.max-password-length", 64); }
    public int getMaxLoginAttempts() { return cfg().getInt("security.max-login-attempts", 5); }
    public int getLoginAttemptWindow() { return cfg().getInt("security.login-attempt-window-seconds", 300); }
    public int getLockoutSeconds() { return cfg().getInt("security.lockout-seconds", 600); }
    public int getSessionTtl() { return cfg().getInt("security.session-ttl-seconds", 3600); }
    public boolean isFreezeOnJoin() { return cfg().getBoolean("security.freeze-on-join", true); }
    public int getAuthTimeout() { return cfg().getInt("security.auth-timeout-seconds", 60); }

    public boolean isWhitelistEnabled() { return cfg().getBoolean("whitelist.enabled", false); }
    public String getKickMessage() { return cfg().getString("whitelist.kick-message", "Not whitelisted"); }

    public String getLimboWorldName() { return cfg().getString("limbo.world-name", "limbo"); }

    public String getCaptchaMode() { return cfg().getString("captcha.mode", "auto").toLowerCase(); }
    public int getCaptchaLength() { return cfg().getInt("captcha.length", 6); }
    public int getCaptchaMaxAttempts() { return cfg().getInt("captcha.max-attempts", 3); }
    public int getFloodThreshold() { return cfg().getInt("captcha.flood-threshold", 10); }
    public int getFloodWindowSeconds() { return cfg().getInt("captcha.flood-window-seconds", 60); }
    public int getFloodCooldownSeconds() { return cfg().getInt("captcha.flood-cooldown-seconds", 300); }

    public boolean isAuditEnabled() { return cfg().getBoolean("audit.enabled", true); }
    public int getAuditRetentionDays() { return cfg().getInt("audit.retention-days", 90); }

    public boolean isGeoIpEnabled() { return cfg().getBoolean("geoip.enabled", true); }
    public String getGeoIpEndpoint() {
        return cfg().getString("geoip.endpoint",
                "http://ip-api.com/json/{ip}?fields=status,countryCode,country");
    }
    public int getGeoIpTimeoutSeconds() { return cfg().getInt("geoip.timeout-seconds", 3); }
    public boolean isGeoIpNotifyDiscord() { return cfg().getBoolean("geoip.notify-discord", true); }
}
