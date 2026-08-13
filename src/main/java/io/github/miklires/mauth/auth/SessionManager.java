package io.github.miklires.mauth.auth;

import org.bukkit.entity.Player;
import io.github.miklires.mauth.MAuth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class SessionManager {

    private final MAuth plugin;
    private final Map<UUID, String> authenticated = new ConcurrentHashMap<>();
    private final Map<String, UUID> online = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> pendingLogins = ConcurrentHashMap.newKeySet();
    private final Map<String, IpSession> ipSessions = new ConcurrentHashMap<>();
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public SessionManager(MAuth plugin) {
        this.plugin = plugin;
    }

    public boolean isAuthenticated(Player player) {
        return authenticated.containsKey(player.getUniqueId());
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticated.containsKey(uuid);
    }

    public void markAuthenticated(Player player) {
        markAuthenticated(player, io.github.miklires.mauth.api.PlayerAuthenticatedEvent.AuthReason.LOGIN);
    }

    public void markAuthenticated(Player player, io.github.miklires.mauth.api.PlayerAuthenticatedEvent.AuthReason reason) {
        authenticated.put(player.getUniqueId(), player.getName().toLowerCase());
        String username = player.getName().toLowerCase();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        if (ip != null) {
            UUID playerId = player.getUniqueId();
            ipSessions.put(username, new IpSession(ip, playerId, System.currentTimeMillis()));
            int ttl = plugin.getConfigManager().getSessionTtl();
            CompletableFuture.runAsync(() -> {
                try {
                    plugin.getSessionRepository().save(username, ip, playerId, ttl);
                } catch (java.sql.SQLException e) {
                    plugin.getLogger().warning("cannot save session: " + e.getMessage());
                }
            }, plugin.getAuthExecutor());
        }
        attempts.remove(username);

        plugin.getPluginScheduler().player(player, () ->
                org.bukkit.Bukkit.getPluginManager().callEvent(
                        new io.github.miklires.mauth.api.PlayerAuthenticatedEvent(player, reason)));
    }

    public void invalidatePersistentSession(String username) {
        CompletableFuture.runAsync(() -> {
            try {
                plugin.getSessionRepository().invalidate(username);
            } catch (java.sql.SQLException e) {
                plugin.getLogger().warning("cannot delete session: " + e.getMessage());
            }
        }, plugin.getAuthExecutor());
    }

    public void clear(Player player) {
        authenticated.remove(player.getUniqueId());
        pendingLogins.remove(player.getUniqueId());
    }

    public void clearAccount(String username) {
        String key = username.toLowerCase();
        authenticated.entrySet().removeIf(entry -> entry.getValue().equals(key));
        ipSessions.remove(key);
    }

    public void markOnline(Player player) {
        online.put(player.getName().toLowerCase(), player.getUniqueId());
    }

    public void markOffline(Player player) {
        online.remove(player.getName().toLowerCase(), player.getUniqueId());
        pendingLogins.remove(player.getUniqueId());
    }

    public boolean isOnline(String username) {
        return online.containsKey(username.toLowerCase());
    }

    public UUID getOnlineUuid(String username) {
        return online.get(username.toLowerCase());
    }

    public boolean beginLogin(UUID uuid) {
        return pendingLogins.add(uuid);
    }

    public void endLogin(UUID uuid) {
        pendingLogins.remove(uuid);
    }

    public void forget(String username, String ip) {
        IpSession session = ipSessions.get(username.toLowerCase());
        if (session != null && session.ip.equals(ip)) ipSessions.remove(username.toLowerCase());
    }

    public void forgetAll(String username) {
        ipSessions.remove(username.toLowerCase());
    }

    public boolean hasValidIpSession(String username, String ip, java.util.UUID playerUuid) {
        if (ip == null) return false;
        IpSession s = ipSessions.get(username.toLowerCase());
        if (s != null) {
            long ttl = plugin.getConfigManager().getSessionTtl() * 1000L;
            if (s.matches(ip, playerUuid, System.currentTimeMillis(), ttl)) {
                return true;
            }
            ipSessions.remove(username.toLowerCase());
        }
        if (playerUuid == null) return false;
        try {
            return plugin.getSessionRepository().hasValid(username, ip, playerUuid);
        } catch (java.sql.SQLException e) {
            plugin.getLogger().warning("session check failed: " + e.getMessage());
            return false;
        }
    }

    public boolean isLockedOut(String username) {
        AttemptState a = attempts.get(username.toLowerCase());
        if (a == null) return false;
        long lockoutMs = plugin.getConfigManager().getLockoutSeconds() * 1000L;
        return a.locked && (System.currentTimeMillis() - a.lockedAt < lockoutMs);
    }

    public long getLockoutRemainingSeconds(String username) {
        AttemptState a = attempts.get(username.toLowerCase());
        if (a == null || !a.locked) return 0;
        long lockoutMs = plugin.getConfigManager().getLockoutSeconds() * 1000L;
        long passed = System.currentTimeMillis() - a.lockedAt;
        return Math.max(0, (lockoutMs - passed) / 1000);
    }

    public void recordFailedAttempt(String username) {
        String key = username.toLowerCase();
        int max = plugin.getConfigManager().getMaxLoginAttempts();
        long windowMs = plugin.getConfigManager().getLoginAttemptWindow() * 1000L;
        AttemptState a = attempts.computeIfAbsent(key, k -> new AttemptState());
        long now = System.currentTimeMillis();
        if (now - a.firstAttemptAt > windowMs) {
            a.firstAttemptAt = now;
            a.count = 0;
            a.locked = false;
        }
        a.count++;
        if (a.count >= max) {
            a.locked = true;
            a.lockedAt = now;
        }
    }

    static record IpSession(String ip, UUID playerUuid, long createdAt) {
        boolean matches(String address, UUID uuid, long now, long ttl) {
            return ip.equals(address) && playerUuid.equals(uuid) && now - createdAt <= ttl;
        }
    }

    private static class AttemptState {
        int count;
        long firstAttemptAt = System.currentTimeMillis();
        boolean locked;
        long lockedAt;
    }
}
