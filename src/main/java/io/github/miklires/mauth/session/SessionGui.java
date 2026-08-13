package io.github.miklires.mauth.session;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.database.SessionRepository;
import io.github.miklires.mauth.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SessionGui implements CommandExecutor, Listener {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MAuth plugin;

    public SessionGui(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.sessions")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        if (!plugin.getSessionManager().isAuthenticated(player)) {
            plugin.getMessageUtil().send(player, "auth.not-logged-in");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        plugin.submit(player, () -> {
            try {
                return plugin.getSessionRepository().list(player.getName(), 7);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (sessions, error) -> {
            if (!player.isOnline()) return;
            if (error != null) {
                plugin.getMessageUtil().send(player, "auth.database-error");
                return;
            }
            show(player, sessions);
        });
    }

    private void show(Player player, List<SessionRepository.SessionRecord> sessions) {
        SessionHolder holder = new SessionHolder(player.getName(), sessions);
        Inventory inventory = Bukkit.createInventory(holder, 9,
                plugin.getMessageUtil().getPlain(player, "sessions.title"));
        holder.inventory = inventory;
        String currentIp = currentIp(player);
        UUID currentUuid = player.getUniqueId();
        for (int slot = 0; slot < sessions.size(); slot++) {
            SessionRepository.SessionRecord session = sessions.get(slot);
            boolean current = session.ip().equals(currentIp) && session.playerUuid().equals(currentUuid);
            inventory.setItem(slot, sessionItem(player, session, current));
        }
        inventory.setItem(8, actionItem(player, Material.BARRIER, "sessions.terminate-all"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SessionHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0) return;
        if (event.getRawSlot() == 8) {
            if (holder.confirmUntil < System.currentTimeMillis()) {
                holder.confirmUntil = System.currentTimeMillis() + 10_000L;
                event.getInventory().setItem(8,
                        actionItem(player, Material.REDSTONE_BLOCK, "sessions.confirm-all"));
            } else {
                terminateAll(player, holder.username);
            }
            return;
        }
        if (event.getRawSlot() >= holder.sessions.size()) return;
        terminate(player, holder.username, holder.sessions.get(event.getRawSlot()));
    }

    private void terminate(Player player, String username, SessionRepository.SessionRecord session) {
        plugin.submit(player, () -> {
            try {
                return plugin.getSessionRepository().delete(username, session.ip());
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (removed, error) -> {
            if (error != null) {
                plugin.getMessageUtil().send(player, "auth.database-error");
                return;
            }
            plugin.getSessionManager().forget(username, session.ip());
            if (session.ip().equals(currentIp(player))
                    && session.playerUuid().equals(player.getUniqueId())) {
                plugin.getSessionManager().clear(player);
                player.kick(plugin.getMessageUtil().getPlain(player, "sessions.current-terminated"));
            } else {
                plugin.getMessageUtil().send(player, "sessions.terminated");
                open(player);
            }
        });
    }

    private void terminateAll(Player player, String username) {
        plugin.submit(player, () -> {
            try {
                plugin.getSessionRepository().invalidate(username);
                return true;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (ignored, error) -> {
            if (error != null) {
                plugin.getMessageUtil().send(player, "auth.database-error");
                return;
            }
            plugin.getSessionManager().forgetAll(username);
            plugin.getSessionManager().clear(player);
            player.kick(plugin.getMessageUtil().getPlain(player, "sessions.all-terminated"));
        });
    }

    private ItemStack sessionItem(Player player, SessionRepository.SessionRecord session, boolean current) {
        ItemStack item = new ItemStack(current ? Material.LIME_DYE : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getMessageUtil().getPlain(player,
                current ? "sessions.current" : "sessions.previous"));
        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMessageUtil().getPlain(player, "sessions.ip",
                MessageUtil.ph("ip", mask(session.ip()))));
        lore.add(plugin.getMessageUtil().getPlain(player, "sessions.created",
                MessageUtil.ph("time", FORMAT.format(Instant.ofEpochSecond(session.createdAt())))));
        lore.add(plugin.getMessageUtil().getPlain(player, "sessions.expires",
                MessageUtil.ph("time", FORMAT.format(Instant.ofEpochSecond(session.expiresAt())))));
        lore.add(plugin.getMessageUtil().getPlain(player, "sessions.click-terminate"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack actionItem(Player player, Material material, String path) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getMessageUtil().getPlain(player, path));
        item.setItemMeta(meta);
        return item;
    }

    private String currentIp(Player player) {
        return player.getAddress() == null ? "" : player.getAddress().getAddress().getHostAddress();
    }

    private String mask(String ip) {
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) return parts[0] + ".xx.xx." + parts[3];
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":", -1);
            return parts[0] + ":" + (parts.length > 1 ? parts[1] : "") + ":xx:xx";
        }
        return "hidden";
    }

    private static class SessionHolder implements InventoryHolder {
        final String username;
        final List<SessionRepository.SessionRecord> sessions;
        Inventory inventory;
        long confirmUntil;

        SessionHolder(String username, List<SessionRepository.SessionRecord> sessions) {
            this.username = username;
            this.sessions = List.copyOf(sessions);
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
