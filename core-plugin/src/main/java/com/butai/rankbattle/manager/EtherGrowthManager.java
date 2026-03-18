package com.butai.rankbattle.manager;

import com.butai.rankbattle.database.EtherGrowthDAO;
import com.butai.rankbattle.database.SeasonDAO;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the ether growth system:
 * - EP (Ether Points) tracking and level-up calculation
 * - MOB tower auto-spawning
 * - Ether cap updates
 *
 * Growth formula: requiredEP = BASE_EP * (EXPONENT ^ currentLevel)
 * Each level grants +ETHER_PER_LEVEL to ether cap.
 * Max level = MAX_LEVEL (cap = 1000 + MAX_LEVEL * ETHER_PER_LEVEL = 2000)
 */
public class EtherGrowthManager {

    // Growth parameters
    private static final int BASE_EP = 100;
    private static final double EXPONENT = 1.10;
    private static final int ETHER_PER_LEVEL = 25;
    private static final int MAX_LEVEL = 40;
    private static final int BASE_ETHER_CAP = 1000;
    private static final int MAX_ETHER_CAP = 2000;

    private final JavaPlugin plugin;
    private final Logger logger;
    private final EtherGrowthDAO growthDAO;
    private final SeasonDAO seasonDAO;

    // In-memory cache per player: [ep_total, growth_level, ore_mined, mob_killed]
    private final Map<UUID, int[]> playerGrowth = new ConcurrentHashMap<>();
    // Per-player ether cap cache
    private final Map<UUID, Integer> etherCapCache = new ConcurrentHashMap<>();

    // MOB tower config
    private Location mobTowerSpawn;
    private Location mobSpawnCenter;
    private int mobSpawnRadius = 5;
    private int maxMobs = 10;
    private int spawnIntervalTicks = 100; // 5 seconds
    private List<EntityType> mobTypes = List.of(EntityType.ZOMBIE, EntityType.SKELETON);
    private BukkitTask mobSpawnTask;

    // MOB EP values
    private static final Map<EntityType, Integer> MOB_EP = Map.of(
            EntityType.ZOMBIE, 2,
            EntityType.SKELETON, 3
    );

    public EtherGrowthManager(JavaPlugin plugin, Logger logger, EtherGrowthDAO growthDAO, SeasonDAO seasonDAO) {
        this.plugin = plugin;
        this.logger = logger;
        this.growthDAO = growthDAO;
        this.seasonDAO = seasonDAO;
    }

    /**
     * Load MOB tower configuration from frames.yml.
     */
    public void loadConfig(YamlConfiguration config) {
        ConfigurationSection mobSection = config.getConfigurationSection("growth_zones.mob_tower");
        if (mobSection == null) {
            logger.warning("No mob tower configuration found in frames.yml (growth_zones.mob_tower)");
            return;
        }

        String worldName = mobSection.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warning("Mob tower world not found: " + worldName);
            return;
        }

        // Spawn point for players
        double sx = mobSection.getDouble("spawn.x", 0);
        double sy = mobSection.getDouble("spawn.y", 64);
        double sz = mobSection.getDouble("spawn.z", 0);
        float syaw = (float) mobSection.getDouble("spawn.yaw", 0);
        mobTowerSpawn = new Location(world, sx, sy, sz, syaw, 0);

        // MOB spawn center
        double mx = mobSection.getDouble("mob_spawn.x", sx);
        double my = mobSection.getDouble("mob_spawn.y", sy);
        double mz = mobSection.getDouble("mob_spawn.z", sz);
        mobSpawnCenter = new Location(world, mx, my, mz);

        mobSpawnRadius = mobSection.getInt("mob_spawn.radius", 5);
        maxMobs = mobSection.getInt("max_mobs", 10);
        spawnIntervalTicks = mobSection.getInt("spawn_interval_seconds", 5) * 20;

