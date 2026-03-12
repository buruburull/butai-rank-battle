package com.butai.rankbattle.manager;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.arena.ArenaInstance;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Manages matchmaking queues for solo ranked, team ranked, and practice matches.
 * Checks queue every 5 seconds (100 ticks) for matchmaking.
 */
public class QueueManager {

    private final BRBPlugin plugin;
    private final FrameSetManager frameSetManager;
    private final Logger logger;

    // Solo ranked queue
    private final Queue<UUID> soloQueue = new ConcurrentLinkedQueue<>();
    // Practice queue
    private final Queue<UUID> practiceQueue = new ConcurrentLinkedQueue<>();

    // Active matches
    private final Map<Integer, ArenaInstance> activeMatches = new HashMap<>();
    // Player -> matchId mapping
    private final Map<UUID, Integer> playerMatchMap = new HashMap<>();

    // Queue checker task
    private BukkitTask queueCheckerTask;

    public QueueManager(BRBPlugin plugin, FrameSetManager frameSetManager, Logger logger) {
        this.plugin = plugin;
        this.frameSetManager = frameSetManager;
        this.logger = logger;
    }

    /**
     * Start the queue checker loop (every 100 ticks = 5 seconds).
     */
    public void startQueueChecker() {
        if (queueCheckerTask != null) return;

        queueCheckerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkQueues();
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    /**
     * Stop the queue checker.
     */
    public void stopQueueChecker() {
        if (queueCheckerTask != null) {
            queueCheckerTask.cancel();
            queueCheckerTask = null;
        }
    }

    /**
     * Add a player to the solo ranked queue.
     * Returns null on success, or an error message.
     */
    public String joinSoloQueue(UUID uuid) {
        if (isInQueue(uuid)) {
            return "既にキューに参加しています。";
        }
        if (isInMatch(uuid)) {
            return "現在試合中のためキューに参加できません。";
        }

        // Validate frameset
        String validation = frameSetManager.validateForQueue(uuid);
        if (validation != null) {
            return validation;
        }

        soloQueue.add(uuid);
        return null;
    }

    /**
     * Add a player to the practice queue.
     */
    public String joinPracticeQueue(UUID uuid) {
        if (isInQueue(uuid)) {
            return "既にキューに参加しています。";
        }
        if (isInMatch(uuid)) {
            return "現在試合中のためキューに参加できません。";
        }

        String validation = frameSetManager.validateForQueue(uuid);
        if (validation != null) {
            return validation;
        }

        practiceQueue.add(uuid);
        return null;
    }

    /**
     * Remove a player from all queues.
     * Returns true if the player was in a queue.
     */
    public boolean leaveQueue(UUID uuid) {
        boolean removed = soloQueue.remove(uuid);
        removed |= practiceQueue.remove(uuid);
        return removed;
    }

    /**
     * Check if a player is in any queue.
     */
    public boolean isInQueue(UUID uuid) {
        return soloQueue.contains(uuid) || practiceQueue.contains(uuid);
    }

    /**
     * Check if a player is in an active match.
     */
    public boolean isInMatch(UUID uuid) {
        return playerMatchMap.containsKey(uuid);
    }

    /**
     * Get the match a player is in, or null.
     */
    public ArenaInstance getPlayerMatch(UUID uuid) {
        Integer matchId = playerMatchMap.get(uuid);
        if (matchId == null) return null;
        return activeMatches.get(matchId);
    }

    /**
     * Check all queues and create matches when enough players are queued.
     */
    private void checkQueues() {
        // Solo ranked: need 2 players
        while (soloQueue.size() >= 2) {
            UUID p1 = soloQueue.poll();
            UUID p2 = soloQueue.poll();

            // Verify both players are still online
            if (!isOnline(p1)) { if (p2 != null) soloQueue.add(p2); continue; }
            if (!isOnline(p2)) { soloQueue.add(p1); continue; }

            createMatch(p1, p2, ArenaInstance.MatchType.SOLO_RANKED);
        }

        // Practice: need 2 players
        while (practiceQueue.size() >= 2) {
            UUID p1 = practiceQueue.poll();
            UUID p2 = practiceQueue.poll();

            if (!isOnline(p1)) { if (p2 != null) practiceQueue.add(p2); continue; }
            if (!isOnline(p2)) { practiceQueue.add(p1); continue; }

            createMatch(p1, p2, ArenaInstance.MatchType.PRACTICE);
        }
    }

    /**
     * Create a new match between two players.
     */
    private void createMatch(UUID p1, UUID p2, ArenaInstance.MatchType type) {
        ArenaInstance match = new ArenaInstance(plugin, type);
        match.addPlayer(p1);
        match.addPlayer(p2);

        // Set spawn locations using world spawn with offsets
        Location lobbyLoc = plugin.getEtherManager().getMaxEther() > 0
                ? getArenaSpawnLocations(p1)
                : null;

        // Get arena spawn points (use world spawn area with offsets for now)
        Player player1 = Bukkit.getPlayer(p1);
        if (player1 != null) {
            World world = player1.getWorld();
            Location center = world.getSpawnLocation();
            // Spawn players 30 blocks apart
            Location s1 = center.clone().add(15, 0, 0);
            s1.setY(world.getHighestBlockYAt(s1.getBlockX(), s1.getBlockZ()) + 1);
            Location s2 = center.clone().add(-15, 0, 0);
            s2.setY(world.getHighestBlockYAt(s2.getBlockX(), s2.getBlockZ()) + 1);
            match.setSpawnLocations(s1, s2);
        }

        // Set lobby location
        Location lobby = plugin.getEtherManager() != null ? null : null;
        // Use config lobby location
        String lobbyWorld = plugin.getConfig().getString("lobby.world", "world");
        World lw = Bukkit.getWorld(lobbyWorld);
        if (lw != null) {
            double lx = plugin.getConfig().getDouble("lobby.x", lw.getSpawnLocation().getX());
            double ly = plugin.getConfig().getDouble("lobby.y", lw.getSpawnLocation().getY());
            double lz = plugin.getConfig().getDouble("lobby.z", lw.getSpawnLocation().getZ());
            match.setLobbyLocation(new Location(lw, lx, ly, lz));
        }

        // Register match
        activeMatches.put(match.getMatchId(), match);
        playerMatchMap.put(p1, match.getMatchId());
        playerMatchMap.put(p2, match.getMatchId());

        // Set cleanup callback
        match.setEndCallback(m -> {
            activeMatches.remove(m.getMatchId());
            for (UUID uuid : m.getPlayers()) {
                playerMatchMap.remove(uuid);
            }
        });

        // Start countdown
        match.startCountdown();

        String typeName = type == ArenaInstance.MatchType.PRACTICE ? "プラクティス" : "ランク";
        logger.info(typeName + "マッチ #" + match.getMatchId() + " created: "
                + p1.toString().substring(0, 8) + " vs " + p2.toString().substring(0, 8));
    }

    /**
     * Handle player disconnect during match.
     */
    public void handleDisconnect(UUID uuid) {
        // Remove from queues
        leaveQueue(uuid);

        // Handle match disconnect
        ArenaInstance match = getPlayerMatch(uuid);
        if (match != null && match.getState() == ArenaInstance.MatchState.ACTIVE) {
            match.onPlayerEliminated(uuid);
        }
    }

    /**
     * Get the queue position for a player (1-based), or 0 if not in queue.
     */
    public int getQueuePosition(UUID uuid) {
        int pos = 1;
        for (UUID queuedUuid : soloQueue) {
            if (queuedUuid.equals(uuid)) return pos;
            pos++;
        }
        pos = 1;
        for (UUID queuedUuid : practiceQueue) {
            if (queuedUuid.equals(uuid)) return pos;
            pos++;
        }
        return 0;
    }

    /**
     * Get total players in solo queue.
     */
    public int getSoloQueueSize() {
        return soloQueue.size();
    }

    /**
     * Get total active matches.
     */
    public int getActiveMatchCount() {
        return activeMatches.size();
    }

    private boolean isOnline(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null && p.isOnline();
    }

    private Location getArenaSpawnLocations(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getWorld().getSpawnLocation() : null;
    }
}
