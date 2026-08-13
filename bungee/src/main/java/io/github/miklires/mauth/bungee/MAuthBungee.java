package io.github.miklires.mauth.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class MAuthBungee extends Plugin implements Listener {

    private static final String CHANNEL = "mauth:auth";

    private final Set<UUID> authenticated = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Long> nonces = new ConcurrentHashMap<>();
    private BungeeConfig config;

    @Override
    public void onEnable() {
        try {
            config = BungeeConfig.load(getDataFolder());
        } catch (Exception e) {
            throw new IllegalStateException("cannot load mAuth Bungee config", e);
        }
        ProxyServer.getInstance().registerChannel(CHANNEL);
        ProxyServer.getInstance().getPluginManager().registerListener(this, this);
        ProxyServer.getInstance().getScheduler().schedule(this, this::purgeNonces,
                1, 1, TimeUnit.MINUTES);
        getLogger().info("mAuth Bungee enabled with login servers " + config.loginServers());
    }

    @Override
    public void onDisable() {
        ProxyServer.getInstance().unregisterChannel(CHANNEL);
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getTag())) return;
        event.setCancelled(true);
        if (!(event.getSender() instanceof Server backend)
                || !(event.getReceiver() instanceof ProxiedPlayer player)) {
            return;
        }
        if (!config.loginServers().contains(backend.getInfo().getName())) return;
        if (!verify(event.getData(), player.getUniqueId())) return;
        authenticated.add(player.getUniqueId());
        ServerInfo target = ProxyServer.getInstance().getServerInfo(config.targetServer());
        if (target != null && !target.equals(backend.getInfo())) {
            player.connect(target, ServerConnectEvent.Reason.PLUGIN);
        }
    }

    @EventHandler
    public void onServerConnect(ServerConnectEvent event) {
        String requested = event.getTarget().getName();
        if (authenticated.contains(event.getPlayer().getUniqueId())
                || config.loginServers().contains(requested)) {
            return;
        }
        ServerInfo login = config.loginServers().stream()
                .map(ProxyServer.getInstance()::getServerInfo)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (login == null) {
            event.setCancelled(true);
            return;
        }
        event.setTarget(login);
        event.getPlayer().sendMessage(ChatColor.YELLOW + "Authenticate before joining this server");
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
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
            byte[] supplied = Base64.getUrlDecoder().decode(parts[4]);
            return MessageDigest.isEqual(sign(body), supplied);
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
