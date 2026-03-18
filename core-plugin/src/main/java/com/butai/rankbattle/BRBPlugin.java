package com.butai.rankbattle;

import com.butai.rankbattle.database.DatabaseManager;
import com.butai.rankbattle.database.EtherGrowthDAO;
import com.butai.rankbattle.database.FrameSetDAO;
import com.butai.rankbattle.database.MatchHistoryDAO;
import com.butai.rankbattle.database.PlayerDAO;
import com.butai.rankbattle.database.SeasonDAO;
import com.butai.rankbattle.database.TeamDAO;
import com.butai.rankbattle.command.AdminCommand;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.command.RankCommand;
import com.butai.rankbattle.command.TeamCommand;
import com.butai.rankbattle.listener.ChatTabListener;
import com.butai.rankbattle.listener.CombatListener;
import com.butai.rankbattle.listener.BlockChangeListener;
import com.butai.rankbattle.listener.EtherGrowthListener;
import com.butai.rankbattle.listener.FrameEffectListener;
import com.butai.rankbattle.listener.LobbyListener;
import com.butai.rankbattle.listener.PlayerConnectionListener;
import com.butai.rankbattle.manager.EtherGrowthManager;
import com.butai.rankbattle.manager.EtherManager;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.manager.LobbyManager;
import com.butai.rankbattle.manager.MineManager;
import com.butai.rankbattle.manager.QueueManager;
import com.butai.rankbattle.manager.RankManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

import java.util.logging.Logger;

public class BRBPlugin extends JavaPlugin {

    private static BRBPlugin instance;
    private Logger log;

    private DatabaseManager databaseManager;
    private PlayerDAO playerDAO;
    private FrameSetDAO frameSetDAO;
    private RankManager rankManager;
    private FrameRegistry frameRegistry;
    private FrameSetManager frameSetManager;
    private EtherManager etherManager;
    private QueueManager queueManager;
    private LobbyManager lobbyManager;
    private MatchHistoryDAO matchHistoryDAO;
    private FrameCommand frameCommand;
    private FrameEffectListener frameEffectListener;
    private ChatTabListener chatTabListener;
    private EtherGrowthManager etherGrowthManager;
    private MineManager mineManager;

    public static BRBPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        log = getLogger();

        log.info("=== BUTAI Rank Battle v" + getDescription().getVersion() + " ===");
        log.info("BRB プラグインを起動しています...");

