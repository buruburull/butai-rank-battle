package com.borderrank.battle;

import com.borderrank.battle.database.DatabaseManager;
import com.borderrank.battle.listener.CombatListener;
import com.borderrank.battle.listener.PlayerConnectionListener;
import com.borderrank.battle.listener.TriggerUseListener;
import com.borderrank.battle.manager.LoadoutManager;
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

    @Override
    public void onEnable() {
        instance = this;
        
        // Load configuration
        saveDefaultConfig();
        
        // Initialize database
        String mysqlHost = getConfig().getString("mysql.host", "localhost");
        int mysqlPort = getConfig().getInt("mysql.port", 3306);
        String mysqlDatabase = getConfig().getString("mysql.database", "brb");
        String mysqlUsername = getConfig().getString("mysql.username", "root");
        String mysqlPassword = getConfig().getString("mysql.password", "");
        
        databaseManager = new DatabaseManager(mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword);
        
        // Initialize managers
        triggerRegistry = new TriggerRegistry(this);
        loadoutManager = new LoadoutManager(databaseManager);
        trionManager = new TrionManager();
        rankManager = new RankManager(databaseManager);
        queueManager = new QueueManager();
        mapManager = new MapManager(this);
        scoreboardManager = new ScoreboardManager();
        matchManager = new MatchManager();
        
        // Register commands
        getCommand("rank").setExecutor(new RankCommand());
        getCommand("trigger").setExecutor(new TriggerCommand());
        getCommand("team").setExecutor(new TeamCommand());
        getCommand("bradmin").setExecutor(new AdminCommand());
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new TriggerUseListener(), this);
        
        // Start ticking tasks
        startTickingTasks();
        
        getLogger().info("Border Rank Battle plugin enabled!");
    }

    @Override
    public void onDisable() {
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
        // Tick match manager every tick (20 ticks/second = 1 second)
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (matchManager != null) {
                matchManager.tick();
            }
        }, 0, 20);
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
}
