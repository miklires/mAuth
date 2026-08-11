package io.github.miklires.mauth.model;

import java.time.Instant;
import java.util.UUID;

public class Account {

    private final String username;
    private String passwordHash;
    private UUID premiumUuid;
    private String discordId;
    private boolean whitelisted;
    private boolean premiumEnabled;
    private Instant registeredAt;
    private Instant lastLoginAt;
    private String lastIp;
    private String lastLocation;
    private Instant lastPasswordResetAt;

    public Account(String username, String passwordHash) {
        this.username = username.toLowerCase();
        this.passwordHash = passwordHash;
        this.registeredAt = Instant.now();
    }

    public Account(String username, String passwordHash, UUID premiumUuid, String discordId,
                   boolean whitelisted, boolean premiumEnabled, Instant registeredAt,
                   Instant lastLoginAt, String lastIp, String lastLocation) {
        this.username = username.toLowerCase();
        this.passwordHash = passwordHash;
        this.premiumUuid = premiumUuid;
        this.discordId = discordId;
        this.whitelisted = whitelisted;
        this.premiumEnabled = premiumEnabled;
        this.registeredAt = registeredAt;
        this.lastLoginAt = lastLoginAt;
        this.lastIp = lastIp;
        this.lastLocation = lastLocation;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public UUID getPremiumUuid() { return premiumUuid; }
    public void setPremiumUuid(UUID premiumUuid) { this.premiumUuid = premiumUuid; }
    public String getDiscordId() { return discordId; }
    public void setDiscordId(String discordId) { this.discordId = discordId; }
    public boolean isWhitelisted() { return whitelisted; }
    public void setWhitelisted(boolean whitelisted) { this.whitelisted = whitelisted; }
    public boolean isPremiumEnabled() { return premiumEnabled; }
    public void setPremiumEnabled(boolean premiumEnabled) { this.premiumEnabled = premiumEnabled; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public String getLastIp() { return lastIp; }
    public void setLastIp(String lastIp) { this.lastIp = lastIp; }
    public String getLastLocation() { return lastLocation; }
    public void setLastLocation(String lastLocation) { this.lastLocation = lastLocation; }
    public Instant getLastPasswordResetAt() { return lastPasswordResetAt; }
    public void setLastPasswordResetAt(Instant lastPasswordResetAt) { this.lastPasswordResetAt = lastPasswordResetAt; }

    public boolean hasDiscordLinked() { return discordId != null && !discordId.isEmpty(); }
}