        logger.info("Mob tower loaded: spawn=" + mobTowerSpawn + ", maxMobs=" + maxMobs);
    }

    /**
     * Start the MOB auto-spawn loop.
     */
    public void startMobSpawner() {
        if (mobSpawnTask != null) return;
        if (mobSpawnCenter == null) {
            logger.warning("MOB spawner not started: mobSpawnCenter is null (config not loaded?)");
            return;
        }

        mobSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                spawnMobs();
            }
        }.runTaskTimer(plugin, 100L, spawnIntervalTicks);

        logger.info("MOB spawner started (interval=" + (spawnIntervalTicks / 20) + "s)");
    }

    /**
     * Stop the MOB auto-spawn loop.
     */
    public void stopMobSpawner() {
        if (mobSpawnTask != null) {
            mobSpawnTask.cancel();
            mobSpawnTask = null;
        }
    }

    /**
     * Spawn mobs if there are players nearby and mob count is below max.
     */
    private void spawnMobs() {
        if (mobSpawnCenter == null) return;

        World world = mobSpawnCenter.getWorld();
        if (world == null) return;

        // Check if any player is within 50 blocks of the spawn center
        boolean playerNearby = false;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(mobSpawnCenter) <= 2500) { // 50^2
                playerNearby = true;
                break;
            }
        }
        if (!playerNearby) return;

        // Count existing mobs in the area
        long currentMobCount = world.getEntities().stream()
                .filter(e -> mobTypes.contains(e.getType()))
                .filter(e -> e.getLocation().distanceSquared(mobSpawnCenter) <= (mobSpawnRadius + 20) * (mobSpawnRadius + 20))
                .count();

        if (currentMobCount >= maxMobs) return;

        // Spawn a mob at random position
        Random rand = new Random();
        double offsetX = (rand.nextDouble() - 0.5) * 2 * mobSpawnRadius;
        double offsetZ = (rand.nextDouble() - 0.5) * 2 * mobSpawnRadius;
        Location spawnLoc = mobSpawnCenter.clone().add(offsetX, 0, offsetZ);

        EntityType type = mobTypes.get(rand.nextInt(mobTypes.size()));
        Entity spawned = world.spawnEntity(spawnLoc, type);
        // Prevent natural despawning
        if (spawned instanceof Mob mob) {
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
        }
    }

    /**
     * Load player growth data from DB (call on join).
     */
    public void loadPlayer(UUID uuid) {
        int seasonId = seasonDAO.getActiveSeasonId();
        if (seasonId < 0) return;

        int[] data = growthDAO.getOrCreateGrowth(uuid, seasonId);
        playerGrowth.put(uuid, data);

        int etherCap = growthDAO.getEtherCap(uuid);
        etherCapCache.put(uuid, etherCap);
    }

    /**
     * Save player growth data to DB (call on quit or periodically).
     */
    public void savePlayer(UUID uuid) {
        int seasonId = seasonDAO.getActiveSeasonId();
        if (seasonId < 0) return;

        int[] data = playerGrowth.get(uuid);
        if (data != null) {
            growthDAO.updateGrowth(uuid, seasonId, data[0], data[1], data[2], data[3]);
        }
    }

    /**
     * Unload player data from cache.
     */
    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        playerGrowth.remove(uuid);
        etherCapCache.remove(uuid);
    }

    /**
     * Add EP from mining an ore.
     */
    public void addOreEP(UUID uuid, int ep) {
        addEP(uuid, ep, true);
    }

    /**
     * Add EP from killing a mob.
     */
    public void addMobEP(UUID uuid, int ep) {
        addEP(uuid, ep, false);
    }

    /**
     * Get MOB EP value for an entity type.
     */
    public int getMobEP(EntityType type) {
        return MOB_EP.getOrDefault(type, 0);
    }

    /**
     * Add EP and check for level up.
     */
    private void addEP(UUID uuid, int ep, boolean isOre) {
        int[] data = playerGrowth.get(uuid);
        if (data == null) return;

        data[0] += ep; // ep_total
        if (isOre) {
            data[2]++; // ore_mined
        } else {
            data[3]++; // mob_killed
        }

        // Check for level up
        int currentLevel = data[1];
        if (currentLevel >= MAX_LEVEL) return;

        int requiredEP = getRequiredEP(currentLevel);
        while (data[0] >= requiredEP && currentLevel < MAX_LEVEL) {
            data[0] -= requiredEP;
            currentLevel++;
            data[1] = currentLevel;

            // Update ether cap
            int newCap = Math.min(BASE_ETHER_CAP + currentLevel * ETHER_PER_LEVEL, MAX_ETHER_CAP);
            etherCapCache.put(uuid, newCap);
            growthDAO.updateEtherCap(uuid, newCap);

            // Notify player
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle("§a§lLEVEL UP!", "§eエーテルLv." + currentLevel + " §8| §bエーテル上限: " + newCap, 5, 40, 20);
                MessageUtil.sendSuccess(player, "§aエーテル成長Lv." + currentLevel + "§7に到達！エーテル上限: §b" + newCap);
            }

            requiredEP = getRequiredEP(currentLevel);
        }

        // Auto-save periodically (every 10 actions)
        if ((data[2] + data[3]) % 10 == 0) {
            savePlayer(uuid);
        }
    }

    /**
     * Calculate required EP for a given level.
     * Formula: BASE_EP * EXPONENT^level
     */
    public static int getRequiredEP(int level) {
        return (int) Math.ceil(BASE_EP * Math.pow(EXPONENT, level));
    }

    /**
     * Get player's current ether cap.
     */
    public int getEtherCap(UUID uuid) {
        return etherCapCache.getOrDefault(uuid, BASE_ETHER_CAP);
    }

    /**
     * Get player's current growth level.
     */
    public int getGrowthLevel(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[1] : 0;
    }

    /**
     * Get player's current EP total.
     */
    public int getEPTotal(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[0] : 0;
    }

    /**
     * Get EP required for next level.
     */
    public int getEPForNextLevel(UUID uuid) {
        int level = getGrowthLevel(uuid);
        if (level >= MAX_LEVEL) return -1; // max level reached
        return getRequiredEP(level);
    }

    /**
     * Get player's ore mined count.
     */
    public int getOreMined(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[2] : 0;
    }

    /**
     * Get player's mob killed count.
     */
    public int getMobKilled(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[3] : 0;
    }

    public Location getMobTowerSpawn() {
        return mobTowerSpawn;
    }

    /**
     * Check if a location is within the mob tower area.
     */
    public boolean isInMobTowerArea(Location loc) {
        if (mobSpawnCenter == null) return false;
        if (!Objects.equals(loc.getWorld(), mobSpawnCenter.getWorld())) return false;
        return loc.distanceSquared(mobSpawnCenter) <= (mobSpawnRadius + 30) * (mobSpawnRadius + 30);
    }

    /**
     * Save all online players' growth data.
     */
    public void saveAll() {
        for (UUID uuid : playerGrowth.keySet()) {
            savePlayer(uuid);
        }
    }

    public static int getMaxLevel() {
        return MAX_LEVEL;
    }

    public static int getMaxEtherCap() {
        return MAX_ETHER_CAP;
    }

    public static int getBaseEtherCap() {
        return BASE_ETHER_CAP;
    }

    public static int getEtherPerLevel() {
        return ETHER_PER_LEVEL;
    }
}