        // Save default config
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        // Initialize database
        databaseManager = new DatabaseManager(log);
        databaseManager.initialize(
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 3306),
                config.getString("database.name", "brb"),
                config.getString("database.username", "root"),
                config.getString("database.password", "")
        );

        // Initialize DAOs
        playerDAO = new PlayerDAO(databaseManager, log);
        frameSetDAO = new FrameSetDAO(databaseManager, log);
        matchHistoryDAO = new MatchHistoryDAO(databaseManager, log);

        // Initialize managers
        TeamDAO teamDAO = new TeamDAO(databaseManager, log);
        rankManager = new RankManager(playerDAO, log);
        rankManager.setTeamDAO(teamDAO);

        // Load frame registry (save default frames.yml to plugin data folder)
        if (!new java.io.File(getDataFolder(), "frames.yml").exists()) {
            saveResource("frames.yml", false);
        }
        frameRegistry = new FrameRegistry(log);
        frameRegistry.loadFromFile(new java.io.File(getDataFolder(), "frames.yml"));

        // Initialize FrameSetManager
        frameSetManager = new FrameSetManager(frameRegistry, frameSetDAO, log);

        // Initialize EtherManager
        etherManager = new EtherManager(this, log);
        // Set lobby location from frames.yml
        File framesFile = new File(getDataFolder(), "frames.yml");
        YamlConfiguration framesConfig = YamlConfiguration.loadConfiguration(framesFile);
        String lobbyWorld = framesConfig.getString("lobby.world", "world");
        World world = getServer().getWorld(lobbyWorld);
        if (world != null) {
            double lx = framesConfig.getDouble("lobby.x", world.getSpawnLocation().getX());
            double ly = framesConfig.getDouble("lobby.y", world.getSpawnLocation().getY());
            double lz = framesConfig.getDouble("lobby.z", world.getSpawnLocation().getZ());
            etherManager.setLobbyLocation(new Location(world, lx, ly, lz));
        }
        // Tick loop will be started per-match (not globally at startup)

        // Initialize QueueManager
        queueManager = new QueueManager(this, frameSetManager, log);
        queueManager.startQueueChecker();

        // Register commands
        frameCommand = new FrameCommand(frameRegistry, frameSetManager);
        com.butai.rankbattle.gui.FrameSetGUI frameSetGUI =
                new com.butai.rankbattle.gui.FrameSetGUI(frameRegistry, frameSetManager);
        frameCommand.setFrameSetGUI(frameSetGUI);
        PluginCommand frameCmdObj = getCommand("frame");
        if (frameCmdObj != null) {
            frameCmdObj.setExecutor(frameCommand);
            frameCmdObj.setTabCompleter(frameCommand);
        }

        RankCommand rankCommand = new RankCommand(queueManager, rankManager, matchHistoryDAO);
        PluginCommand rankCmdObj = getCommand("rank");
        if (rankCmdObj != null) {
            rankCmdObj.setExecutor(rankCommand);
            rankCmdObj.setTabCompleter(rankCommand);
        }

        TeamCommand teamCommand = new TeamCommand(rankManager);
        PluginCommand teamCmdObj = getCommand("team");
        if (teamCmdObj != null) {
            teamCmdObj.setExecutor(teamCommand);
            teamCmdObj.setTabCompleter(teamCommand);
        }

        // Initialize Ether Growth System
        EtherGrowthDAO etherGrowthDAO = new EtherGrowthDAO(databaseManager, log);
        etherGrowthDAO.createTableIfNotExists();
        SeasonDAO seasonDAO = new SeasonDAO(databaseManager, log);

        mineManager = new MineManager(this, log);
        mineManager.loadConfig(framesConfig);

        etherGrowthManager = new EtherGrowthManager(this, log, etherGrowthDAO, seasonDAO);
        etherGrowthManager.loadConfig(framesConfig);
        etherGrowthManager.startMobSpawner();

        // Place ores in mine zone
        mineManager.placeAllOres();

        AdminCommand adminCommand = new AdminCommand(rankManager, queueManager, frameRegistry);
        adminCommand.setSeasonDAO(seasonDAO);
        PluginCommand adminCmdObj = getCommand("bradmin");
        if (adminCmdObj != null) {
            adminCmdObj.setExecutor(adminCommand);
            adminCmdObj.setTabCompleter(adminCommand);
        }

        // Initialize LobbyManager (NPCs, holograms, action bar)
        lobbyManager = new LobbyManager(this, log);
        lobbyManager.initialize();

        // Register listeners (after commands, so frameCommand is available)
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(this, rankManager, frameSetManager, frameCommand), this);
        CombatListener combatListener = new CombatListener(etherManager, frameRegistry, queueManager, log);
        frameEffectListener = new FrameEffectListener(etherManager, frameRegistry, queueManager, log);
        combatListener.setFrameEffectListener(frameEffectListener);
        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(frameEffectListener, this);
        getServer().getPluginManager().registerEvents(new LobbyListener(), this);
        getServer().getPluginManager().registerEvents(
                new com.butai.rankbattle.gui.FrameSetGUIListener(
                        frameSetGUI, frameSetManager, frameRegistry, frameCommand), this);
        getServer().getPluginManager().registerEvents(new BlockChangeListener(queueManager), this);
        getServer().getPluginManager().registerEvents(
                new EtherGrowthListener(etherGrowthManager, mineManager), this);
        chatTabListener = new ChatTabListener(rankManager);
        getServer().getPluginManager().registerEvents(chatTabListener, this);

        log.info("BRB プラグインが正常に起動しました！");
    }

    @Override
    public void onDisable() {
        log.info("BRB プラグインを停止しています...");

        // Shutdown lobby (remove NPCs, holograms, stop tasks)
        if (lobbyManager != null) {
            lobbyManager.shutdown();
        }

        // Stop queue checker
        if (queueManager != null) {
            queueManager.stopQueueChecker();
        }

        // Save and stop ether growth
        if (etherGrowthManager != null) {
            etherGrowthManager.saveAll();
            etherGrowthManager.stopMobSpawner();
        }

        // Stop ether tick loop
        if (etherManager != null) {
            etherManager.stopTickLoop();
        }

        // Close database
        if (databaseManager != null) {
            databaseManager.close();
        }

        log.info("BRB プラグインが停止しました。");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerDAO getPlayerDAO() {
        return playerDAO;
    }

    public FrameSetDAO getFrameSetDAO() {
        return frameSetDAO;
    }

    public RankManager getRankManager() {
        return rankManager;
    }

    public FrameRegistry getFrameRegistry() {
        return frameRegistry;
    }

    public FrameSetManager getFrameSetManager() {
        return frameSetManager;
    }

    public EtherManager getEtherManager() {
        return etherManager;
    }

    public QueueManager getQueueManager() {
        return queueManager;
    }

    public LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    public FrameCommand getFrameCommand() {
        return frameCommand;
    }

    public FrameEffectListener getFrameEffectListener() {
        return frameEffectListener;
    }

    public ChatTabListener getChatTabListener() {
        return chatTabListener;
    }

    public MatchHistoryDAO getMatchHistoryDAO() {
        return matchHistoryDAO;
    }

    public EtherGrowthManager getEtherGrowthManager() {
        return etherGrowthManager;
    }

    public MineManager getMineManager() {
        return mineManager;
    }
}
