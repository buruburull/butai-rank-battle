package com.borderrank.battle;

import com.borderrank.battle.database.DatabaseManager;
import com.borderrank.battle.database.LoadoutDAO;
import com.borderrank.battle.database.MatchDAO;
import com.borderrank.battle.database.PlayerDAO;
import com.borderrank.battle.listener.BlockChangeListener;
import com.borderrank.battle.listener.ChatListener;
import com.borderrank.battle.listener.CombatListener;
import com.borderrank.battle.listener.PlayerConnectionListener;
import com.borderrank.battle.listener.ProjectileListener;
import com.borderrank.battle.listener.TriggerUseListener;
import com.borderrank.battle.model.MapData;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.LobbyManager;
import com.borderrank.battle.manager.MapManager;
import com.borderrank.battle.manager.QueueManager;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.manager.ScoreboardManager;
import com.borderrank.battle.manager.TrionManager;
import com.borderrank.battle.manager.TriggerRegistry;
import com.borderrank.battle.command.RankCommand;
import com.borderrank.battle.command.TriggerCommand;
import com.borderrank.battle.command.TeamCommand;
import com.borderrank.battle.command.AdminCommand;
import com.borderrank.battle.arena.MatchManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for Border Rank Battle.
 * Initializes all managers, commands, and listeners.
 */
public class BRBPlugin extends JavaPlugin {

    private static BRBPlugin instance;

    private DatabaseManager databaseManager;
    private TriggerRegistry triggerRegistry;
    private LoadoutManager loadoutManager;
    private TrionManager trionManager;
    private RankManager rankManager;
    private QueueManager queueManager;
    private MapManager mapManager;
    private ScoreboardManager scoreboardManager;
    private MatchManager matchManager;
    private MatchDAO matchDAO;
    private LobbyManager lobbyManager;
    private Location lobbyLocation;

    @Override
    public void onEnable() {
        instance = this;

        // Load configuration
        saveDefaultConfig();

        // Initialize database
        String mysqlHost = getConfig().getString("database.host", "localhost");
        int mysqlPort = getConfig().getInt("database.port", 3306);
        String mysqlDatabase = getConfig().getString("database.name", "brb_game");
        String mysqlUsername = getConfig().getString("database.user", "root");
        String mysqlPassword = getConfig().getString("database.password", "");

        databaseManager = new DatabaseManager(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword);
        try {
            databaseManager.init();
            getLogger().info("Database connected successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
        }

        // Initialize managers
        triggerRegistry = new TriggerRegistry(this);
        loadoutManager = new LoadoutManager(new LoadoutDAO(databaseManager));
        trionManager = new TrionManager();
        rankManager = new RankManager(new PlayerDAO(databaseManager));
        queueManager = new QueueManager();
        mapManager = new MapManager(this);
        // Load maps from the same triggers.yml that TriggerRegistry found
        if (triggerRegistry.getResolvedTriggersFile() != null) {
            mapManager.loadMaps(triggerRegistry.getResolvedTriggersFile());
        } else {
            getLogger().warning("TriggerRegistry did not resolve triggers.yml - maps not loaded.");
        }
        scoreboardManager = new ScoreboardManager();
        matchManager = new MatchManager();
        matchDAO = new MatchDAO(databaseManager);

        // Load lobby location from triggers.yml
        loadLobbyLocation();

        // Initialize lobby manager
        lobbyManager = new LobbyManager(this);

        // Register commands
        getCommand("rank").setExecutor(new RankCommand());
        getCommand("trigger").setExecutor(new TriggerCommand());
        getCommand("team").setExecutor(new TeamCommand());
        getCommand("bradmin").setExecutor(new AdminCommand());

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new TriggerUseListener(), this);
        getServer().getPluginManager().registerEvents(new ProjectileListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new BlockChangeListener(), this);
        getServer().getPluginManager().registerEvents(lobbyManager, this);

        // Start ticking tasks
        startTickingTasks();

        // Setup lobby (NPCs, holograms, actionbar) - delay 1 tick to ensure world is loaded
        getServer().getScheduler().scheduleSyncDelayedTask(this, () -> lobbyManager.setup(), 20);

        getLogger().info("Border Rank Battle plugin enabled!");
    }

    @Override
    public void onDisable() {
        // Clean up lobby entities
        if (lobbyManager != null) {
            lobbyManager.cleanup();
        }

        // Save all data and close database
        if (databaseManager != null) {
            databaseManager.close();
        }

        // Cancel all tasks
        getServer().getScheduler().cancelTasks(this);

        getLogger().info("Border Rank Battle plugin disabled!");
    }

