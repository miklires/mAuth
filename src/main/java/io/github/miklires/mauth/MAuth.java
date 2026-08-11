package io.github.miklires.mauth;

import org.bukkit.plugin.java.JavaPlugin;
import io.github.miklires.mauth.audit.AuditEvent;
import io.github.miklires.mauth.audit.AuditLogger;
import io.github.miklires.mauth.auth.AuthManager;
import io.github.miklires.mauth.auth.PasswordHasher;
import io.github.miklires.mauth.auth.PasswordResetService;
import io.github.miklires.mauth.auth.PasswordValidator;
import io.github.miklires.mauth.auth.SessionManager;
import io.github.miklires.mauth.captcha.CaptchaManager;
import io.github.miklires.mauth.command.CaptchaCommand;
import io.github.miklires.mauth.command.ChangePasswordCommand;
import io.github.miklires.mauth.command.LicenseCommand;
import io.github.miklires.mauth.command.LoginCommand;
import io.github.miklires.mauth.command.LogoutCommand;
import io.github.miklires.mauth.command.RegisterCommand;
import io.github.miklires.mauth.command.ResetPasswordCommand;
import io.github.miklires.mauth.command.UnlinkCommand;
import io.github.miklires.mauth.command.WhitelistCommand;
import io.github.miklires.mauth.config.ConfigManager;
import io.github.miklires.mauth.database.AccountRepository;
import io.github.miklires.mauth.database.DatabaseManager;
import io.github.miklires.mauth.database.KnownIpRepository;
import io.github.miklires.mauth.database.SessionRepository;
import io.github.miklires.mauth.geoip.GeoIpService;
import io.github.miklires.mauth.discord.DiscordBot;
import io.github.miklires.mauth.discord.LinkCodeManager;
import io.github.miklires.mauth.limbo.LimboWorldManager;
import io.github.miklires.mauth.listener.AuthRestrictionListener;
import io.github.miklires.mauth.listener.PlayerJoinListener;
import io.github.miklires.mauth.listener.PlayerQuitListener;
import io.github.miklires.mauth.util.MessageUtil;

public final class MAuth extends JavaPlugin {

    private ConfigManager configManager;
    private MessageUtil messageUtil;
    private DatabaseManager databaseManager;
    private AccountRepository accountRepository;
    private KnownIpRepository knownIpRepository;
    private SessionRepository sessionRepository;
    private PasswordHasher passwordHasher;
    private PasswordValidator passwordValidator;
    private SessionManager sessionManager;
    private AuthManager authManager;
    private PasswordResetService passwordResetService;
    private AuditLogger auditLogger;
    private GeoIpService geoIpService;
    private LinkCodeManager linkCodeManager;
    private DiscordBot discordBot;
    private LimboWorldManager limboWorldManager;
    private CaptchaManager captchaManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("passwords-blacklist.txt", false);

        configManager = new ConfigManager(this);
        messageUtil = new MessageUtil(this);

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initialize();
        } catch (Exception e) {
            getLogger().severe("database connection failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        accountRepository = new AccountRepository(databaseManager);
        knownIpRepository = new KnownIpRepository(databaseManager);
        sessionRepository = new SessionRepository(databaseManager);

        passwordHasher = new PasswordHasher(configManager.getBcryptCost());
        passwordValidator = new PasswordValidator(this);
        sessionManager = new SessionManager(this);
        authManager = new AuthManager(this);
        passwordResetService = new PasswordResetService(this);
        auditLogger = new AuditLogger(this);
        geoIpService = new GeoIpService(this);
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> auditLogger.purgeOld(),
                20L * 60L,
                20L * 60L * 60L * 24L);
        getServer().getScheduler().runTaskTimerAsynchronously(this,
                () -> {
                    try {
                        int removed = sessionRepository.purgeExpired();
                        if (removed > 0) {
                            getLogger().info("purged " + removed + " expired sessions");
                        }
                    } catch (java.sql.SQLException e) {
                        getLogger().warning("session purge failed: " + e.getMessage());
                    }
                },
                20L * 60L,
                20L * 60L * 60L);
        linkCodeManager = new LinkCodeManager(this);
        limboWorldManager = new LimboWorldManager(this);
        getServer().getScheduler().runTask(this, () -> limboWorldManager.initialize());
        captchaManager = new CaptchaManager(this);

        if (configManager.isDiscordEnabled()) {
            discordBot = new DiscordBot(this);
            try {
                discordBot.start();
            } catch (Exception e) {
                getLogger().severe("discord bot failed to start: " + e.getMessage());
            }
        }

        registerCommands();
        registerListeners();

        getLogger().info("mAuth enabled");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("mAuth disabled");
    }

    private void registerCommands() {
        getCommand("register").setExecutor(new RegisterCommand(this));
        getCommand("captcha").setExecutor(new CaptchaCommand(this));
        getCommand("login").setExecutor(new LoginCommand(this));
        getCommand("logout").setExecutor(new LogoutCommand(this));
        getCommand("changepassword").setExecutor(new ChangePasswordCommand(this));
        getCommand("license").setExecutor(new LicenseCommand(this));
        WhitelistCommand wl = new WhitelistCommand(this);
        getCommand("whitelist").setExecutor(wl);
        getCommand("whitelist").setTabCompleter(wl);
        getCommand("mauthreset").setExecutor(new ResetPasswordCommand(this));
        getCommand("mauthunlink").setExecutor(new UnlinkCommand(this));
        getCommand("mauthlog").setExecutor(new io.github.miklires.mauth.command.LogCommand(this));
        getCommand("mauth").setExecutor(new io.github.miklires.mauth.command.MAuthAdminCommand(this));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new AuthRestrictionListener(this), this);
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageUtil getMessageUtil() { return messageUtil; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public AccountRepository getAccountRepository() { return accountRepository; }
    public KnownIpRepository getKnownIpRepository() { return knownIpRepository; }
    public SessionRepository getSessionRepository() { return sessionRepository; }
    public PasswordHasher getPasswordHasher() { return passwordHasher; }
    public PasswordValidator getPasswordValidator() { return passwordValidator; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AuthManager getAuthManager() { return authManager; }
    public PasswordResetService getPasswordResetService() { return passwordResetService; }
    public AuditLogger getAuditLogger() { return auditLogger; }
    public GeoIpService getGeoIpService() { return geoIpService; }
    public LinkCodeManager getLinkCodeManager() { return linkCodeManager; }
    public DiscordBot getDiscordBot() { return discordBot; }
    public LimboWorldManager getLimboWorldManager() { return limboWorldManager; }
    public CaptchaManager getCaptchaManager() { return captchaManager; }
}
