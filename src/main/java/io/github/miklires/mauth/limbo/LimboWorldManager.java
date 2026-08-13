package io.github.miklires.mauth.limbo;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import io.github.miklires.mauth.MAuth;

public class LimboWorldManager {

    private final MAuth plugin;
    private World limboWorld;

    public LimboWorldManager(MAuth plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        if (!plugin.getConfigManager().isLimboEnabled()) return;
        String name = plugin.getConfigManager().getLimboWorldName();
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            limboWorld = existing;
            applyRules();
            return;
        }

        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidChunkGenerator());
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);

        try {
            limboWorld = creator.createWorld();
        } catch (UnsupportedOperationException e) {
            plugin.getLogger().warning("this server cannot create a limbo world; authentication restrictions remain active");
            return;
        }
        if (limboWorld == null) {
            plugin.getLogger().severe("cannot create limbo world: " + name);
            return;
        }
        applyRules();
        plugin.getLogger().info("limbo world '" + name + "' ready");
    }

    private void applyRules() {
        limboWorld.setSpawnLocation((int) plugin.getConfigManager().getLimboX(),
                (int) plugin.getConfigManager().getLimboY(),
                (int) plugin.getConfigManager().getLimboZ());
        limboWorld.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        limboWorld.setTime(6000);
        limboWorld.setStorm(false);
        limboWorld.setThundering(false);
        applyGameRules(limboWorld);
    }

    private void applyGameRules(World world) {
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.DROWNING_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_DAMAGE, false);
        world.setGameRule(GameRules.FREEZE_DAMAGE, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
    }

    public World getLimboWorld() {
        return limboWorld;
    }

    public boolean isInLimbo(Player player) {
        return player.getWorld().equals(limboWorld);
    }

    public void sendToLimbo(Player player) {
        if (limboWorld == null) return;
        Location spawn = new Location(limboWorld,
                plugin.getConfigManager().getLimboX(),
                plugin.getConfigManager().getLimboY(),
                plugin.getConfigManager().getLimboZ(),
                plugin.getConfigManager().getLimboYaw(),
                plugin.getConfigManager().getLimboPitch());
        player.teleportAsync(spawn).thenAccept(moved -> {
            if (!moved || !player.isOnline()) return;
            plugin.getPluginScheduler().player(player, () -> {
                player.setGameMode(GameMode.ADVENTURE);
                player.setFlying(false);
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setHealth(20);
                player.setFoodLevel(20);
            });
        });
    }

    public Location parseLocation(String serialized) {
        if (serialized == null || serialized.isEmpty()) return null;
        String[] parts = serialized.split(",");
        if (parts.length < 4) return null;
        try {
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) return null;
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            float yaw = parts.length > 4 ? Float.parseFloat(parts[4]) : 0f;
            float pitch = parts.length > 5 ? Float.parseFloat(parts[5]) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ","
                + loc.getX() + ","
                + loc.getY() + ","
                + loc.getZ() + ","
                + loc.getYaw() + ","
                + loc.getPitch();
    }

    public Location returnFromLimbo(Player player, Location savedLocation) {
        if (!plugin.getConfigManager().isLimboEnabled()) return player.getLocation();
        Location target = savedLocation;
        if (target == null) {
            World main = Bukkit.getWorlds().stream()
                    .filter(w -> !w.equals(limboWorld))
                    .findFirst()
                    .orElse(null);
            if (main == null) {
                plugin.getLogger().warning("no fallback world to return " + player.getName());
                return null;
            }
            target = findSafeSpawn(main);
        }
        player.teleportAsync(target).thenAccept(moved -> {
            if (!moved || !player.isOnline()) return;
            plugin.getPluginScheduler().player(player, () -> player.setFallDistance(0));
        });
        return target;
    }

    private Location findSafeSpawn(World world) {
        return world.getSpawnLocation();
    }
}
