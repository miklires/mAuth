package io.github.miklires.mauth;

import org.bukkit.plugin.java.JavaPlugin;
import io.github.miklires.mauth.audit.AuditEvent;
import io.github.miklires.mauth.api.MAuthApi;
import io.github.miklires.mauth.audit.AuditLogger;
import io.github.miklires.mauth.auth.AuthManager;
import io.github.miklires.mauth.auth.DiscordMode;
import io.github.miklires.mauth.auth.FloodgateBridge;
import io.github.miklires.mauth.auth.PasswordHasher;
import io.github.miklires.mauth.auth.PremiumProfileService;
import io.github.miklires.mauth.auth.PasswordResetService;
import io.github.miklires.mauth.auth.PasswordValidator;
import io.github.miklires.mauth.auth.SessionManager;
import io.github.miklires.mauth.auth.SecretProtector;
import io.github.miklires.mauth.auth.TotpService;
import io.github.miklires.mauth.captcha.CaptchaManager;
import io.github.miklires.mauth.command.CaptchaCommand;
import io.github.miklires.mauth.command.BrigadierCommands;
import io.github.miklires.mauth.command.ChangePasswordCommand;
import io.github.miklires.mauth.command.CrackedCommand;
import io.github.miklires.mauth.command.EmailCommand;
import io.github.miklires.mauth.command.LicenseCommand;
import io.github.miklires.mauth.command.IpHistoryCommand;
import io.github.miklires.mauth.command.LoginCommand;
import io.github.miklires.mauth.command.LogoutCommand;
import io.github.miklires.mauth.command.RegisterCommand;
import io.github.miklires.mauth.command.ResetPasswordCommand;
import io.github.miklires.mauth.command.UnlinkCommand;
import io.github.miklires.mauth.command.TotpCommand;
import io.github.miklires.mauth.command.TelegramCommand;
import io.github.miklires.mauth.command.WhitelistCommand;
import io.github.miklires.mauth.config.ConfigManager;
import io.github.miklires.mauth.database.AccountRepository;
import io.github.miklires.mauth.database.DatabaseManager;
import io.github.miklires.mauth.database.DiscordHistoryRepository;
import io.github.miklires.mauth.database.KnownIpRepository;
import io.github.miklires.mauth.database.KnownDeviceRepository;
import io.github.miklires.mauth.database.SessionRepository;
import io.github.miklires.mauth.geoip.GeoIpService;
import io.github.miklires.mauth.importer.AccountImporter;
import io.github.miklires.mauth.discord.LinkCodeManager;
import io.github.miklires.mauth.limbo.LimboWorldManager;
import io.github.miklires.mauth.limbo.PlayerStateStore;
import io.github.miklires.mauth.listener.AuthRestrictionListener;
import io.github.miklires.mauth.listener.PlayerJoinListener;
import io.github.miklires.mauth.listener.PlayerQuitListener;
import io.github.miklires.mauth.proxy.ProxyAuthMessenger;
import io.github.miklires.mauth.session.SessionGui;
import io.github.miklires.mauth.risk.IpRiskService;
import io.github.miklires.mauth.web.WebPanelServer;
import io.github.miklires.mauth.email.EmailRecoveryService;
import io.github.miklires.mauth.util.MessageUtil;
import io.github.miklires.mauth.util.PluginScheduler;
import io.github.miklires.mauth.update.UpdateChecker;
import io.github.miklires.mauth.placeholder.MAuthExpansion;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.ServicePriority;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MAuth extends JavaPlugin implements MAuthApi {

    private ConfigManager configManager;
    private MessageUtil messageUtil;
    private DatabaseManager databaseManager;
    private AccountRepository accountRepository;
    private KnownIpRepository knownIpRepository;
    private KnownDeviceRepository knownDeviceRepository;
    private SessionRepository sessionRepository;
    private DiscordHistoryRepository discordHistoryRepository;
    private PasswordHasher passwordHasher;
    private PasswordValidator passwordValidator;
    private PremiumProfileService premiumProfileService;
    private SessionManager sessionManager;
    private AuthManager authManager;
    private PasswordResetService passwordResetService;
    private AuditLogger auditLogger;
    private GeoIpService geoIpService;
    private FloodgateBridge floodgateBridge;
    private AccountImporter accountImporter;
    private TotpService totpService;
    private IpRiskService ipRiskService;
    private WebPanelServer webPanelServer;
    private EmailRecoveryService emailRecoveryService;
    private LinkCodeManager linkCodeManager;
    private LimboWorldManager limboWorldManager;
    private PlayerStateStore playerStateStore;
    private CaptchaManager captchaManager;
    private ExecutorService authExecutor;
    private PluginScheduler scheduler;
    private SessionGui sessionGui;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();
        scheduler = new PluginScheduler(this);
        if (configManager.getDiscordMode() == DiscordMode.REQUIRED_FOR_NEW
                && configManager.getDiscordRequiredAfter() <= 0) {
            getConfig().set("discord.required-for-new-after", System.currentTimeMillis() / 1000);
            saveConfig();
        }
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
        knownDeviceRepository = new KnownDeviceRepository(databaseManager);
        sessionRepository = new SessionRepository(databaseManager);
        discordHistoryRepository = new DiscordHistoryRepository(databaseManager);
        accountImporter = new AccountImporter(databaseManager);

        passwordHasher = new PasswordHasher(
                configManager.getPasswordAlgorithm(),
                configManager.getBcryptCost(),
                configManager.getArgon2Memory(),
                configManager.getArgon2Iterations(),
                configManager.getArgon2Parallelism());
        authExecutor = Executors.newFixedThreadPool(
                Math.max(2, Math.min(4, configManager.getDbPoolSize())),
                Thread.ofPlatform().name("mauth-auth-", 0).factory());
        passwordValidator = new PasswordValidator(this);
        totpService = new TotpService(new SecretProtector(this));
        premiumProfileService = new PremiumProfileService();
        sessionManager = new SessionManager(this);
        getServer().getServicesManager().register(MAuthApi.class, this, this, ServicePriority.Normal);
        authManager = new AuthManager(this);
        passwordResetService = new PasswordResetService(this);
        emailRecoveryService = new EmailRecoveryService(this);
        auditLogger = new AuditLogger(this);
        geoIpService = new GeoIpService(this);
        floodgateBridge = new FloodgateBridge(this);
        ipRiskService = new IpRiskService(this);
        scheduler.asyncTimer(() -> auditLogger.purgeOld(),
                1, 24, TimeUnit.HOURS);
        scheduler.asyncTimer(
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
                1, 1, TimeUnit.HOURS);
        linkCodeManager = new LinkCodeManager(this);
        playerStateStore = new PlayerStateStore(this);
        limboWorldManager = new LimboWorldManager(this);
        scheduler.global(() -> limboWorldManager.initialize());
        captchaManager = new CaptchaManager(this);
        webPanelServer = new WebPanelServer(this);
        webPanelServer.start();
        int bstatsId = configManager.getBstatsId();
        if (configManager.isMetricsEnabled() && bstatsId > 0) new org.bstats.bukkit.Metrics(this, bstatsId);
        UpdateChecker updates = new UpdateChecker(this);
        scheduler.async(updates::check);
        int updateHours = configManager.getUpdateIntervalHours();
        scheduler.asyncTimer(updates::check, updateHours, updateHours, TimeUnit.HOURS);

        sessionGui = new SessionGui(this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MAuthExpansion(this).register();
        }
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new BrigadierCommands(this).register(event.registrar(), sessionGui));
        registerListeners();

        getLogger().info("mAuth enabled");
    }

    @Override
    public void onDisable() {
        if (authExecutor != null) {
            authExecutor.shutdown();
            try {
                if (!authExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    authExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                authExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (geoIpService != null) geoIpService.close();
        getServer().getServicesManager().unregisterAll(this);
        if (webPanelServer != null) webPanelServer.close();
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("mAuth disabled");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new AuthRestrictionListener(this), this);
        getServer().getPluginManager().registerEvents(sessionGui, this);
        String proxySecret = configManager.getProxySharedSecret();
        if (!proxySecret.isBlank()) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, ProxyAuthMessenger.CHANNEL);
            getServer().getPluginManager().registerEvents(new ProxyAuthMessenger(this, proxySecret), this);
        }
    }

    public <T> void submit(Supplier<T> task, BiConsumer<T, Throwable> callback) {
        java.util.concurrent.CompletableFuture.supplyAsync(task, authExecutor)
                .whenComplete((result, error) -> scheduler.global(() -> callback.accept(result, error)));
    }

    public <T> void submit(org.bukkit.entity.Player player, Supplier<T> task,
                           BiConsumer<T, Throwable> callback) {
        java.util.concurrent.CompletableFuture.supplyAsync(task, authExecutor)
                .whenComplete((result, error) -> scheduler.player(player,
                        () -> callback.accept(result, error)));
    }

    public ConfigManager getConfigManager() { return configManager; }
    public MessageUtil getMessageUtil() { return messageUtil; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public AccountRepository getAccountRepository() { return accountRepository; }
    public KnownIpRepository getKnownIpRepository() { return knownIpRepository; }
    public KnownDeviceRepository getKnownDeviceRepository() { return knownDeviceRepository; }
    public SessionRepository getSessionRepository() { return sessionRepository; }
    public DiscordHistoryRepository getDiscordHistoryRepository() { return discordHistoryRepository; }
    public PasswordHasher getPasswordHasher() { return passwordHasher; }
    public PasswordValidator getPasswordValidator() { return passwordValidator; }
    public PremiumProfileService getPremiumProfileService() { return premiumProfileService; }
    public SessionManager getSessionManager() { return sessionManager; }
    public AuthManager getAuthManager() { return authManager; }
    public PasswordResetService getPasswordResetService() { return passwordResetService; }
    public AuditLogger getAuditLogger() { return auditLogger; }
    public GeoIpService getGeoIpService() { return geoIpService; }
    public FloodgateBridge getFloodgateBridge() { return floodgateBridge; }
    public AccountImporter getAccountImporter() { return accountImporter; }
    public TotpService getTotpService() { return totpService; }
    public IpRiskService getIpRiskService() { return ipRiskService; }
    public EmailRecoveryService getEmailRecoveryService() { return emailRecoveryService; }
    public LinkCodeManager getLinkCodeManager() { return linkCodeManager; }
    public LimboWorldManager getLimboWorldManager() { return limboWorldManager; }
    public PlayerStateStore getPlayerStateStore() { return playerStateStore; }
    public CaptchaManager getCaptchaManager() { return captchaManager; }
    public ExecutorService getAuthExecutor() { return authExecutor; }
    public PluginScheduler getPluginScheduler() { return scheduler; }

    @Override
    public boolean isAuthenticated(UUID playerUuid) {
        return sessionManager != null && sessionManager.isAuthenticated(playerUuid);
    }

    @Override
    public CompletableFuture<Boolean> isRegistered(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return accountRepository.findByUsername(username).isPresent();
            } catch (java.sql.SQLException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, authExecutor);
    }

    @Override
    public CompletableFuture<Void> invalidateSessions(String username) {
        return CompletableFuture.runAsync(() -> {
            try {
                sessionRepository.invalidate(username);
                sessionManager.clearAccount(username);
            } catch (java.sql.SQLException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, authExecutor);
    }
}
