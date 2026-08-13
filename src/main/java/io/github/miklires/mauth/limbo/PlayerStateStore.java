package io.github.miklires.mauth.limbo;

import io.github.miklires.mauth.MAuth;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class PlayerStateStore {

    private final MAuth plugin;
    private final Path directory;

    public PlayerStateStore(MAuth plugin) {
        this.plugin = plugin;
        directory = plugin.getDataFolder().toPath().resolve("pending-state");
    }

    public boolean protect(Player player) {
        if (!plugin.getConfigManager().isLimboEnabled()) return true;
        Path file = file(player);
        try {
            Files.createDirectories(directory);
            if (!Files.exists(file)) save(player, file);
            if (plugin.getConfigManager().isLimboInventoryHidden()) {
                player.getInventory().setContents(new ItemStack[player.getInventory().getSize()]);
                player.updateInventory();
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("cannot protect player state for " + player.getName() + ": " + e.getMessage());
            player.kick(plugin.getMessageUtil().getPlain(player, "auth.database-error"));
            return false;
        }
    }

    public Location restore(Player player) {
        Path file = file(player);
        if (!Files.exists(file)) return null;
        try {
            State state = read(file);
            player.getInventory().setContents(state.contents);
            player.setGameMode(state.gameMode);
            player.setAllowFlight(state.allowFlight);
            player.setFlying(state.allowFlight && state.flying);
            double max = player.getAttribute(Attribute.MAX_HEALTH) == null
                    ? 20 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.max(0.1, Math.min(max, state.health)));
            player.setFoodLevel(Math.max(0, Math.min(20, state.food)));
            player.setSaturation(Math.max(0, Math.min(state.saturation, player.getFoodLevel())));
            player.setExp(Math.max(0, Math.min(1, state.exp)));
            player.setLevel(Math.max(0, state.level));
            player.updateInventory();
            Files.delete(file);
            return state.location;
        } catch (Exception e) {
            plugin.getLogger().severe("cannot restore player state for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }

    public void restoreOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) restore(player);
    }

    private void save(Player player, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (var raw = new BufferedOutputStream(Files.newOutputStream(tmp));
             var data = new DataOutputStream(raw);
             var out = new BukkitObjectOutputStream(data)) {
            Location loc = player.getLocation();
            out.writeInt(1);
            out.writeUTF(loc.getWorld().getName());
            out.writeDouble(loc.getX());
            out.writeDouble(loc.getY());
            out.writeDouble(loc.getZ());
            out.writeFloat(loc.getYaw());
            out.writeFloat(loc.getPitch());
            out.writeUTF(player.getGameMode().name());
            out.writeBoolean(player.getAllowFlight());
            out.writeBoolean(player.isFlying());
            out.writeDouble(player.getHealth());
            out.writeInt(player.getFoodLevel());
            out.writeFloat(player.getSaturation());
            out.writeFloat(player.getExp());
            out.writeInt(player.getLevel());
            out.writeObject(player.getInventory().getContents());
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private State read(Path file) throws IOException, ClassNotFoundException {
        try (var raw = new BufferedInputStream(Files.newInputStream(file));
             var data = new DataInputStream(raw);
             var in = new BukkitObjectInputStream(data)) {
            if (in.readInt() != 1) throw new IOException("unknown state version");
            String worldName = in.readUTF();
            double x = in.readDouble();
            double y = in.readDouble();
            double z = in.readDouble();
            float yaw = in.readFloat();
            float pitch = in.readFloat();
            GameMode gameMode = GameMode.valueOf(in.readUTF());
            boolean allowFlight = in.readBoolean();
            boolean flying = in.readBoolean();
            double health = in.readDouble();
            int food = in.readInt();
            float saturation = in.readFloat();
            float exp = in.readFloat();
            int level = in.readInt();
            ItemStack[] contents = (ItemStack[]) in.readObject();
            var world = Bukkit.getWorld(worldName);
            Location location = world == null ? null : new Location(world, x, y, z, yaw, pitch);
            return new State(location, gameMode, allowFlight, flying, health, food, saturation, exp, level, contents);
        }
    }

    private Path file(Player player) {
        return directory.resolve(player.getUniqueId() + ".dat");
    }

    private record State(Location location, GameMode gameMode, boolean allowFlight, boolean flying,
                         double health, int food, float saturation, float exp, int level,
                         ItemStack[] contents) {
    }
}
