package io.github.miklires.mauth.captcha;

import io.github.miklires.mauth.MAuth;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public class FloodDetector {

    private final MAuth plugin;
    private final Deque<Long> connectionTimestamps = new ConcurrentLinkedDeque<>();
    private final AtomicLong floodTriggeredAt = new AtomicLong(0);

    public FloodDetector(MAuth plugin) {
        this.plugin = plugin;
    }

    public void recordConnection() {
        long now = System.currentTimeMillis();
        connectionTimestamps.addLast(now);
        prune(now);
        if (connectionTimestamps.size() >= plugin.getConfigManager().getFloodThreshold()) {
            long prev = floodTriggeredAt.get();
            floodTriggeredAt.set(now);
            if (prev == 0 || now - prev > plugin.getConfigManager().getFloodCooldownSeconds() * 1000L) {
                plugin.getLogger().warning("flood detected, captcha forced for "
                        + plugin.getConfigManager().getFloodCooldownSeconds() + "s ("
                        + connectionTimestamps.size() + " connections in window)");
                plugin.getAuditLogger().log(
                        io.github.miklires.mauth.audit.AuditEvent.FLOOD_TRIGGERED,
                        null, null,
                        "connections=" + connectionTimestamps.size());
            }
        }
    }

    public boolean isFloodActive() {
        long triggered = floodTriggeredAt.get();
        if (triggered == 0) return false;
        long cooldownMs = plugin.getConfigManager().getFloodCooldownSeconds() * 1000L;
        return System.currentTimeMillis() - triggered < cooldownMs;
    }

    private void prune(long now) {
        long windowMs = plugin.getConfigManager().getFloodWindowSeconds() * 1000L;
        while (!connectionTimestamps.isEmpty()) {
            Long oldest = connectionTimestamps.peekFirst();
            if (oldest == null || now - oldest > windowMs) {
                connectionTimestamps.pollFirst();
            } else {
                break;
            }
        }
    }
}