    /**
     * Start the main ticking tasks.
     */
    private void startTickingTasks() {
        // Tick match manager every second
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (matchManager != null) {
                matchManager.tick();
            }
        }, 0, 20);

        // Check queue for matchmaking every 5 seconds
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (queueManager == null || matchManager == null || mapManager == null) return;

            // Try to form a solo match (minimum 2 players)
            java.util.Set<java.util.UUID> matched = queueManager.trySoloMatch(2);
            if (!matched.isEmpty()) {
                // Select an available map
                MapData mapData = mapManager.selectRandomMap();
                if (mapData == null) {
                    // No maps available - put players back in queue
                    getLogger().info("No maps available, keeping " + matched.size() + " players in queue.");
                    for (java.util.UUID uuid : matched) {
                        queueManager.addToSoloQueue(uuid);
                        org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                        if (player != null) {
                            com.borderrank.battle.util.MessageUtil.sendMessage(player,
                                org.bukkit.ChatColor.YELLOW + "全マップが使用中です。空きが出るまでお待ちください...");
                        }
                    }
                    return;
                }

                int matchId = matchManager.createSoloMatch(matched, mapData);
                if (matchId > 0) {
                    getLogger().info("Solo match #" + matchId + " created on map '" + mapData.getDisplayName() + "' with " + matched.size() + " players");
                    for (java.util.UUID uuid : matched) {
                        org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                        if (player != null) {
                            com.borderrank.battle.util.MessageUtil.sendSuccessMessage(player,
                                "マッチが見つかりました！マッチ #" + matchId + " | マップ: " + mapData.getDisplayName());
                        }
                    }
                } else {
                    // Match creation failed - release map
                    mapManager.releaseMap(mapData.getMapId());
                }
            }
        }, 100, 100); // Start after 5 sec, repeat every 5 sec

        // Check practice queue for matchmaking every 5 seconds
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (queueManager == null || matchManager == null || mapManager == null) return;

            java.util.Set<java.util.UUID> matched = queueManager.tryPracticeMatch(2);
            if (!matched.isEmpty()) {
                MapData mapData = mapManager.selectRandomMap();
                if (mapData == null) {
                    getLogger().info("No maps available for practice, keeping " + matched.size() + " players in queue.");
                    for (java.util.UUID uuid : matched) {
                        queueManager.addToPracticeQueue(uuid);
                        org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                        if (player != null) {
                            com.borderrank.battle.util.MessageUtil.sendMessage(player,
                                org.bukkit.ChatColor.YELLOW + "全マップが使用中です。空きが出るまでお待ちください...");
                        }
                    }
                    return;
                }

                int matchId = matchManager.createSoloMatch(matched, mapData, true);
                if (matchId > 0) {
                    getLogger().info("Practice match #" + matchId + " created on map '" + mapData.getDisplayName() + "' with " + matched.size() + " players");
                    for (java.util.UUID uuid : matched) {
                        org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                        if (player != null) {
                            com.borderrank.battle.util.MessageUtil.sendSuccessMessage(player,
                                "\u00a7b【練習マッチ】\u00a7a マッチが見つかりました！マッチ #" + matchId + " | マップ: " + mapData.getDisplayName());
                        }
                    }
                } else {
                    mapManager.releaseMap(mapData.getMapId());
                }
            }
        }, 100, 100);
    }

    /**
     * Load lobby location from triggers.yml.
     */
    private void loadLobbyLocation() {
        if (triggerRegistry.getResolvedTriggersFile() == null) {
            getLogger().warning("triggers.yml not found - using world spawn as lobby.");
            return;
        }

        org.bukkit.configuration.file.YamlConfiguration triggersConfig =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(triggerRegistry.getResolvedTriggersFile());

        String worldName = triggersConfig.getString("lobby.world", "world");
        String locationStr = triggersConfig.getString("lobby.location", "0,65,0");
        float yaw = (float) triggersConfig.getDouble("lobby.yaw", 0.0);
        float pitch = (float) triggersConfig.getDouble("lobby.pitch", 0.0);

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().warning("Lobby world '" + worldName + "' not found - using default world spawn.");
            return;
        }

        String[] parts = locationStr.split(",");
        if (parts.length >= 3) {
            try {
                double x = Double.parseDouble(parts[0].trim());
                double y = Double.parseDouble(parts[1].trim());
                double z = Double.parseDouble(parts[2].trim());
                lobbyLocation = new Location(world, x, y, z, yaw, pitch);
                getLogger().info("Lobby location loaded: " + worldName + " (" + x + ", " + y + ", " + z + ")");
            } catch (NumberFormatException e) {
                getLogger().warning("Invalid lobby location format: " + locationStr);
            }
        }
    }

    /**
     * Get the lobby location. Falls back to world spawn if not configured.
     */
    public Location getLobbyLocation() {
        if (lobbyLocation != null) {
            return lobbyLocation.clone();
        }
        World world = Bukkit.getWorld("world");
        return world != null ? world.getSpawnLocation() : null;
    }

    /**
     * Get the plugin instance.
     */
    public static BRBPlugin getInstance() {
        return instance;
    }

    /**
     * Get the database manager.
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Get the trigger registry.
     */
    public TriggerRegistry getTriggerRegistry() {
        return triggerRegistry;
    }

    /**
     * Get the loadout manager.
     */
    public LoadoutManager getLoadoutManager() {
        return loadoutManager;
    }

    /**
     * Get the trion manager.
     */
    public TrionManager getTrionManager() {
        return trionManager;
    }

    /**
     * Get the rank manager.
     */
    public RankManager getRankManager() {
        return rankManager;
    }

    /**
     * Get the queue manager.
     */
    public QueueManager getQueueManager() {
        return queueManager;
    }

    /**
     * Get the map manager.
     */
    public MapManager getMapManager() {
        return mapManager;
    }

    /**
     * Get the scoreboard manager.
     */
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    /**
     * Get the match manager.
     */
    public MatchManager getMatchManager() {
        return matchManager;
    }

    /**
     * Get the match DAO for match history persistence.
     */
    public MatchDAO getMatchDAO() {
        return matchDAO;
    }

    /**
     * Get the lobby manager.
     */
    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }
}
