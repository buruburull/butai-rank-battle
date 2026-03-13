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

import com.butai.rankbattle.model.Team;

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
    // Team ranked queue (stores leader UUID, team data fetched from RankManager)
    private final Queue<UUID> teamQueue = new ConcurrentLinkedQueue<>();
    // Practice queue
    private final Queue<UUID> practiceQueue = new ConcurrentLinkedQueue<>();

    // Active matches
    private final Map<Integer, ArenaInstance> activeMatches = new HashMap<>();
    // Player -> matchId mapping
    private final Map<UUID, Integer> playerMatchMap = new HashMap<>();
    // Spectator -> matchId mapping
    private final Map<UUID, Integer> spectatorMatchMap = new HashMap<>();

    // Queue checker task
    private BukkitTask queueCheckerTask;

    // Disconnect tracking and penalties
    private final DisconnectTracker disconnectTracker;

    public QueueManager(BRBPlugin plugin, FrameSetManager frameSetManager, Logger logger) {
        this.plugin = plugin;
        this.frameSetManager = frameSetManager;
        this.logger = logger;
        this.disconnectTracker = new DisconnectTracker(logger);
    }

    public DisconnectTracker getDisconnectTracker() {
        return disconnectTracker;
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

        // Check disconnect penalty
        String penalty = disconnectTracker.getPenaltyMessage(uuid);
        if (penalty != null) {
            return penalty;
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
     * Add a team to the team ranked queue.
     * Only the team leader can queue. All members must be online and not in queue/match.
     * Returns null on success, or an error message.
     */
    public String joinTeamQueue(UUID leaderUuid) {
        RankManager rankManager = plugin.getRankManager();
        Team team = rankManager.getTeam(leaderUuid);
        if (team == null) {
            return "チームに所属していません。";
        }
        if (!team.isLeader(leaderUuid)) {
            return "チームリーダーのみキューに参加できます。";
        }
        if (team.getMemberCount() < 2) {
            return "チームメンバーが2人以上必要です。";
        }

        // Check disconnect penalty for all members
        for (UUID member : team.getMembers()) {
            String penalty = disconnectTracker.getPenaltyMessage(member);
            if (penalty != null) {
                Player p = Bukkit.getPlayer(member);
                String name = p != null ? p.getName() : member.toString().substring(0, 8);
                return name + ": " + penalty;
            }
        }

        // Check all members
        for (UUID member : team.getMembers()) {
            if (!isOnline(member)) {
                Player p = Bukkit.getPlayer(member);
                String name = p != null ? p.getName() : member.toString().substring(0, 8);
                return "メンバー " + name + " がオフラインです。";
            }
            if (isInQueue(member)) {
                return "チームメンバーが既にキューに参加しています。";
            }
            if (isInMatch(member)) {
                return "チームメンバーが試合中です。";
            }
            // Validate frameset for each member
            String validation = frameSetManager.validateForQueue(member);
            if (validation != null) {
                Player p = Bukkit.getPlayer(member);
                String name = p != null ? p.getName() : member.toString().substring(0, 8);
                return name + ": " + validation;
            }
        }

        if (teamQueue.contains(leaderUuid)) {
            return "既にチームキューに参加しています。";
        }

        teamQueue.add(leaderUuid);
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
        removed |= teamQueue.remove(uuid);
        removed |= practiceQueue.remove(uuid);

        // Also check if this player's team leader is in team queue
        RankManager rankManager = plugin.getRankManager();
        Team team = rankManager.getTeam(uuid);
        if (team != null && teamQueue.remove(team.getLeaderId())) {
            removed = true;
        }

        return removed;
    }

    /**
     * Check if a player is in any queue.
     */
    public boolean isInQueue(UUID uuid) {
        if (soloQueue.contains(uuid) || practiceQueue.contains(uuid) || teamQueue.contains(uuid)) {
            return true;
        }
        // Check if member's team leader is in team queue
        RankManager rankManager = plugin.getRankManager();
        Team team = rankManager.getTeam(uuid);
        return team != null && teamQueue.contains(team.getLeaderId());
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

        // Team ranked: need 2 teams
        while (teamQueue.size() >= 2) {
            UUID leader1 = teamQueue.poll();
            UUID leader2 = teamQueue.poll();

            RankManager rm = plugin.getRankManager();
            Team team1 = rm.getTeam(leader1);
            Team team2 = rm.getTeam(leader2);

            // Verify teams are still valid and all members online
            if (team1 == null || !allMembersOnline(team1)) {
                if (team2 != null) teamQueue.add(leader2);
                continue;
            }
            if (team2 == null || !allMembersOnline(team2)) {
                teamQueue.add(leader1);
                continue;
            }

            createTeamMatch(team1, team2);
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
            for (UUID uuid : m.getSpectators()) {
                spectatorMatchMap.remove(uuid);
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
     * - Removes from queues
     * - Eliminates from active match (E-Shift)
     * - Tracks disconnect for penalty (ranked matches only, not practice)
     * Returns the penalty message if applicable, or null.
     */
    public String handleDisconnect(UUID uuid) {
        // Remove from queues
        leaveQueue(uuid);

        // Handle match disconnect (ACTIVE or SUDDEN_DEATH)
        ArenaInstance match = getPlayerMatch(uuid);
        if (match != null && (match.getState() == ArenaInstance.MatchState.ACTIVE
                || match.getState() == ArenaInstance.MatchState.SUDDEN_DEATH)) {

            // Broadcast disconnect message
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : uuid.toString().substring(0, 8);
            match.broadcast("§c§l✖ " + name + " §7が切断しました！(E-Shift扱い)");

            match.onPlayerEliminated(uuid);

            // Track disconnect penalty (practice matches excluded)
            if (match.getMatchType() != ArenaInstance.MatchType.PRACTICE) {
                return disconnectTracker.recordDisconnect(uuid);
            }
        }
        return null;
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

    /**
     * Create a new team match between two teams.
     */
    private void createTeamMatch(Team team1, Team team2) {
        ArenaInstance match = new ArenaInstance(plugin, ArenaInstance.MatchType.TEAM_RANKED);
        match.addTeams(team1.getMembers(), team2.getMembers());

        // Get arena spawn points
        Player anyPlayer = Bukkit.getPlayer(team1.getLeaderId());
        if (anyPlayer != null) {
            World world = anyPlayer.getWorld();
            Location center = world.getSpawnLocation();
            // Spawn teams 30 blocks apart
            Location s1 = center.clone().add(15, 0, 0);
            s1.setY(world.getHighestBlockYAt(s1.getBlockX(), s1.getBlockZ()) + 1);
            Location s2 = center.clone().add(-15, 0, 0);
            s2.setY(world.getHighestBlockYAt(s2.getBlockX(), s2.getBlockZ()) + 1);
            match.setSpawnLocations(s1, s2);
        }

        // Set lobby location
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
        for (UUID uuid : match.getPlayers()) {
            playerMatchMap.put(uuid, match.getMatchId());
        }

        // Set cleanup callback
        match.setEndCallback(m -> {
            activeMatches.remove(m.getMatchId());
            for (UUID uuid : m.getPlayers()) {
                playerMatchMap.remove(uuid);
            }
            for (UUID uuid : m.getSpectators()) {
                spectatorMatchMap.remove(uuid);
            }
        });

        // Start countdown
        match.startCountdown();

        logger.info("チームランクマッチ #" + match.getMatchId() + " created: "
                + team1.getName() + " vs " + team2.getName());
    }

    /**
     * Get team queue size.
     */
    public int getTeamQueueSize() {
        return teamQueue.size();
    }

    /**
     * Add a player as spectator to a match.
     * Returns null on success, or an error message.
     */
    public String joinSpectate(UUID uuid, Integer matchId) {
        if (isInQueue(uuid)) {
            return "キュー待機中は観戦できません。";
        }
        if (isInMatch(uuid)) {
            return "試合中は観戦できません。";
        }
        if (isSpectating(uuid)) {
            return "既に観戦中です。/rank spectate leave で退出してください。";
        }

        ArenaInstance match;
        if (matchId != null) {
            match = activeMatches.get(matchId);
            if (match == null) {
                return "マッチ #" + matchId + " が見つかりません。";
            }
        } else {
            // Auto-select: find any active match
            match = activeMatches.values().stream()
                    .filter(m -> m.getState() == ArenaInstance.MatchState.ACTIVE
                            || m.getState() == ArenaInstance.MatchState.SUDDEN_DEATH
                            || m.getState() == ArenaInstance.MatchState.COUNTDOWN)
                    .findFirst().orElse(null);
            if (match == null) {
                return "現在アクティブなマッチがありません。";
            }
        }

        ArenaInstance.MatchState st = match.getState();
        if (st == ArenaInstance.MatchState.ENDING || st == ArenaInstance.MatchState.FINISHED) {
            return "そのマッチは終了しています。";
        }

        match.addSpectator(uuid);
        spectatorMatchMap.put(uuid, match.getMatchId());
        return null;
    }

    /**
     * Remove a player from spectating.
     * Returns true if the player was spectating.
     */
    public boolean leaveSpectate(UUID uuid) {
        Integer matchId = spectatorMatchMap.remove(uuid);
        if (matchId == null) return false;

        ArenaInstance match = activeMatches.get(matchId);
        if (match != null) {
            match.removeSpectator(uuid);
        }
        return true;
    }

    /**
     * Check if a player is spectating a match.
     */
    public boolean isSpectating(UUID uuid) {
        return spectatorMatchMap.containsKey(uuid);
    }

    /**
     * Handle spectator disconnect.
     */
    public void handleSpectatorDisconnect(UUID uuid) {
        leaveSpectate(uuid);
    }

    /**
     * Get the match a spectator is watching, or null.
     */
    public ArenaInstance getSpectatorMatch(UUID uuid) {
        Integer matchId = spectatorMatchMap.get(uuid);
        if (matchId == null) return null;
        return activeMatches.get(matchId);
    }

    /**
     * Get all active matches (for listing available matches to spectate).
     */
    public Map<Integer, ArenaInstance> getActiveMatches() {
        return Collections.unmodifiableMap(activeMatches);
    }

    private boolean allMembersOnline(Team team) {
        for (UUID uuid : team.getMembers()) {
            if (!isOnline(uuid)) return false;
        }
        return true;
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
