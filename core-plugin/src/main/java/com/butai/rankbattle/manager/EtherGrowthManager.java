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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages the ether growth system:
 * - EP (Ether Points) tracking and level-up calculation
 * - Multi-floor MOB tower with per-floor spawning
 * - Ether cap updates
 * - Player inventory save/restore for tower entry
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

    // In-memory cache per player: [ep_total, growth_level, ore_mined, mob_killed, shards, shards_total]
    private final Map<UUID, int[]> playerGrowth = new ConcurrentHashMap<>();
    // Per-player permanent upgrade cache: upgrade_type -> level
    private final Map<UUID, Map<String, Integer>> playerUpgrades = new ConcurrentHashMap<>();
    // Per-player ether cap cache
    private final Map<UUID, Integer> etherCapCache = new ConcurrentHashMap<>();
    // Saved inventory for tower entry (restored on exit)
    private final Map<UUID, ItemStack[]> savedInventories = new ConcurrentHashMap<>();
    // Track which players are in the tower
    private final Set<UUID> playersInTower = ConcurrentHashMap.newKeySet();

    // MOB tower config
    private Location mobTowerSpawn; // 1F entrance
    private int spawnIntervalTicks = 100;
    private BukkitTask mobSpawnTask;

    // Floor data
    private final List<FloorData> floors = new ArrayList<>();

    /**
     * Represents a single floor in the MOB tower.
     */
    public static class FloorData {
        public final String id;
        public final String name;
        public final int requiredLevel;
        public final Location mobSpawnCenter;
        public final int mobSpawnRadius;
        public final int maxMobs;
        public final List<EntityType> mobTypes;
        public final Map<EntityType, Integer> epValues;
        public final Location playerSpawn;
        public final Location nextFloorNpcLoc;
        public final Location exitNpcLoc;

        public FloorData(String id, String name, int requiredLevel, Location mobSpawnCenter,
                         int mobSpawnRadius, int maxMobs, List<EntityType> mobTypes,
                         Map<EntityType, Integer> epValues, Location playerSpawn,
                         Location nextFloorNpcLoc, Location exitNpcLoc) {
            this.id = id;
            this.name = name;
            this.requiredLevel = requiredLevel;
            this.mobSpawnCenter = mobSpawnCenter;
            this.mobSpawnRadius = mobSpawnRadius;
            this.maxMobs = maxMobs;
            this.mobTypes = mobTypes;
            this.epValues = epValues;
            this.playerSpawn = playerSpawn;
            this.nextFloorNpcLoc = nextFloorNpcLoc;
            this.exitNpcLoc = exitNpcLoc;
        }
    }

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

        // 1F spawn (entry point)
        double sx = mobSection.getDouble("spawn.x", 0);
        double sy = mobSection.getDouble("spawn.y", 64);
        double sz = mobSection.getDouble("spawn.z", 0);
        float syaw = (float) mobSection.getDouble("spawn.yaw", 0);
        mobTowerSpawn = new Location(world, sx, sy, sz, syaw, 0);

        spawnIntervalTicks = mobSection.getInt("spawn_interval_seconds", 5) * 20;

        // Load floors
        floors.clear();
        ConfigurationSection floorsSection = mobSection.getConfigurationSection("floors");
        if (floorsSection == null) {
            logger.warning("No floors configured in mob tower");
            return;
        }

        for (String floorKey : floorsSection.getKeys(false)) {
            ConfigurationSection floor = floorsSection.getConfigurationSection(floorKey);
            if (floor == null) continue;

            String name = floor.getString("name", floorKey);
            int reqLevel = floor.getInt("required_level", 0);

            double mx = floor.getDouble("mob_spawn.x", sx);
            double my = floor.getDouble("mob_spawn.y", sy);
            double mz = floor.getDouble("mob_spawn.z", sz);
            Location spawnCenter = new Location(world, mx, my, mz);

            int radius = floor.getInt("mob_spawn.radius", 8);
            int maxMobs = floor.getInt("max_mobs", 8);

            List<EntityType> mobTypes = new ArrayList<>();
            List<String> typeNames = floor.getStringList("mob_types");
            for (String typeName : typeNames) {
                try {
                    mobTypes.add(EntityType.valueOf(typeName));
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid mob type: " + typeName + " on " + floorKey);
                }
            }

            Map<EntityType, Integer> epValues = new HashMap<>();
            ConfigurationSection epSection = floor.getConfigurationSection("ep_values");
            if (epSection != null) {
                for (String epKey : epSection.getKeys(false)) {
                    try {
                        epValues.put(EntityType.valueOf(epKey), epSection.getInt(epKey));
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid EP mob type: " + epKey);
                    }
                }
            }

            // Floor player spawn (defaults to mob spawn center)
            double psx = floor.getDouble("spawn.x", mx);
            double psy = floor.getDouble("spawn.y", my);
            double psz = floor.getDouble("spawn.z", mz);
            float psyaw = (float) floor.getDouble("spawn.yaw", 0);
            Location playerSpawn = new Location(world, psx, psy, psz, psyaw, 0);

            // Next floor NPC location
            Location nextNpcLoc = null;
            if (floor.contains("next_floor_npc")) {
                double nx = floor.getDouble("next_floor_npc.x");
                double ny = floor.getDouble("next_floor_npc.y");
                double nz = floor.getDouble("next_floor_npc.z");
                float nyaw = (float) floor.getDouble("next_floor_npc.yaw", 0);
                nextNpcLoc = new Location(world, nx, ny, nz, nyaw, 0);
            }

            // Exit NPC location
            Location exitNpcLoc = null;
            if (floor.contains("exit_npc")) {
                double ex = floor.getDouble("exit_npc.x");
                double ey = floor.getDouble("exit_npc.y");
                double ez = floor.getDouble("exit_npc.z");
                float eyaw = (float) floor.getDouble("exit_npc.yaw", 0);
                exitNpcLoc = new Location(world, ex, ey, ez, eyaw, 0);
            }

            floors.add(new FloorData(floorKey, name, reqLevel, spawnCenter, radius, maxMobs,
                    mobTypes, epValues, playerSpawn, nextNpcLoc, exitNpcLoc));
        }

        logger.info("Mob tower loaded: " + floors.size() + " floors, entry=" + mobTowerSpawn);
    }

    /**
     * Start the MOB auto-spawn loop for all floors.
     */
    public void startMobSpawner() {
        if (mobSpawnTask != null) return;
        if (floors.isEmpty()) {
            logger.warning("MOB spawner not started: no floors configured");
            return;
        }

        mobSpawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (FloorData floor : floors) {
                    spawnMobsForFloor(floor);
                }
            }
        }.runTaskTimer(plugin, 100L, spawnIntervalTicks);

        logger.info("MOB spawner started for " + floors.size() + " floors");
    }

    public void stopMobSpawner() {
        if (mobSpawnTask != null) {
            mobSpawnTask.cancel();
            mobSpawnTask = null;
        }
    }

    /**
     * Spawn mobs for a specific floor if players are nearby.
     */
    private void spawnMobsForFloor(FloorData floor) {
        if (floor.mobSpawnCenter == null || floor.mobTypes.isEmpty()) return;

        World world = floor.mobSpawnCenter.getWorld();
        if (world == null) return;

        // Check if any player is within 50 blocks
        boolean playerNearby = false;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(floor.mobSpawnCenter) <= 2500) {
                playerNearby = true;
                break;
            }
        }
        if (!playerNearby) return;

        // Count existing mobs
        int searchRadius = floor.mobSpawnRadius + 20;
        long currentMobCount = world.getEntities().stream()
                .filter(e -> floor.mobTypes.contains(e.getType()))
                .filter(e -> e.getLocation().distanceSquared(floor.mobSpawnCenter) <= searchRadius * searchRadius)
                .count();

        if (currentMobCount >= floor.maxMobs) return;

        Random rand = new Random();
        double offsetX = (rand.nextDouble() - 0.5) * 2 * floor.mobSpawnRadius;
        double offsetZ = (rand.nextDouble() - 0.5) * 2 * floor.mobSpawnRadius;
        Location spawnLoc = floor.mobSpawnCenter.clone().add(offsetX, 0, offsetZ);

        EntityType type = floor.mobTypes.get(rand.nextInt(floor.mobTypes.size()));
        Entity spawned = world.spawnEntity(spawnLoc, type);
        if (spawned instanceof Mob mob) {
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
        }
    }

    // ========== Tower entry/exit inventory management ==========

    /**
     * Save player inventory and prepare for tower entry.
     * Clears inventory, gives iron sword.
     */
    private static final org.bukkit.NamespacedKey TOWER_HP_MODIFIER_KEY =
            new org.bukkit.NamespacedKey("butairankbattle", "tower_hp_bonus");

    public void enterTower(Player player) {
        UUID uuid = player.getUniqueId();
        // Save current inventory
        savedInventories.put(uuid, player.getInventory().getContents().clone());
        playersInTower.add(uuid);

        // Clear and give iron sword
        player.getInventory().clear();
        player.getInventory().setItem(0, new ItemStack(org.bukkit.Material.IRON_SWORD));

        // Apply tower HP bonus from permanent upgrade
        applyTowerHPBonus(player);
    }

    /**
     * Restore player inventory when leaving tower.
     */
    public void exitTower(Player player) {
        UUID uuid = player.getUniqueId();
        playersInTower.remove(uuid);

        ItemStack[] saved = savedInventories.remove(uuid);
        if (saved != null) {
            player.getInventory().clear();
            player.getInventory().setContents(saved);
        }

        // Remove tower HP bonus
        removeTowerHPBonus(player);
    }

    private void applyTowerHPBonus(Player player) {
        double bonusHP = getTowerBonusHP(player.getUniqueId());
        if (bonusHP <= 0) return;

        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) return;

        // Remove existing modifier if any
        attr.removeModifier(TOWER_HP_MODIFIER_KEY);

        org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                TOWER_HP_MODIFIER_KEY, bonusHP,
                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                org.bukkit.inventory.EquipmentSlotGroup.ANY);
        attr.addModifier(modifier);
        player.setHealth(attr.getValue());
    }

    private void removeTowerHPBonus(Player player) {
        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.removeModifier(TOWER_HP_MODIFIER_KEY);
        // Clamp health to new max
        if (player.getHealth() > attr.getValue()) {
            player.setHealth(attr.getValue());
        }
    }

    /**
     * Check if player is currently in the tower.
     */
    public boolean isInTower(UUID uuid) {
        return playersInTower.contains(uuid);
    }

    // ========== Floor navigation ==========

    /**
     * Get floor data by index (0-based).
     */
    public FloorData getFloor(int index) {
        if (index < 0 || index >= floors.size()) return null;
        return floors.get(index);
    }

    /**
     * Get the floor index from an NPC location (matches next_floor_npc position).
     * Returns the index of the floor CONTAINING the NPC (not the target floor).
     */
    public int getFloorIndexByNpcLocation(Location npcLoc) {
        for (int i = 0; i < floors.size(); i++) {
            Location npc = floors.get(i).nextFloorNpcLoc;
            if (npc != null && npc.distanceSquared(npcLoc) < 4) { // within 2 blocks
                return i;
            }
        }
        return -1;
    }

    /**
     * Get the number of floors.
     */
    public int getFloorCount() {
        return floors.size();
    }

    /**
     * Get all floors.
     */
    public List<FloorData> getFloors() {
        return Collections.unmodifiableList(floors);
    }

    // ========== EP/Growth system ==========

    /**
     * Get MOB EP value for an entity type, checking all floors.
     */
    public int getMobEP(EntityType type) {
        for (FloorData floor : floors) {
            Integer ep = floor.epValues.get(type);
            if (ep != null) return ep;
        }
        return 0;
    }

    /**
     * Check if a location is within any mob tower floor area.
     */
    public boolean isInMobTowerArea(Location loc) {
        for (FloorData floor : floors) {
            if (floor.mobSpawnCenter == null) continue;
            if (!Objects.equals(loc.getWorld(), floor.mobSpawnCenter.getWorld())) continue;
            int range = floor.mobSpawnRadius + 30;
            if (loc.distanceSquared(floor.mobSpawnCenter) <= range * range) {
                return true;
            }
        }
        return false;
    }

    // ========== Season/Growth persistence ==========

    private int getOrCreateSeasonId() {
        int seasonId = seasonDAO.getActiveSeasonId();
        if (seasonId < 0) {
            seasonId = seasonDAO.createSeason("Default");
            if (seasonId < 0) {
                logger.warning("Failed to create default season for growth system, using memory-only mode");
            }
        }
        return seasonId;
    }

    public void loadPlayer(UUID uuid) {
        int seasonId = getOrCreateSeasonId();
        if (seasonId < 0) {
            playerGrowth.put(uuid, new int[]{0, 0, 0, 0, 0, 0});
        } else {
            int[] data = growthDAO.getOrCreateGrowth(uuid, seasonId);
            playerGrowth.put(uuid, data);
        }

        int etherCap = growthDAO.getEtherCap(uuid);
        etherCapCache.put(uuid, etherCap);

        // Load permanent upgrades
        Map<String, Integer> upgrades = growthDAO.getAllUpgrades(uuid);
        playerUpgrades.put(uuid, upgrades);
    }

    public void savePlayer(UUID uuid) {
        int seasonId = seasonDAO.getActiveSeasonId();
        if (seasonId < 0) return;

        int[] data = playerGrowth.get(uuid);
        if (data != null) {
            growthDAO.updateGrowth(uuid, seasonId, data[0], data[1], data[2], data[3], data[4], data[5]);
        }
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        playerGrowth.remove(uuid);
        etherCapCache.remove(uuid);
        playerUpgrades.remove(uuid);
        // If player was in tower, restore inventory
        if (playersInTower.remove(uuid)) {
            savedInventories.remove(uuid);
        }
    }

    public void addOreEP(UUID uuid, int ep) {
        addEP(uuid, ep, true);
    }

    public void addMobEP(UUID uuid, int ep) {
        addEP(uuid, ep, false);
    }

    private void addEP(UUID uuid, int ep, boolean isOre) {
        int[] data = playerGrowth.get(uuid);
        if (data == null) return;

        data[0] += ep;
        if (isOre) {
            data[2]++;
        } else {
            data[3]++;
        }

        int currentLevel = data[1];
        if (currentLevel >= MAX_LEVEL) return;

        int requiredEP = getRequiredEP(currentLevel);
        while (data[0] >= requiredEP && currentLevel < MAX_LEVEL) {
            data[0] -= requiredEP;
            currentLevel++;
            data[1] = currentLevel;

            int newCap = Math.min(BASE_ETHER_CAP + currentLevel * ETHER_PER_LEVEL, MAX_ETHER_CAP);
            etherCapCache.put(uuid, newCap);
            growthDAO.updateEtherCap(uuid, newCap);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendTitle("§a§lLEVEL UP!", "§eエーテルLv." + currentLevel + " §8| §bエーテル上限: " + newCap, 5, 40, 20);
                MessageUtil.sendSuccess(player, "§aエーテル成長Lv." + currentLevel + "§7に到達！エーテル上限: §b" + newCap);
            }

            requiredEP = getRequiredEP(currentLevel);
        }

        if ((data[2] + data[3]) % 10 == 0) {
            savePlayer(uuid);
        }
    }

    public static int getRequiredEP(int level) {
        return (int) Math.ceil(BASE_EP * Math.pow(EXPONENT, level));
    }

    public int getEtherCap(UUID uuid) {
        return etherCapCache.getOrDefault(uuid, BASE_ETHER_CAP);
    }

    public int getGrowthLevel(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[1] : 0;
    }

    public int getEPTotal(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[0] : 0;
    }

    public int getEPForNextLevel(UUID uuid) {
        int level = getGrowthLevel(uuid);
        if (level >= MAX_LEVEL) return -1;
        return getRequiredEP(level);
    }

    public int getOreMined(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[2] : 0;
    }

    public int getMobKilled(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[3] : 0;
    }

    public Location getMobTowerSpawn() {
        return mobTowerSpawn;
    }

    public void saveAll() {
        for (UUID uuid : playerGrowth.keySet()) {
            savePlayer(uuid);
        }
    }

    // ========== Shard System ==========

    public int getShards(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[4] : 0;
    }

    public int getShardsTotal(UUID uuid) {
        int[] data = playerGrowth.get(uuid);
        return data != null ? data[5] : 0;
    }

    public void addShards(UUID uuid, int amount) {
        int[] data = playerGrowth.get(uuid);
        if (data == null) return;
        data[4] += amount;
        data[5] += amount;
    }

    /**
     * Spend shards. Returns true if successful, false if insufficient.
     */
    public boolean spendShards(UUID uuid, int amount) {
        int[] data = playerGrowth.get(uuid);
        if (data == null || data[4] < amount) return false;
        data[4] -= amount;
        return true;
    }

    // ========== Permanent Upgrades ==========

    public int getUpgradeLevel(UUID uuid, String upgradeType) {
        Map<String, Integer> upgrades = playerUpgrades.get(uuid);
        if (upgrades == null) return 0;
        return upgrades.getOrDefault(upgradeType, 0);
    }

    public void setUpgradeLevel(UUID uuid, String upgradeType, int level, int totalSpent) {
        playerUpgrades.computeIfAbsent(uuid, k -> new HashMap<>()).put(upgradeType, level);
        growthDAO.setUpgradeLevel(uuid, upgradeType, level, totalSpent);
    }

    /**
     * Get tower bonus HP (from tower_hp upgrade). Each level = +2 HP (1 heart).
     */
    public double getTowerBonusHP(UUID uuid) {
        return getUpgradeLevel(uuid, "tower_hp") * 2.0;
    }

    /**
     * Get tower bonus attack damage (from tower_atk upgrade). Each level = +1.0 damage.
     */
    public double getTowerBonusAttack(UUID uuid) {
        return getUpgradeLevel(uuid, "tower_atk") * 1.0;
    }

    public EtherGrowthDAO getGrowthDAO() {
        return growthDAO;
    }

    public static int getMaxLevel() { return MAX_LEVEL; }
    public static int getMaxEtherCap() { return MAX_ETHER_CAP; }
    public static int getBaseEtherCap() { return BASE_ETHER_CAP; }
    public static int getEtherPerLevel() { return ETHER_PER_LEVEL; }
}
