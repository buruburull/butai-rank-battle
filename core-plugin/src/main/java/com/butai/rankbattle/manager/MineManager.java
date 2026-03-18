package com.butai.rankbattle.manager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the mining zone: ore positions, regeneration, and EP values.
 * Ores are defined in frames.yml with fixed coordinates.
 * When mined, ores become COBBLESTONE and regenerate after a configurable delay.
 */
public class MineManager {

    // EP values per ore type
    private static final Map<Material, Integer> ORE_EP = Map.of(
            Material.COAL_ORE, 1,
            Material.DEEPSLATE_COAL_ORE, 1,
            Material.IRON_ORE, 2,
            Material.DEEPSLATE_IRON_ORE, 2,
            Material.GOLD_ORE, 3,
            Material.DEEPSLATE_GOLD_ORE, 3,
            Material.EMERALD_ORE, 4,
            Material.DEEPSLATE_EMERALD_ORE, 4,
            Material.DIAMOND_ORE, 5,
            Material.DEEPSLATE_DIAMOND_ORE, 5
    );

    private final JavaPlugin plugin;
    private final Logger logger;

    // Registered ore locations and their original material
    private final Map<Location, Material> oreLocations = new ConcurrentHashMap<>();
    // Currently regenerating ores (location -> scheduled restore time)
    private final Set<Location> regenerating = ConcurrentHashMap.newKeySet();

    // Mine zone bounds
    private Location mineSpawn;
    private int regenDelayTicks = 600; // 30 seconds default

    public MineManager(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * Load mine configuration from frames.yml.
     */
    public void loadConfig(YamlConfiguration config) {
        ConfigurationSection mineSection = config.getConfigurationSection("growth_zones.mine");
        if (mineSection == null) {
            logger.warning("No mine configuration found in frames.yml (growth_zones.mine)");
            return;
        }

        String worldName = mineSection.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warning("Mine world not found: " + worldName);
            return;
        }

        // Load spawn point
        double sx = mineSection.getDouble("spawn.x", 0);
        double sy = mineSection.getDouble("spawn.y", 64);
        double sz = mineSection.getDouble("spawn.z", 0);
        float syaw = (float) mineSection.getDouble("spawn.yaw", 0);
        mineSpawn = new Location(world, sx, sy, sz, syaw, 0);

        // Load regen delay
        regenDelayTicks = mineSection.getInt("regen_delay_seconds", 30) * 20;

        // Load ore positions
        oreLocations.clear();
        ConfigurationSection oresSection = mineSection.getConfigurationSection("ores");
        if (oresSection != null) {
            for (String key : oresSection.getKeys(false)) {
                ConfigurationSection ore = oresSection.getConfigurationSection(key);
                if (ore == null) continue;

                int ox = ore.getInt("x");
                int oy = ore.getInt("y");
                int oz = ore.getInt("z");
                String materialName = ore.getString("material", "COAL_ORE");

                Material material;
                try {
                    material = Material.valueOf(materialName);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid ore material: " + materialName + " for ore " + key);
                    continue;
                }

                Location loc = new Location(world, ox, oy, oz);
                oreLocations.put(loc, material);
            }
        }

        logger.info("Mine loaded: " + oreLocations.size() + " ore positions, regen=" + (regenDelayTicks / 20) + "s");
    }

    /**
     * Check if a block location is a registered ore.
     */
    public boolean isRegisteredOre(Location blockLocation) {
        return oreLocations.containsKey(toBlockLocation(blockLocation));
    }

    /**
     * Check if the ore is currently regenerating (cobblestone state).
     */
    public boolean isRegenerating(Location blockLocation) {
        return regenerating.contains(toBlockLocation(blockLocation));
    }

    /**
     * Get EP value for the given ore material.
     * Returns 0 if not an ore.
     */
    public int getOreEP(Material material) {
        return ORE_EP.getOrDefault(material, 0);
    }

    /**
     * Handle an ore being mined: change to cobblestone and schedule regen.
     */
    public void onOreMined(Location blockLocation) {
        Location blockLoc = toBlockLocation(blockLocation);
        Material originalMaterial = oreLocations.get(blockLoc);
        if (originalMaterial == null) return;

        regenerating.add(blockLoc);

        // Set to cobblestone
        Block block = blockLoc.getBlock();
        block.setType(Material.COBBLESTONE);

        // Schedule regeneration
        new BukkitRunnable() {
            @Override
            public void run() {
                block.setType(originalMaterial);
                regenerating.remove(blockLoc);
            }
        }.runTaskLater(plugin, regenDelayTicks);
    }

    /**
     * Place all ores at their configured positions (for initial setup/reset).
     */
    public void placeAllOres() {
        for (Map.Entry<Location, Material> entry : oreLocations.entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue());
        }
        logger.info("Placed " + oreLocations.size() + " ores in mine zone.");
    }

    public Location getMineSpawn() {
        return mineSpawn;
    }

    public Map<Location, Material> getOreLocations() {
        return Collections.unmodifiableMap(oreLocations);
    }

    /**
     * Convert a location to block-aligned (integer) coordinates.
     */
    private Location toBlockLocation(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
