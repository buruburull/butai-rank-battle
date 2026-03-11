package com.butai.rankbattle;

import com.butai.rankbattle.database.DatabaseManager;
import com.butai.rankbattle.database.FrameSetDAO;
import com.butai.rankbattle.database.PlayerDAO;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.listener.PlayerConnectionListener;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.manager.RankManager;
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

        // Load frame registry
        frameRegistry = new FrameRegistry(log);
        frameRegistry.loadFromFile(new java.io.File(getDataFolder().getParentFile().getParentFile(), "config/frames.yml"));

        // Initialize FrameSetManager
        frameSetManager = new FrameSetManager(frameRegistry, frameSetDAO, log);

        // TODO: EtherManager, QueueManager, etc.

        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this, rankManager, frameSetManager), this);

        // Register commands
        FrameCommand frameCommand = new FrameCommand(frameRegistry, frameSetManager);
        PluginCommand frameCmdObj = getCommand("frame");
        if (frameCmdObj != null) {
            frameCmdObj.setExecutor(frameCommand);
            frameCmdObj.setTabCompleter(frameCommand);
        }

        log.info("BRB プラグインが正常に起動しました！");
    }

    @Override
    public void onDisable() {
        log.info("BRB プラグインを停止しています...");

        // TODO: 進行中マッチの終了処理

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
}
