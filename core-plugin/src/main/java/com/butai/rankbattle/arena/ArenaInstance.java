package com.butai.rankbattle.arena;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.manager.EtherManager;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Logger;

/**
 * Represents a single match instance.
 * Manages match lifecycle: WAITING → COUNTDOWN → ACTIVE → ENDING → FINISHED
 */
public class ArenaInstance {

    public enum MatchState {
        WAITING, COUNTDOWN, ACTIVE, ENDING, FINISHED
    }

    public enum MatchType {
        SOLO_RANKED, TEAM_RANKED, PRACTICE
    }

    private static int nextMatchId = 1;

    private final int matchId;
    private final MatchType matchType;
    private final BRBPlugin plugin;
    private final EtherManager etherManager;
    private final Logger logger;

    private MatchState state = MatchState.WAITING;

    // Players in this match
    private final List<UUID> players = new ArrayList<>();
    // Team data for team matches: teamIndex(0,1) -> Set<UUID>
    private final Map<Integer, Set<UUID>> teamData = new HashMap<>();
    // Players who have been eliminated (E-Shift or death)
    private final Set<UUID> eliminated = new HashSet<>();
    // Damage tracking for judge scoring
    private final Map<UUID, Double> damageDealt = new HashMap<>();

    // Match timing
    private int timeLimit; // seconds
    private int timeRemaining; // seconds
    private int countdownRemaining;

    // Tasks
    private BukkitTask countdownTask;
    private BukkitTask matchTimerTask;

    // Spawn locations
    private Location spawn1;
    private Location spawn2;

    // Lobby location for return
    private Location lobbyLocation;

    // Callback for match completion
    private MatchEndCallback endCallback;

    @FunctionalInterface
    public interface MatchEndCallback {
        void onMatchEnd(ArenaInstance match);
    }

    public ArenaInstance(BRBPlugin plugin, MatchType matchType) {
        this.matchId = nextMatchId++;
        this.matchType = matchType;
        this.plugin = plugin;
        this.etherManager = plugin.getEtherManager();
        this.logger = plugin.getLogger();
        this.timeLimit = matchType == MatchType.TEAM_RANKED ? 600 : 300;
        this.timeRemaining = timeLimit;
    }

    public int getMatchId() { return matchId; }
    public MatchState getState() { return state; }
    public MatchType getMatchType() { return matchType; }
    public List<UUID> getPlayers() { return Collections.unmodifiableList(players); }
    public int getTimeRemaining() { return timeRemaining; }

    public void setSpawnLocations(Location spawn1, Location spawn2) {
        this.spawn1 = spawn1;
        this.spawn2 = spawn2;
    }

    public void setLobbyLocation(Location lobby) {
        this.lobbyLocation = lobby;
    }

    public void setEndCallback(MatchEndCallback callback) {
        this.endCallback = callback;
    }

    /**
     * Add a player (solo) to this match.
     */
    public void addPlayer(UUID uuid) {
        players.add(uuid);
        damageDealt.put(uuid, 0.0);
    }

    /**
     * Add teams to this match.
     */
    public void addTeams(Set<UUID> team1, Set<UUID> team2) {
        teamData.put(0, new HashSet<>(team1));
        teamData.put(1, new HashSet<>(team2));
        for (UUID uuid : team1) {
            players.add(uuid);
            damageDealt.put(uuid, 0.0);
        }
        for (UUID uuid : team2) {
            players.add(uuid);
            damageDealt.put(uuid, 0.0);
        }
    }

    /**
     * Check if two players are teammates.
     */
    public boolean isTeammate(UUID p1, UUID p2) {
        if (teamData.isEmpty()) return false;
        for (Set<UUID> team : teamData.values()) {
            if (team.contains(p1) && team.contains(p2)) return true;
        }
        return false;
    }

