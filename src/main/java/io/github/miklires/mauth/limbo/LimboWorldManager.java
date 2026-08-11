package io.github.miklires.mauth.limbo;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
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

        limboWorld = creator.createWorld();
        if (limboWorld == null) {
            plugin.getLogger().severe("cannot create limbo world: " + name);
            return;
        }
        applyRules();
        plugin.getLogger().info("limbo world '" + name + "' ready");
    }

    private void applyRules() {
        limboWorld.setSpawnLocation(0, 100, 0);
        limboWorld.setDifficulty(org.bukkit.Difficulty.PEACEFUL);
        limboWorld.setTime(6000);
        limboWorld.setStorm(false);
        limboWorld.setThundering(false);
        applyGameRules(limboWorld);
    }

    @SuppressWarnings("deprecation")
    private void applyGameRules(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.DROWNING_DAMAGE, false);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.FREEZE_DAMAGE, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
    }

    public World getLimboWorld() {
        return limboWorld;
    }

    public boolean isInLimbo(Player player) {
        return player.getWorld().equals(limboWorld);
    }

    public void sendToLimbo(Player player) {
        if (limboWorld == null) return;
        Location spawn = new Location(limboWorld, 0.5, 100, 0.5, 0, 0);
        player.teleport(spawn);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFlying(false);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setHealth(20);
        player.setFoodLevel(20);
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
        player.teleport(target);
        player.setGameMode(player.getServer().getDefaultGameMode());
        player.setAllowFlight(player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR);
        player.setFlying(false);
        player.setFallDistance(0);
        return target;
    }

    private Location findSafeSpawn(World world) {
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX();
        int z = spawn.getBlockZ();
        int safeY = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, safeY, z + 0.5, spawn.getYaw(), spawn.getPitch());
    }
}
