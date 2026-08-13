package io.github.miklires.mauth.model;

import java.time.Instant;
import java.util.UUID;

public class Account {

    private final String username;
    private String registeredName;
    private String passwordHash;
    private UUID premiumUuid;
    private String bedrockXuid;
    private String discordId;
    private boolean whitelisted;
    private boolean premiumEnabled;
    private Instant registeredAt;
    private Instant lastLoginAt;
    private String lastIp;
    private String lastLocation;
    private Instant lastPasswordResetAt;
    private String totpSecret;
    private String recoveryCodes;
    private Instant lockedUntil;
    private String email;
    private boolean emailVerified;
    private String telegramId;

    public Account(String username, String passwordHash) {
        this.username = username.toLowerCase();
        this.registeredName = username;
        this.passwordHash = passwordHash;
        this.registeredAt = Instant.now();
    }

    public Account(String username, String registeredName, String passwordHash, UUID premiumUuid,
                   String bedrockXuid, String discordId,
                   boolean whitelisted, boolean premiumEnabled, Instant registeredAt,
                   Instant lastLoginAt, String lastIp, String lastLocation,
                   String totpSecret, String recoveryCodes, Instant lockedUntil,
                   String email, boolean emailVerified, String telegramId) {
        this.username = username.toLowerCase();
        this.registeredName = registeredName;
        this.passwordHash = passwordHash;
        this.premiumUuid = premiumUuid;
        this.bedrockXuid = bedrockXuid;
        this.discordId = discordId;
        this.whitelisted = whitelisted;
        this.premiumEnabled = premiumEnabled;
        this.registeredAt = registeredAt;
        this.lastLoginAt = lastLoginAt;
        this.lastIp = lastIp;
        this.lastLocation = lastLocation;
        this.totpSecret = totpSecret;
        this.recoveryCodes = recoveryCodes;
        this.lockedUntil = lockedUntil;
        this.email = email;
        this.emailVerified = emailVerified;
        this.telegramId = telegramId;
    }

    public String getUsername() { return username; }
    public String getRegisteredName() { return registeredName; }
    public void setRegisteredName(String registeredName) { this.registeredName = registeredName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public UUID getPremiumUuid() { return premiumUuid; }
    public void setPremiumUuid(UUID premiumUuid) { this.premiumUuid = premiumUuid; }
    public String getBedrockXuid() { return bedrockXuid; }
    public void setBedrockXuid(String bedrockXuid) { this.bedrockXuid = bedrockXuid; }
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
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public String getRecoveryCodes() { return recoveryCodes; }
    public void setRecoveryCodes(String recoveryCodes) { this.recoveryCodes = recoveryCodes; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public String getTelegramId() { return telegramId; }
    public void setTelegramId(String telegramId) { this.telegramId = telegramId; }
    public boolean isTemporarilyLocked() { return lockedUntil != null && lockedUntil.isAfter(Instant.now()); }

    public boolean hasDiscordLinked() { return discordId != null && !discordId.isEmpty(); }
    public boolean hasTotp() { return totpSecret != null && !totpSecret.isEmpty(); }
    public boolean hasTelegramLinked() { return telegramId != null && !telegramId.isEmpty(); }
}
