package com.butai.rankbattle;

import com.butai.rankbattle.database.DatabaseManager;
import com.butai.rankbattle.database.FrameSetDAO;
import com.butai.rankbattle.database.PlayerDAO;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.command.RankCommand;
import com.butai.rankbattle.listener.CombatListener;
import com.butai.rankbattle.listener.PlayerConnectionListener;
import com.butai.rankbattle.manager.EtherManager;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.manager.QueueManager;
import com.butai.rankbattle.manager.RankManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

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

        // Initialize managers
        rankManager = new RankManager(playerDAO, log);

        // Load frame registry (save default frames.yml to plugin data folder)
        saveResource("frames.yml", false);
        frameRegistry = new FrameRegistry(log);
        frameRegistry.loadFromFile(new java.io.File(getDataFolder(), "frames.yml"));

        // Initialize FrameSetManager
        frameSetManager = new FrameSetManager(frameRegistry, frameSetDAO, log);

        // Initialize EtherManager
        etherManager = new EtherManager(this, log);
        // Set lobby location from config or world spawn
        String lobbyWorld = config.getString("lobby.world", "world");
        World world = getServer().getWorld(lobbyWorld);
        if (world != null) {
            double lx = config.getDouble("lobby.x", world.getSpawnLocation().getX());
            double ly = config.getDouble("lobby.y", world.getSpawnLocation().getY());
            double lz = config.getDouble("lobby.z", world.getSpawnLocation().getZ());
            etherManager.setLobbyLocation(new Location(world, lx, ly, lz));
        }
        // Tick loop will be started per-match (not globally at startup)

        // Initialize QueueManager
        queueManager = new QueueManager(this, frameSetManager, log);
        queueManager.startQueueChecker();

        // Register commands
        FrameCommand frameCommand = new FrameCommand(frameRegistry, frameSetManager);
        PluginCommand frameCmdObj = getCommand("frame");
        if (frameCmdObj != null) {
            frameCmdObj.setExecutor(frameCommand);
            frameCmdObj.setTabCompleter(frameCommand);
        }

        RankCommand rankCommand = new RankCommand(queueManager);
        PluginCommand rankCmdObj = getCommand("rank");
        if (rankCmdObj != null) {
            rankCmdObj.setExecutor(rankCommand);
            rankCmdObj.setTabCompleter(rankCommand);
        }

        // Register listeners (after commands, so frameCommand is available)
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(this, rankManager, frameSetManager, frameCommand), this);
        getServer().getPluginManager().registerEvents(
                new CombatListener(etherManager, frameRegistry, queueManager, log), this);

        log.info("BRB プラグインが正常に起動しました！");
    }

    @Override
    public void onDisable() {
        log.info("BRB プラグインを停止しています...");

        // Stop queue checker
        if (queueManager != null) {
            queueManager.stopQueueChecker();
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
}
