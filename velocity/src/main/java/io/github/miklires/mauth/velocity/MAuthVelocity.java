package io.github.miklires.mauth.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id = "mauth-velocity", name = "mAuth Velocity", version = "1.0.1",
        authors = {"miklires"})
public class MAuthVelocity {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("mauth:auth");

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final VelocityProfileService profiles = new VelocityProfileService();
    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> nonces = new ConcurrentHashMap<>();
    private VelocityConfig config;
    private CoreAccountService accounts;

    @Inject
    public MAuthVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        try {
            config = VelocityConfig.load(dataDirectory);
        } catch (Exception e) {
            throw new IllegalStateException("cannot load mAuth Velocity config", e);
        }
        proxy.getChannelRegistrar().register(CHANNEL);
        accounts = new CoreAccountService(config);
        Scheduler.TaskBuilder cleanup = proxy.getScheduler().buildTask(this, this::purgeNonces)
                .repeat(Duration.ofMinutes(1));
        cleanup.schedule();
        logger.info("mAuth Velocity enabled with login servers {}", config.loginServers());
    }

    @Subscribe
    public com.velocitypowered.api.event.EventTask onPreLogin(PreLoginEvent event) {
        return com.velocitypowered.api.event.EventTask.async(() -> {
            if (config.premiumMode() == VelocityConfig.PremiumMode.DISABLED) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                return;
            }
            VelocityProfileService.Lookup lookup = profiles.lookup(event.getUsername());
            switch (lookup.result()) {
                case PREMIUM -> applyPremiumPolicy(event, lookup);
                case CRACKED -> event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                case UNAVAILABLE -> {
                    if (config.allowCrackedOnLookupFailure()) {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    } else {
                        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                                Component.text("Mojang profile service is unavailable")));
                    }
                }
            }
        });
    }

    private void applyPremiumPolicy(PreLoginEvent event, VelocityProfileService.Lookup profile) {
        CoreAccountService.State account = accounts.lookup(event.getUsername());
        if (!account.available()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            return;
        }
        if (!account.exists()) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
            return;
        }
        if (account.premiumEnabled() && profile.uuid().equals(account.premiumUuid())) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
            return;
        }
        switch (config.conflictPolicy()) {
            case BLOCK -> event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
            case RENAME -> event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    Component.text("This premium name conflicts with a cracked account. Rename the cracked account first")));
            case TAKE_OVER -> event.setResult(PreLoginEvent.PreLoginComponentResult.forceOnlineMode());
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection backend)) return;
        String server = backend.getServerInfo().getName();
        if (!config.loginServers().contains(server)) return;
        Player player = backend.getPlayer();
        if (!verify(event.getData(), player.getUniqueId())) return;
        authenticated.add(player.getUniqueId());
        if (!config.targetServer().isEmpty()) {
            proxy.getServer(config.targetServer()).ifPresent(target -> {
                if (!target.equals(backend.getServer())) {
                    player.createConnectionRequest(target).fireAndForget();
                }
            });
        }
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent event) {
        String requested = event.getOriginalServer().getServerInfo().getName();
        if (authenticated.contains(event.getPlayer().getUniqueId())
                || config.loginServers().contains(requested)) {
            return;
        }
        config.loginServers().stream()
                .map(proxy::getServer)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .ifPresentOrElse(
                        server -> event.setResult(ServerPreConnectEvent.ServerResult.allowed(server)),
                        () -> event.setResult(ServerPreConnectEvent.ServerResult.denied()));
        event.getPlayer().sendMessage(Component.text("Authenticate before joining this server"));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        authenticated.remove(event.getPlayer().getUniqueId());
    }

    private boolean verify(byte[] data, UUID expectedUuid) {
        if (data.length > 512) return false;
        String payload = new String(data, StandardCharsets.UTF_8);
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 5 || !parts[0].equals("1") || !parts[1].equals(expectedUuid.toString())) {
            return false;
        }
        try {
            long timestamp = Long.parseLong(parts[2]);
            long now = System.currentTimeMillis() / 1_000L;
            if (Math.abs(now - timestamp) > 30L) return false;
            if (nonces.putIfAbsent(parts[3], now + 60L) != null) return false;
            String body = String.join("|", parts[0], parts[1], parts[2], parts[3]);
            byte[] expected = sign(body);
            byte[] supplied = Base64.getUrlDecoder().decode(parts[4]);
            return MessageDigest.isEqual(expected, supplied);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.sharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void purgeNonces() {
        long now = System.currentTimeMillis() / 1_000L;
        nonces.entrySet().removeIf(entry -> entry.getValue() < now);
    }
}
