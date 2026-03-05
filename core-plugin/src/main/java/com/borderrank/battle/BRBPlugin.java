package com.borderrank.battle;

import com.borderrank.battle.database.DatabaseManager;
import com.borderrank.battle.database.LoadoutDAO;
import com.borderrank.battle.database.PlayerDAO;
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
        saveDefaultConfig();

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

        triggerRegistry = new TriggerRegistry(this);
        loadoutManager = new LoadoutManager(new LoadoutDAO(databaseManager));
        trionManager = new TrionManager();
        rankManager = new RankManager(new PlayerDAO(databaseManager));
        queueManager = new QueueManager();
        mapManager = new MapManager(this);
        scoreboardManager = new ScoreboardManager();
        matchManager = new MatchManager();

        getCommand("rank").setExecutor(new RankCommand());
        getCommand("trigger").setExecutor(new TriggerCommand());
        getCommand("team").setExecutor(new TeamCommand());
        getCommand("bradmin").setExecutor(new AdminCommand());

        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new TriggerUseListener(), this);

        startTickingTasks();
        getLogger().info("Border Rank Battle plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("Border Rank Battle plugin disabled!");
    }

    private void startTickingTasks() {
        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (matchManager != null) matchManager.tick();
        }, 0, 20);

        getServer().getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (queueManager == null || matchManager == null) return;

            // Solo queue check
            java.util.Set<java.util.UUID> matched = queueManager.trySoloMatch(2);
            if (!matched.isEmpty()) {
                int matchId = matchManager.createSoloMatch(matched, "arena_default");
                if (matchId > 0) {
                    getLogger().info("Solo match #" + matchId + " created with " + matched.size() + " players");
                    for (java.util.UUID uuid : matched) {
                        org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                        if (player != null) {
                            com.borderrank.battle.util.MessageUtil.sendSuccessMessage(player, "ソロマッチが見つかりました！マッチ #" + matchId);
                        }
                    }
                }
            }

            // Team queue check
            java.util.Set<Integer> matchedTeams = queueManager.tryTeamMatch(2);
            if (!matchedTeams.isEmpty()) {
                java.util.Map<Integer, java.util.Set<java.util.UUID>> teamData = new java.util.HashMap<>();
                int teamNum = 1;
                for (int teamId : matchedTeams) {
                    teamData.put(teamNum, queueManager.getTeamMembers(teamId));
                    teamNum++;
                }
                int matchId = matchManager.createTeamMatch(teamData, "arena_default");
                if (matchId > 0) {
                    getLogger().info("Team match #" + matchId + " created");
                    for (java.util.Set<java.util.UUID> members : teamData.values()) {
                        for (java.util.UUID uuid : members) {
                            org.bukkit.entity.Player player = getServer().getPlayer(uuid);
                            if (player != null) {
                                com.borderrank.battle.util.MessageUtil.sendSuccessMessage(player, "チームマッチが見つかりました！マッチ #" + matchId);
                            }
                        }
                    }
                }
            }
        }, 100, 100);
    }

    public static BRBPlugin getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public TriggerRegistry getTriggerRegistry() { return triggerRegistry; }
    public LoadoutManager getLoadoutManager() { return loadoutManager; }
    public TrionManager getTrionManager() { return trionManager; }
    public RankManager getRankManager() { return rankManager; }
    public QueueManager getQueueManager() { return queueManager; }
    public MapManager getMapManager() { return mapManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public MatchManager getMatchManager() { return matchManager; }
}