    /**
     * Start the countdown phase (10 seconds).
     */
    public void startCountdown() {
        if (state != MatchState.WAITING) return;
        state = MatchState.COUNTDOWN;
        countdownRemaining = 10;

        // Teleport players to spawn
        teleportToSpawns();

        // Initialize ether for all players
        for (UUID uuid : players) {
            etherManager.initPlayer(uuid);
        }

        broadcast("§e§lマッチが見つかりました！ §710秒後に開始します...");

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdownRemaining <= 0) {
                    cancel();
                    startMatch();
                    return;
                }

                if (countdownRemaining <= 5) {
                    broadcast("§e開始まで §f§l" + countdownRemaining + " §e秒...");
                    // Play sound for last 5 seconds
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        }
                    }
                }

                countdownRemaining--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Start the actual match.
     */
    private void startMatch() {
        if (state != MatchState.COUNTDOWN) return;
        state = MatchState.ACTIVE;

        // Start ether tick loop
        etherManager.startTickLoop();

        // Set E-Shift callback
        etherManager.setEShiftCallback(uuid -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> onPlayerEliminated(uuid));
        });

        broadcast("§a§l▶ 試合開始！");
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendTitle("§a§l試合開始！", "§7" + (timeLimit / 60) + "分間の戦闘", 5, 30, 10);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
            }
        }

        // Start match timer (ticks every second)
        matchTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != MatchState.ACTIVE) {
                    cancel();
                    return;
                }

                timeRemaining--;

                // Time warnings
                if (timeRemaining == 60) {
                    broadcast("§e⚠ 残り §f1分！");
                } else if (timeRemaining == 30) {
                    broadcast("§c⚠ 残り §f30秒！");
                } else if (timeRemaining <= 10 && timeRemaining > 0) {
                    broadcast("§c" + timeRemaining + "...");
                }

                if (timeRemaining <= 0) {
                    cancel();
                    onTimeUp();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Called when a player is eliminated (E-Shift or death).
     */
    public void onPlayerEliminated(UUID uuid) {
        if (state != MatchState.ACTIVE) return;
        if (!eliminated.add(uuid)) return; // Already eliminated

        Player eliminated_player = Bukkit.getPlayer(uuid);
        String name = eliminated_player != null ? eliminated_player.getName() : uuid.toString();
        broadcast("§c§l✖ " + name + " §7がE-Shiftしました！");

        checkWinCondition();
    }

    /**
     * Track damage dealt by a player.
     */
    public void addDamageDealt(UUID attacker, double damage) {
        damageDealt.merge(attacker, damage, Double::sum);
    }

    /**
     * Check if the match should end based on eliminations.
     */
    private void checkWinCondition() {
        if (teamData.isEmpty()) {
            // Solo: one player eliminated = other wins
            if (eliminated.size() >= 1) {
                UUID winner = null;
                UUID loser = null;
                for (UUID uuid : players) {
                    if (eliminated.contains(uuid)) {
                        loser = uuid;
                    } else {
                        winner = uuid;
                    }
                }
                endMatch(winner, loser);
            }
        } else {
            // Team: check if all members of a team are eliminated
            for (Map.Entry<Integer, Set<UUID>> entry : teamData.entrySet()) {
                int teamIndex = entry.getKey();
                Set<UUID> team = entry.getValue();
                if (eliminated.containsAll(team)) {
                    // This team is fully eliminated, other team wins
                    int winningTeamIndex = teamIndex == 0 ? 1 : 0;
                    endTeamMatch(winningTeamIndex);
                    return;
                }
            }
        }
    }

    /**
     * Called when time runs out.
     */
    private void onTimeUp() {
        if (state != MatchState.ACTIVE) return;
        broadcast("§e§l⏰ 制限時間終了！ジャッジ判定中...");

        // Calculate judge scores
        Map<UUID, Double> scores = calculateJudgeScores();

        if (teamData.isEmpty()) {
            // Solo judge
            UUID p1 = players.get(0);
            UUID p2 = players.get(1);
            double s1 = scores.getOrDefault(p1, 0.0);
            double s2 = scores.getOrDefault(p2, 0.0);

            // Show scores
            broadcastJudgeScores(scores);

            if (Math.abs(s1 - s2) / Math.max(s1 + s2, 1.0) <= 0.01) {
                // Draw (within 1%)
                broadcast("§e§l引き分け！ §7両者に参加ボーナスのみ付与されます。");
                endMatch(null, null); // Draw
            } else if (s1 > s2) {
                endMatch(p1, p2);
            } else {
                endMatch(p2, p1);
            }
        } else {
            // Team judge: sum scores by team
            double[] teamScores = new double[2];
            for (Map.Entry<Integer, Set<UUID>> entry : teamData.entrySet()) {
                for (UUID uuid : entry.getValue()) {
                    if (!eliminated.contains(uuid)) {
                        teamScores[entry.getKey()] += scores.getOrDefault(uuid, 0.0);
                    }
                }
            }

            broadcastJudgeScores(scores);

            if (Math.abs(teamScores[0] - teamScores[1]) / Math.max(teamScores[0] + teamScores[1], 1.0) <= 0.01) {
                broadcast("§e§l引き分け！");
                endMatch(null, null);
            } else {
                endTeamMatch(teamScores[0] > teamScores[1] ? 0 : 1);
            }
        }
    }

    /**
     * Calculate judge scores for all alive players.
     * Score = damage × 0.5 + ether × 0.3 + HP × 0.2
     */
    private Map<UUID, Double> calculateJudgeScores() {
        Map<UUID, Double> scores = new HashMap<>();
        for (UUID uuid : players) {
            if (eliminated.contains(uuid)) {
                scores.put(uuid, 0.0);
                continue;
            }
            Player p = Bukkit.getPlayer(uuid);
            double damage = damageDealt.getOrDefault(uuid, 0.0);
            double ether = etherManager.getEther(uuid);
            double hp = p != null ? p.getHealth() : 0;

            double score = damage * 0.5 + ether * 0.3 + hp * 0.2;
            scores.put(uuid, score);
        }
        return scores;
    }

    /**
     * Broadcast judge scores to all match players.
     */
    private void broadcastJudgeScores(Map<UUID, Double> scores) {
        broadcast("§6§l=== ジャッジスコア ===");
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : uuid.toString().substring(0, 8);
            double score = scores.getOrDefault(uuid, 0.0);
            double damage = damageDealt.getOrDefault(uuid, 0.0);
            int ether = etherManager.getEther(uuid);

            broadcast("  §f" + name + ": §e" + String.format("%.1f", score)
                    + " §8(DMG:§7" + String.format("%.1f", damage)
                    + " §8E:§9" + ether + "§8)");
        }
    }

    /**
     * End a solo match with a winner and loser.
     * Pass null for both for a draw.
     */
    private void endMatch(UUID winner, UUID loser) {
        if (state == MatchState.ENDING || state == MatchState.FINISHED) return;
        state = MatchState.ENDING;

        cancelTasks();

        if (winner != null && loser != null) {
            Player winnerPlayer = Bukkit.getPlayer(winner);
            Player loserPlayer = Bukkit.getPlayer(loser);
            String winnerName = winnerPlayer != null ? winnerPlayer.getName() : "???";
            String loserName = loserPlayer != null ? loserPlayer.getName() : "???";

            broadcast("§6§l==================");
            broadcast("§a§l  ✦ 勝者: §f§l" + winnerName);
            broadcast("§c   敗者: §7" + loserName);
            broadcast("§6§l==================");

            if (winnerPlayer != null) {
                winnerPlayer.sendTitle("§a§l勝利！", "§7おめでとうございます！", 5, 60, 20);
            }
            if (loserPlayer != null) {
                loserPlayer.sendTitle("§c§l敗北", "§7次回頑張りましょう", 5, 60, 20);
            }
        } else {
            broadcast("§6§l==================");
            broadcast("§e§l  引き分け");
            broadcast("§6§l==================");
        }

        // Return to lobby after 5 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                finishMatch();
            }
        }.runTaskLater(plugin, 100L); // 5 seconds
    }

    /**
     * End a team match with a winning team index.
     */
    private void endTeamMatch(int winningTeamIndex) {
        if (state == MatchState.ENDING || state == MatchState.FINISHED) return;
        state = MatchState.ENDING;

        cancelTasks();

        broadcast("§6§l==================");
        broadcast("§a§l  ✦ チーム" + (winningTeamIndex + 1) + " の勝利！");
        broadcast("§6§l==================");

        Set<UUID> winners = teamData.get(winningTeamIndex);
        int losingTeamIndex = winningTeamIndex == 0 ? 1 : 0;
        Set<UUID> losers = teamData.get(losingTeamIndex);

        if (winners != null) {
            for (UUID uuid : winners) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendTitle("§a§l勝利！", "", 5, 60, 20);
            }
        }
        if (losers != null) {
            for (UUID uuid : losers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.sendTitle("§c§l敗北", "", 5, 60, 20);
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                finishMatch();
            }
        }.runTaskLater(plugin, 100L);
    }

    /**
     * Final cleanup: teleport players back, remove from ether, notify callback.
     */
    private void finishMatch() {
        state = MatchState.FINISHED;

        for (UUID uuid : players) {
            etherManager.removePlayer(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (lobbyLocation != null) {
                    p.teleport(lobbyLocation);
                } else {
                    p.teleport(p.getWorld().getSpawnLocation());
                }
            }
        }

        // Stop ether tick if no more active matches
        if (!etherManager.hasActivePlayers()) {
            etherManager.stopTickLoop();
        }

        if (endCallback != null) {
            endCallback.onMatchEnd(this);
        }

        logger.info("Match #" + matchId + " finished.");
    }

    /**
     * Teleport players to spawn locations.
     */
    private void teleportToSpawns() {
        if (spawn1 == null || spawn2 == null) {
            logger.warning("Spawn locations not set for match #" + matchId);
            return;
        }

        if (teamData.isEmpty()) {
            // Solo: player 0 to spawn1, player 1 to spawn2
            if (players.size() >= 2) {
                Player p1 = Bukkit.getPlayer(players.get(0));
                Player p2 = Bukkit.getPlayer(players.get(1));
                if (p1 != null) p1.teleport(spawn1);
                if (p2 != null) p2.teleport(spawn2);
            }
        } else {
            // Team: team 0 to spawn1 area, team 1 to spawn2 area
            Set<UUID> team0 = teamData.get(0);
            Set<UUID> team1 = teamData.get(1);
            if (team0 != null) {
                int offset = 0;
                for (UUID uuid : team0) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.teleport(spawn1.clone().add(offset * 2, 0, 0));
                        offset++;
                    }
                }
            }
            if (team1 != null) {
                int offset = 0;
                for (UUID uuid : team1) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.teleport(spawn2.clone().add(offset * 2, 0, 0));
                        offset++;
                    }
                }
            }
        }
    }

    /**
     * Send a message to all players in this match.
     */
    private void broadcast(String message) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                MessageUtil.send(p, message);
            }
        }
    }

    /**
     * Cancel any running tasks.
     */
    private void cancelTasks() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (matchTimerTask != null) {
            matchTimerTask.cancel();
            matchTimerTask = null;
        }
    }

    /**
     * Check if a player is in this match.
     */
    public boolean hasPlayer(UUID uuid) {
        return players.contains(uuid);
    }

    /**
     * Check if a player is eliminated.
     */
    public boolean isEliminated(UUID uuid) {
        return eliminated.contains(uuid);
    }

    /**
     * Get damage dealt by a player.
     */
    public double getDamageDealt(UUID uuid) {
        return damageDealt.getOrDefault(uuid, 0.0);
    }
}
