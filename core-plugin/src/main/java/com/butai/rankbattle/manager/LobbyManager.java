package com.butai.rankbattle.manager;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.model.BRBPlayer;
import com.butai.rankbattle.util.MessageUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages lobby NPCs, holograms, and action bar display.
 * - Spawns NPCs (Villagers) with PDC tags for click handling
 * - Spawns TextDisplay holograms (welcome banner + ranking TOP10)
 * - Updates action bar with player status every 2 seconds
 * - All settings are loaded from frames.yml (not config.yml)
 */
public class LobbyManager {

    public static final NamespacedKey NPC_KEY = new NamespacedKey("butairankbattle", "npc_command");
    public static final NamespacedKey GROWTH_TELEPORT_KEY = new NamespacedKey("butairankbattle", "growth_teleport");
    public static final NamespacedKey FLOOR_ACTION_KEY = new NamespacedKey("butairankbattle", "floor_action");
    public static final NamespacedKey HOLOGRAM_KEY = new NamespacedKey("butairankbattle", "hologram_type");

    private final BRBPlugin plugin;
    private final Logger logger;
    private YamlConfiguration framesConfig;

    private Location lobbyLocation;
    private final List<UUID> spawnedNPCs = new ArrayList<>();
    private final List<UUID> spawnedHolograms = new ArrayList<>();
    private BukkitTask actionBarTask;
    private BukkitTask rankingUpdateTask;
    private TextDisplay rankingDisplay;

    public LobbyManager(BRBPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
        // Load frames.yml
        File framesFile = new File(plugin.getDataFolder(), "frames.yml");
        if (framesFile.exists()) {
            this.framesConfig = YamlConfiguration.loadConfiguration(framesFile);
        } else {
            logger.warning("frames.yml not found, lobby features disabled.");
            this.framesConfig = new YamlConfiguration();
        }
    }

    /**
     * Initialize lobby: load location, spawn NPCs, holograms, start action bar.
     */
    public void initialize() {
        loadLobbyLocation();
        // Delay entity spawning by 1 tick to ensure world is ready
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOldEntities();
                spawnNPCs();
                spawnGrowthNPCs();
                spawnFloorNPCs();
                spawnHolograms();
                startActionBarLoop();
                startRankingUpdateLoop();
                logger.info("Lobby initialized: NPCs=" + spawnedNPCs.size()
                        + ", Holograms=" + spawnedHolograms.size());
            }
        }.runTaskLater(plugin, 20L);
    }

    /**
     * Shutdown: remove spawned entities and stop tasks.
     */
    public void shutdown() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        if (rankingUpdateTask != null) {
            rankingUpdateTask.cancel();
            rankingUpdateTask = null;
        }
        removeSpawnedEntities();
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    /**
     * Teleport a player to the lobby and set ADVENTURE mode.
     */
    public void sendToLobby(Player player) {
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
        } else {
            player.teleport(player.getWorld().getSpawnLocation());
        }
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
    }

    // ========== Lobby Location ==========

    private void loadLobbyLocation() {
        String worldName = framesConfig.getString("lobby.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            logger.warning("Lobby world '" + worldName + "' not found, using default spawn.");
            world = Bukkit.getWorlds().get(0);
        }
        double x = framesConfig.getDouble("lobby.x", world.getSpawnLocation().getX());
        double y = framesConfig.getDouble("lobby.y", world.getSpawnLocation().getY());
        double z = framesConfig.getDouble("lobby.z", world.getSpawnLocation().getZ());
        float yaw = (float) framesConfig.getDouble("lobby.yaw", 0.0);
        float pitch = (float) framesConfig.getDouble("lobby.pitch", 0.0);
        lobbyLocation = new Location(world, x, y, z, yaw, pitch);
    }

    // ========== NPC Management ==========

    /**
     * Clean up old BRB entities from previous server sessions.
     */
    private void cleanupOldEntities() {
        // Load all chunks where NPCs/holograms are configured
        loadConfiguredChunks();

        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                boolean shouldRemove = false;

                // Remove entities with our PDC tags
                if (entity.getPersistentDataContainer().has(NPC_KEY, PersistentDataType.STRING)
                        || entity.getPersistentDataContainer().has(HOLOGRAM_KEY, PersistentDataType.STRING)) {
                    shouldRemove = true;
                }

                // Remove ALL Villagers with no AI near lobby (our NPCs)
                if (!shouldRemove && entity instanceof Villager villager
                        && !villager.hasAI() && lobbyLocation != null
                        && entity.getWorld().equals(lobbyLocation.getWorld())
                        && entity.getLocation().distance(lobbyLocation) < 100) {
                    shouldRemove = true;
                }

                // Remove ALL TextDisplays near lobby (our holograms)
                if (!shouldRemove && entity instanceof TextDisplay
                        && lobbyLocation != null
                        && entity.getWorld().equals(lobbyLocation.getWorld())
                        && entity.getLocation().distance(lobbyLocation) < 100) {
                    shouldRemove = true;
                }

                if (shouldRemove) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up " + removed + " old lobby entities.");
        }
    }

    /**
     * Load chunks for all configured NPC and hologram locations to ensure entities are accessible.
     */
    private void loadConfiguredChunks() {
        if (lobbyLocation != null) {
            lobbyLocation.getChunk().load();
        }

        // Load NPC chunks
        ConfigurationSection npcs = framesConfig.getConfigurationSection("npcs");
        if (npcs != null) {
            for (String key : npcs.getKeys(false)) {
                Location loc = getLocationFromSection(npcs.getConfigurationSection(key));
                if (loc != null) loc.getChunk().load();
            }
        }

        // Load hologram chunks
        ConfigurationSection holos = framesConfig.getConfigurationSection("holograms");
        if (holos != null) {
            for (String key : holos.getKeys(false)) {
                Location loc = getLocationFromSection(holos.getConfigurationSection(key));
                if (loc != null) loc.getChunk().load();
            }
        }
    }

    /**
     * Spawn NPC villagers from config.
     */
    private void spawnNPCs() {
        ConfigurationSection npcsSection = framesConfig.getConfigurationSection("npcs");
        if (npcsSection == null) {
            logger.warning("No NPCs configured in config.yml");
            return;
        }

        for (String key : npcsSection.getKeys(false)) {
            ConfigurationSection npc = npcsSection.getConfigurationSection(key);
            if (npc == null) continue;

            String worldName = npc.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            double x = npc.getDouble("x");
            double y = npc.getDouble("y");
            double z = npc.getDouble("z");
            float yaw = (float) npc.getDouble("yaw", 0.0);
            String name = npc.getString("name", key);
            String subtitle = npc.getString("subtitle", "");
            String command = npc.getString("command", "");

            Location loc = new Location(world, x, y, z, yaw, 0f);

            Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setProfession(Villager.Profession.NONE);
            villager.setVillagerType(Villager.Type.PLAINS);

            // Store command in PDC for click handling
            villager.getPersistentDataContainer().set(NPC_KEY, PersistentDataType.STRING, command);

            spawnedNPCs.add(villager.getUniqueId());

            // Spawn subtitle as TextDisplay below NPC name
            if (!subtitle.isEmpty()) {
                TextDisplay td = (TextDisplay) world.spawnEntity(
                        loc.clone().add(0, 2.3, 0), EntityType.TEXT_DISPLAY);
                td.setText(name + "\n" + subtitle);
                td.setBillboard(Display.Billboard.CENTER);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setSeeThrough(false);
                td.setShadowed(true);
                td.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, "npc_label");
                spawnedHolograms.add(td.getUniqueId());
            }

            logger.info("NPC spawned: " + key + " at " + locToString(loc));
        }
    }

    /**
     * Spawn Growth Zone NPCs (mine, mob tower) with teleport PDC tag.
     */
    private void spawnGrowthNPCs() {
        ConfigurationSection section = framesConfig.getConfigurationSection("growth_npcs");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection npc = section.getConfigurationSection(key);
            if (npc == null) continue;

            String worldName = npc.getString("world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            double x = npc.getDouble("x");
            double y = npc.getDouble("y");
            double z = npc.getDouble("z");
            float yaw = (float) npc.getDouble("yaw", 0.0);
            String name = npc.getString("name", key);
            String subtitle = npc.getString("subtitle", "");
            String teleportTo = npc.getString("teleport_to", "");

            Location loc = new Location(world, x, y, z, yaw, 0f);

            Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
            villager.setCustomName(null);
            villager.setCustomNameVisible(false);
            villager.setAI(false);
            villager.setInvulnerable(true);
            villager.setSilent(true);
            villager.setCollidable(false);
            villager.setProfession(Villager.Profession.NONE);
            villager.setVillagerType(Villager.Type.PLAINS);

            // Store teleport destination in PDC
            villager.getPersistentDataContainer().set(GROWTH_TELEPORT_KEY, PersistentDataType.STRING, teleportTo);
            // Also set NPC_KEY so cleanup recognizes it
            villager.getPersistentDataContainer().set(NPC_KEY, PersistentDataType.STRING, "");

            spawnedNPCs.add(villager.getUniqueId());

            if (!subtitle.isEmpty()) {
                TextDisplay td = (TextDisplay) world.spawnEntity(
                        loc.clone().add(0, 2.3, 0), EntityType.TEXT_DISPLAY);
                td.setText(name + "\n" + subtitle);
                td.setBillboard(Display.Billboard.CENTER);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setSeeThrough(false);
                td.setShadowed(true);
                td.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, "npc_label");
                spawnedHolograms.add(td.getUniqueId());
            }

            logger.info("Growth NPC spawned: " + key + " → " + teleportTo);
        }
    }

    /**
     * Spawn floor NPCs for each mob tower floor (next floor / exit).
     */
    private void spawnFloorNPCs() {
        EtherGrowthManager gm = plugin.getEtherGrowthManager();
        if (gm == null || gm.getFloorCount() == 0) return;

        java.util.List<EtherGrowthManager.FloorData> floors = gm.getFloors();
        for (int i = 0; i < floors.size(); i++) {
            EtherGrowthManager.FloorData floor = floors.get(i);

            // "Next floor" NPC on this floor (goes to floor i+1)
            if (floor.nextFloorNpcLoc != null && i + 1 < floors.size()) {
                EtherGrowthManager.FloorData nextFloor = floors.get(i + 1);
                spawnFloorNPC(floor.nextFloorNpcLoc,
                        "§b§l次の階へ §8→ " + nextFloor.name,
                        "§7必要Lv." + nextFloor.requiredLevel,
                        "next_" + (i + 1));
            }

            // "Exit to lobby" NPC on each floor (at spawn point offset)
            if (floor.playerSpawn != null) {
                Location exitLoc = floor.playerSpawn.clone().add(-3, 0, 0);
                spawnFloorNPC(exitLoc,
                        "§e§lロビーへ戻る",
                        "§7右クリックで退出",
                        "exit");
            }
        }
    }

    private void spawnFloorNPC(Location loc, String name, String subtitle, String action) {
        World world = loc.getWorld();
        if (world == null) return;

        Villager villager = (Villager) world.spawnEntity(loc, EntityType.VILLAGER);
        villager.setCustomName(null);
        villager.setCustomNameVisible(false);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setProfession(Villager.Profession.NONE);
        villager.setVillagerType(Villager.Type.PLAINS);

        if ("exit".equals(action)) {
            // Exit NPC uses GROWTH_TELEPORT_KEY with "lobby"
            villager.getPersistentDataContainer().set(GROWTH_TELEPORT_KEY, PersistentDataType.STRING, "lobby");
            villager.getPersistentDataContainer().set(NPC_KEY, PersistentDataType.STRING, "");
        } else {
            // Floor navigation NPC
            villager.getPersistentDataContainer().set(FLOOR_ACTION_KEY, PersistentDataType.STRING, action);
            villager.getPersistentDataContainer().set(NPC_KEY, PersistentDataType.STRING, "");
        }

        spawnedNPCs.add(villager.getUniqueId());

        // Name label
        TextDisplay td = (TextDisplay) world.spawnEntity(
                loc.clone().add(0, 2.3, 0), EntityType.TEXT_DISPLAY);
        td.setText(name + "\n" + subtitle);
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setSeeThrough(false);
        td.setShadowed(true);
        td.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, "npc_label");
        spawnedHolograms.add(td.getUniqueId());

        logger.info("Floor NPC spawned: " + action + " at " + locToString(loc));
    }

    // ========== Hologram Management ==========

    private void spawnHolograms() {
        ConfigurationSection holoSection = framesConfig.getConfigurationSection("holograms");
        if (holoSection == null) return;

        // Welcome banner
        ConfigurationSection welcome = holoSection.getConfigurationSection("welcome");
        if (welcome != null) {
            Location loc = getLocationFromSection(welcome);
            if (loc != null) {
                TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
                td.setText(welcome.getString("text", "§6§lBUTAI RANK BATTLE"));
                td.setBillboard(Display.Billboard.CENTER);
                td.setAlignment(TextDisplay.TextAlignment.CENTER);
                td.setSeeThrough(false);
                td.setShadowed(true);
                td.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, "welcome");
                spawnedHolograms.add(td.getUniqueId());
                logger.info("Welcome hologram spawned.");
            }
        }

        // Ranking hologram
        ConfigurationSection ranking = holoSection.getConfigurationSection("ranking");
        if (ranking != null) {
            Location loc = getLocationFromSection(ranking);
            if (loc != null) {
                TextDisplay td = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
                td.setText("§6§l=== ランキング TOP10 ===\n§7読み込み中...");
                td.setBillboard(Display.Billboard.CENTER);
                td.setAlignment(TextDisplay.TextAlignment.LEFT);
                td.setLineWidth(400);
                td.setSeeThrough(false);
                td.setShadowed(true);
                td.setBackgroundColor(org.bukkit.Color.fromARGB(160, 0, 0, 0));
                td.getPersistentDataContainer().set(HOLOGRAM_KEY, PersistentDataType.STRING, "ranking");
                spawnedHolograms.add(td.getUniqueId());
                rankingDisplay = td;
                logger.info("Ranking hologram spawned.");
                // Initial update
                updateRankingHologram();
            }
        }
    }

    /**
     * Update ranking hologram with current TOP10 from DB.
     */
    private void updateRankingHologram() {
        if (rankingDisplay == null || rankingDisplay.isDead()) return;

        RankManager rm = plugin.getRankManager();
        List<Map<String, Object>> top = rm.getTopPlayers(10);

        StringBuilder sb = new StringBuilder();
        sb.append("§6§l═══ ランキング TOP10 ═══\n\n");

        if (top.isEmpty()) {
            sb.append("§7データがありません。");
        } else {
            for (int i = 0; i < top.size(); i++) {
                Map<String, Object> row = top.get(i);
                String name = (String) row.get("name");
                String rankStr = (String) row.get("rank_class");
                int totalRp = ((Number) row.get("total_rp")).intValue();

                com.butai.rankbattle.model.RankClass rc =
                        com.butai.rankbattle.model.RankClass.fromString(rankStr);

                String posColor = i == 0 ? "§6§l" : i <= 2 ? "§f" : "§7";
                String pos = String.format("%2d", i + 1);
                sb.append(posColor).append("#").append(pos).append(" ");
                sb.append(rc.getColor()).append(rc.getDisplayName()).append(" ");
                sb.append("§f").append(name);
                sb.append(" §8- §e").append(totalRp).append(" RP");
                sb.append("\n");
            }
        }

        rankingDisplay.setText(sb.toString());
    }

    // ========== Action Bar ==========

    private void startActionBarLoop() {
        if (!framesConfig.getBoolean("actionbar.enabled", true)) return;

        int interval = framesConfig.getInt("actionbar.update_interval", 40);
        String format = framesConfig.getString("actionbar.format",
                "§6ランク: {rank} §8| §bRP: §f{rp} §8| §7{status}");

        actionBarTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Skip players in matches (they have ether XP bar)
                    if (plugin.getEtherManager().isTracking(player.getUniqueId())) continue;

                    RankManager rm = plugin.getRankManager();
                    BRBPlayer data = rm.getPlayer(player.getUniqueId());
                    if (data == null) continue;

                    QueueManager qm = plugin.getQueueManager();
                    String status;
                    if (qm.isInMatch(player.getUniqueId())) {
                        status = "§c試合中";
                    } else if (qm.isInQueue(player.getUniqueId())) {
                        status = "§eキュー待ち中...";
                    } else {
                        status = "§a待機中";
                    }

                    String text = format
                            .replace("{rank}", data.getRankClass().getColoredName())
                            .replace("{rp}", String.valueOf(data.getTotalRP()))
                            .replace("{status}", status);

                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacy(text));
                }
            }
        }.runTaskTimer(plugin, 40L, interval);
    }

    private void startRankingUpdateLoop() {
        ConfigurationSection ranking = framesConfig.getConfigurationSection("holograms.ranking");
        if (ranking == null) return;

        int interval = ranking.getInt("update_interval", 600);

        rankingUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateRankingHologram();
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    // ========== Cleanup ==========

    private void removeSpawnedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (spawnedNPCs.contains(entity.getUniqueId())
                        || spawnedHolograms.contains(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
        spawnedNPCs.clear();
        spawnedHolograms.clear();
        rankingDisplay = null;
    }

    // ========== Utility ==========

    private Location getLocationFromSection(ConfigurationSection section) {
        String worldName = section.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world,
                section.getDouble("x"), section.getDouble("y"), section.getDouble("z"));
    }

    private String locToString(Location loc) {
        return String.format("(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ());
    }
}
