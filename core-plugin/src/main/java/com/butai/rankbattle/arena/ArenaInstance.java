package com.butai.rankbattle.arena;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.manager.EtherManager;
import com.butai.rankbattle.manager.RankManager;
import com.butai.rankbattle.model.BRBPlayer;
import com.butai.rankbattle.model.WeaponType;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import com.butai.rankbattle.database.MatchHistoryDAO;
import com.butai.rankbattle.database.SeasonDAO;

import java.util.*;
import java.util.logging.Logger;

/**
 * Represents a single match instance.
 * Manages match lifecycle: WAITING → COUNTDOWN → ACTIVE → SUDDEN_DEATH → ENDING → FINISHED
 */
public class ArenaInstance {

    public enum MatchState {
        WAITING, COUNTDOWN, ACTIVE, SUDDEN_DEATH, ENDING, FINISHED
    }

    public enum MatchType {
        SOLO_RANKED, TEAM_RANKED, PRACTICE
    }

    private static final int SUDDEN_DEATH_TIME = 60; // seconds
    private static final double SUDDEN_DEATH_LEAK_MULTIPLIER = 1.5; // 3x normal (0.5 * 3)

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
    // Spectators watching this match (not participants)
    private final Set<UUID> spectators = new HashSet<>();
    // Damage tracking for judge scoring
    private final Map<UUID, Double> damageDealt = new HashMap<>();
    // Block state tracking for map restoration
    private final Map<Location, BlockState> originalBlocks = new LinkedHashMap<>();
    // Weapon type per player (determined at match start from slot 1)
    private final Map<UUID, WeaponType> playerWeaponTypes = new HashMap<>();
    // RP changes per player (recorded during endMatch/endTeamMatch for DB saving)
    private final Map<UUID, Integer> rpChanges = new HashMap<>();
    // Match result tracking for DB
    private String resultType = "kill"; // kill, judge, sudden_death, draw, disconnect
    private String mapName = "default";
    private UUID matchWinner = null; // solo winner UUID
    private int winningTeamIndex = -1; // team match winning team

    // Match timing
    private int timeLimit; // seconds
    private int timeRemaining; // seconds
    private int countdownRemaining;

    // Tasks
    private BukkitTask countdownTask;
    private BukkitTask matchTimerTask;
    private BukkitTask suddenDeathTask;

    // Boss bar for match timer
    private BossBar bossBar;

    // Sidebar scoreboard
    private Scoreboard scoreboard;

    // Spawn locations
    private Location spawn1;
    private Location spawn2;

    // Spectator location (from map config, or calculated from spawns)
    private Location spectateLocation;

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

    public void setSpectateLocation(Location spectateLocation) {
        this.spectateLocation = spectateLocation;
    }


    public void setEndCallback(MatchEndCallback callback) {
        this.endCallback = callback;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
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

        // Record weapon types and initialize ether for all players
        for (UUID uuid : players) {
            WeaponType wt = plugin.getFrameSetManager().getWeaponType(uuid);
            if (wt != null) {
                playerWeaponTypes.put(uuid, wt);
            }
            // Set player-specific ether cap from growth system
            if (plugin.getEtherGrowthManager() != null) {
                int cap = plugin.getEtherGrowthManager().getEtherCap(uuid);
                etherManager.setMaxEther(uuid, cap);
            }
            etherManager.initPlayer(uuid);
        }

        // Create boss bar
        bossBar = Bukkit.createBossBar("§e§lマッチ開始まで §f" + countdownRemaining + "秒",
                BarColor.YELLOW, BarStyle.SOLID);
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) bossBar.addPlayer(p);
        }

        broadcast("§e§lマッチが見つかりました！ §710秒後に開始します...");
        if (!mapName.equals("default")) {
            broadcast("§7マップ: §f" + mapName);
        }

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
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                        }
                    }
                }

                bossBar.setTitle("§e§lマッチ開始まで §f" + countdownRemaining + "秒");
                bossBar.setProgress(Math.max(0, (double) countdownRemaining / 10.0));
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
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player ep = Bukkit.getPlayer(uuid);
                String name = ep != null ? ep.getName() : uuid.toString().substring(0, 8);
                broadcast("§c§l✖ " + name + " §7がE-Shiftしました！");
                onPlayerEliminated(uuid);
            });
        });

        // Update boss bar for match timer
        bossBar.setColor(BarColor.GREEN);
        bossBar.setStyle(BarStyle.SEGMENTED_10);
        updateBossBar();

        // Create sidebar scoreboard
        createScoreboard();

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
                updateBossBar();
                updateScoreboard();

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
        if (state != MatchState.ACTIVE && state != MatchState.SUDDEN_DEATH) return;
        if (!eliminated.add(uuid)) return; // Already eliminated

        checkWinCondition();
    }

    /**
     * Called when a player disconnects during match (sets result type to disconnect).
     */
    public void onPlayerDisconnected(UUID uuid) {
        if (resultType.equals("kill")) {
            resultType = "disconnect";
        }
        onPlayerEliminated(uuid);
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
                    int winningTeamIndex = teamIndex == 0 ? 1 : 0;
                    endTeamMatch(winningTeamIndex);
                    return;
                }
            }
        }
    }

    /**
     * Called when time runs out during ACTIVE phase.
     */
    private void onTimeUp() {
        if (state != MatchState.ACTIVE) return;
        broadcast("§e§l⏰ 制限時間終了！ジャッジ判定中...");

        Map<UUID, Double> scores = calculateJudgeScores();
        broadcastJudgeScores(scores);

        if (teamData.isEmpty()) {
            UUID p1 = players.get(0);
            UUID p2 = players.get(1);
            double s1 = scores.getOrDefault(p1, 0.0);
            double s2 = scores.getOrDefault(p2, 0.0);

            if (isWithinDrawThreshold(s1, s2)) {
                startSuddenDeath();
            } else {
                resultType = "judge";
                broadcast("§6§lジャッジ判定: §f勝者決定！");
                if (s1 > s2) {
                    endMatch(p1, p2);
                } else {
                    endMatch(p2, p1);
                }
            }
        } else {
            double[] teamScores = calculateTeamScores(scores);
            if (isWithinDrawThreshold(teamScores[0], teamScores[1])) {
                startSuddenDeath();
            } else {
                resultType = "judge";
                broadcast("§6§lジャッジ判定: §f勝者決定！");
                endTeamMatch(teamScores[0] > teamScores[1] ? 0 : 1);
            }
        }
    }

    /**
     * Check if two scores are within the 1% draw threshold.
     */
    private boolean isWithinDrawThreshold(double s1, double s2) {
        return Math.abs(s1 - s2) / Math.max(s1 + s2, 1.0) <= 0.01;
    }

    /**
     * Calculate team scores from individual judge scores.
     */
    private double[] calculateTeamScores(Map<UUID, Double> scores) {
        double[] teamScores = new double[2];
        for (Map.Entry<Integer, Set<UUID>> entry : teamData.entrySet()) {
            for (UUID uuid : entry.getValue()) {
                if (!eliminated.contains(uuid)) {
                    teamScores[entry.getKey()] += scores.getOrDefault(uuid, 0.0);
                }
            }
        }
        return teamScores;
    }

    /**
     * Start sudden death phase.
     * - Ether leak 3x acceleration
     * - 60 second time limit
     * - Boss bar turns red
     * - Warning messages and sound effects
     */
    private void startSuddenDeath() {
        state = MatchState.SUDDEN_DEATH;
        resultType = "sudden_death";
        timeRemaining = SUDDEN_DEATH_TIME;

        // Accelerate ether leak (3x: 0.5 → 1.5)
        etherManager.setLeakCoefficient(SUDDEN_DEATH_LEAK_MULTIPLIER);

        // Update boss bar to red
        if (bossBar != null) {
            bossBar.setColor(BarColor.RED);
            bossBar.setStyle(BarStyle.SOLID);
            bossBar.setTitle("§c§l⚠ サドンデス §f" + timeRemaining + "秒");
            bossBar.setProgress(1.0);
        }

        // Broadcast warning
        broadcast("§c§l⚠ サドンデス突入！エーテルリーク加速！");
        broadcast("§7追加60秒以内に決着しない場合は引き分けとなります。");

        // Sound effect for all players
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendTitle("§c§l⚠ SUDDEN DEATH ⚠", "§cエーテルリーク3倍加速！", 5, 50, 20);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.0f);
            }
        }

        // Start sudden death timer
        suddenDeathTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state != MatchState.SUDDEN_DEATH) {
                    cancel();
                    return;
                }

                timeRemaining--;

                // Update boss bar
                if (bossBar != null) {
                    bossBar.setTitle("§c§l⚠ サドンデス §f" + timeRemaining + "秒");
                    bossBar.setProgress(Math.max(0, (double) timeRemaining / SUDDEN_DEATH_TIME));
                }

                // Time warnings
                if (timeRemaining == 30) {
                    broadcast("§c⚠ サドンデス残り §f30秒！");
                } else if (timeRemaining <= 10 && timeRemaining > 0) {
                    broadcast("§c§l" + timeRemaining + "...");
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && !eliminated.contains(uuid)) {
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                        }
                    }
                }

                if (timeRemaining <= 0) {
                    cancel();
                    onSuddenDeathTimeUp();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        logger.info("Match #" + matchId + " entered sudden death.");
    }

    /**
     * Called when sudden death time runs out. Results in a draw.
     */
    private void onSuddenDeathTimeUp() {
        if (state != MatchState.SUDDEN_DEATH) return;

        broadcast("§e§l⏰ サドンデス終了！引き分け！");
        broadcast("§7両者に参加ボーナスのみ付与されます。");

        // Reset leak coefficient
        etherManager.resetLeakCoefficient();

        resultType = "draw";
        endMatch(null, null); // Draw
    }

    /**
     * Update boss bar to show remaining time.
     */
    private void updateBossBar() {
        if (bossBar == null) return;
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        bossBar.setTitle("§e§l残り時間 §f" + String.format("%d:%02d", minutes, seconds));
        bossBar.setProgress(Math.max(0, Math.min(1, (double) timeRemaining / timeLimit)));

        // Change color based on time
        if (timeRemaining <= 30) {
            bossBar.setColor(BarColor.RED);
        } else if (timeRemaining <= 60) {
            bossBar.setColor(BarColor.YELLOW);
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
        etherManager.resetLeakCoefficient();

        if (winner != null && loser != null) {
            matchWinner = winner;
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
                loserPlayer.sendTitle("§c§l敗北", "", 5, 60, 20);
            }

            // Apply RP changes (ranked matches only)
            if (matchType == MatchType.SOLO_RANKED) {
                applyRPChanges(winner, loser);
            }
        } else {
            broadcast("§6§l==================");
            broadcast("§e§l  引き分け");
            broadcast("§7  両者に参加ボーナス(+5 RP)のみ");
            broadcast("§6§l==================");

            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendTitle("§e§l引き分け", "§7参加ボーナスのみ付与", 5, 60, 20);
                }
            }

            // Apply participation bonus only (ranked matches)
            if (matchType == MatchType.SOLO_RANKED || matchType == MatchType.TEAM_RANKED) {
                applyDrawRP();
            }
        }

        // Return to lobby after 5 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                finishMatch();
            }
        }.runTaskLater(plugin, 100L);
    }

    /**
     * End a team match with a winning team index.
     */
    private void endTeamMatch(int winningTeamIdx) {
        if (state == MatchState.ENDING || state == MatchState.FINISHED) return;
        state = MatchState.ENDING;
        this.winningTeamIndex = winningTeamIdx;

        cancelTasks();
        etherManager.resetLeakCoefficient();

        Set<UUID> winners = teamData.get(winningTeamIdx);
        int losingTeamIndex = winningTeamIdx == 0 ? 1 : 0;
        Set<UUID> losers = teamData.get(losingTeamIndex);

        // Build winner/loser name lists
        List<String> winnerNames = new ArrayList<>();
        List<String> loserNames = new ArrayList<>();
        if (winners != null) {
            for (UUID uuid : winners) {
                Player p = Bukkit.getPlayer(uuid);
                winnerNames.add(p != null ? p.getName() : uuid.toString().substring(0, 8));
            }
        }
        if (losers != null) {
            for (UUID uuid : losers) {
                Player p = Bukkit.getPlayer(uuid);
                loserNames.add(p != null ? p.getName() : uuid.toString().substring(0, 8));
            }
        }

        broadcast("§6§l==================");
        broadcast("§a§l  ✦ 勝利チーム: §f" + String.join(", ", winnerNames));
        broadcast("§c   敗北チーム: §7" + String.join(", ", loserNames));
        broadcast("§6§l==================");

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

        // Apply RP changes for team ranked
        if (matchType == MatchType.TEAM_RANKED && winners != null && losers != null) {
            applyTeamRPChanges(winners, losers);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                finishMatch();
            }
        }.runTaskLater(plugin, 100L);
    }

    /**
     * Apply RP changes for a team match.
     * Base: Elo calculation using team average RP
     * Bonus: +5 per kill, +10 for surviving (not eliminated)
     */
    private void applyTeamRPChanges(Set<UUID> winners, Set<UUID> losers) {
        RankManager rankManager = plugin.getRankManager();

        // Calculate team average RP
        int winnerTotalRP = 0, winnerCount = 0;
        int loserTotalRP = 0, loserCount = 0;

        for (UUID uuid : winners) {
            WeaponType wt = playerWeaponTypes.get(uuid);
            BRBPlayer data = rankManager.getPlayer(uuid);
            if (wt != null && data != null) {
                winnerTotalRP += data.getWeaponRP(wt).getRp();
                winnerCount++;
            }
        }
        for (UUID uuid : losers) {
            WeaponType wt = playerWeaponTypes.get(uuid);
            BRBPlayer data = rankManager.getPlayer(uuid);
            if (wt != null && data != null) {
                loserTotalRP += data.getWeaponRP(wt).getRp();
                loserCount++;
            }
        }

        int avgWinnerRP = winnerCount > 0 ? winnerTotalRP / winnerCount : 1000;
        int avgLoserRP = loserCount > 0 ? loserTotalRP / loserCount : 1000;

        // Base RP from Elo formula
        int[] baseRP = rankManager.calculateRP(avgWinnerRP, avgLoserRP);
        int baseWinGain = baseRP[0];
        int baseLoseLoss = baseRP[1];

        broadcast("§6§lRP変動:");

        // Apply to winners
        for (UUID uuid : winners) {
            WeaponType wt = playerWeaponTypes.get(uuid);
            BRBPlayer data = rankManager.getPlayer(uuid);
            if (wt == null || data == null) continue;

            int currentRP = data.getWeaponRP(wt).getRp();
            int gain = baseWinGain;

            // Survival bonus: +10 if not eliminated
            if (!eliminated.contains(uuid)) gain += 10;
            // Kill contribution bonus: based on damage dealt
            double dmg = damageDealt.getOrDefault(uuid, 0.0);
            if (dmg >= 20) gain += 5; // significant damage bonus

            rankManager.applyMatchResult(uuid, wt, gain, true);
            rpChanges.put(uuid, gain);

            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "???";
            String bonusInfo = "";
            if (!eliminated.contains(uuid)) bonusInfo += " +生存";
            if (dmg >= 20) bonusInfo += " +貢献";

            broadcast("  §a" + name + " §f+" + gain + " RP"
                    + " §8(" + wt.getColor() + wt.getDisplayName() + " §f" + (currentRP + gain) + "§8)"
                    + (bonusInfo.isEmpty() ? "" : " §7[" + bonusInfo.trim() + "]"));

            checkAndAnnounceRankChange(uuid, data);
        }

        // Apply to losers
        for (UUID uuid : losers) {
            WeaponType wt = playerWeaponTypes.get(uuid);
            BRBPlayer data = rankManager.getPlayer(uuid);
            if (wt == null || data == null) continue;

            int currentRP = data.getWeaponRP(wt).getRp();
            int loss = baseLoseLoss;

            // Reduce loss if player contributed significantly
            double dmg = damageDealt.getOrDefault(uuid, 0.0);
            if (dmg >= 20) loss = Math.max(5, loss - 5);

            rankManager.applyMatchResult(uuid, wt, -loss, false);
            rpChanges.put(uuid, -loss);

            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "???";

            broadcast("  §c" + name + " §f-" + loss + " RP"
                    + " §8(" + wt.getColor() + wt.getDisplayName() + " §f" + Math.max(0, currentRP - loss) + "§8)");

            checkAndAnnounceRankChange(uuid, data);
        }

        logger.info("Team match #" + matchId + " RP applied. Winners: +" + baseWinGain + " base, Losers: -" + baseLoseLoss + " base");
    }

    /**
     * Apply RP changes after a solo match with a winner and loser.
     */
    private void applyRPChanges(UUID winner, UUID loser) {
        RankManager rankManager = plugin.getRankManager();

        WeaponType winnerWeapon = playerWeaponTypes.get(winner);
        WeaponType loserWeapon = playerWeaponTypes.get(loser);
        if (winnerWeapon == null || loserWeapon == null) return;

        // Get current RP for calculation
        BRBPlayer winnerData = rankManager.getPlayer(winner);
        BRBPlayer loserData = rankManager.getPlayer(loser);
        if (winnerData == null || loserData == null) return;

        int winnerRP = winnerData.getWeaponRP(winnerWeapon).getRp();
        int loserRP = loserData.getWeaponRP(loserWeapon).getRp();

        // Calculate RP changes (asymmetric Elo)
        int[] rpCalc = rankManager.calculateRP(winnerRP, loserRP);
        int winnerGain = rpCalc[0];
        int loserLoss = rpCalc[1];

        // Apply to winner
        rankManager.applyMatchResult(winner, winnerWeapon, winnerGain, true);
        rpChanges.put(winner, winnerGain);
        // Apply to loser (negative RP)
        rankManager.applyMatchResult(loser, loserWeapon, -loserLoss, false);
        rpChanges.put(loser, -loserLoss);

        // Broadcast RP changes
        Player wp = Bukkit.getPlayer(winner);
        Player lp = Bukkit.getPlayer(loser);
        String winnerName = wp != null ? wp.getName() : "???";
        String loserName = lp != null ? lp.getName() : "???";

        broadcast("§6§lRP変動:");
        broadcast("  §a" + winnerName + " §f+" + winnerGain + " RP"
                + " §8(" + winnerWeapon.getColor() + winnerWeapon.getDisplayName() + " §f"
                + (winnerRP + winnerGain) + "§8)");
        broadcast("  §c" + loserName + " §f-" + loserLoss + " RP"
                + " §8(" + loserWeapon.getColor() + loserWeapon.getDisplayName() + " §f"
                + Math.max(0, loserRP - loserLoss) + "§8)");

        // Check for rank changes
        checkAndAnnounceRankChange(winner, winnerData);
        checkAndAnnounceRankChange(loser, loserData);

        logger.info("Match #" + matchId + " RP: " + winnerName + " +" + winnerGain
                + ", " + loserName + " -" + loserLoss);
    }

    /**
     * Apply participation bonus only (draw result).
     */
    private void applyDrawRP() {
        RankManager rankManager = plugin.getRankManager();
        int participationBonus = 5;

        for (UUID uuid : players) {
            WeaponType weapon = playerWeaponTypes.get(uuid);
            if (weapon == null) continue;
            BRBPlayer data = rankManager.getPlayer(uuid);
            if (data == null) continue;

            int currentRP = data.getWeaponRP(weapon).getRp();
            rankManager.applyMatchResult(uuid, weapon, participationBonus, false);
            rpChanges.put(uuid, participationBonus);

            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "???";
            broadcast("  §e" + name + " §f+" + participationBonus + " RP (参加ボーナス)"
                    + " §8(" + weapon.getColor() + weapon.getDisplayName() + " §f"
                    + (currentRP + participationBonus) + "§8)");
        }
    }

    /**
     * Check if a player's rank changed and announce it.
     */
    private void checkAndAnnounceRankChange(UUID uuid, BRBPlayer data) {
        String oldRank = data.getRankClass().getDisplayName();
        if (data.recalculateRank()) {
            String newRank = data.getRankClass().getColoredName();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                broadcast("§d§l★ " + p.getName() + " §7が §f" + oldRank + " §7→ " + newRank + " §7にランクアップ！");
                p.sendTitle("§d§lランク変動！", newRank, 5, 60, 20);
                // Update tab list name
                if (plugin.getChatTabListener() != null) {
                    plugin.getChatTabListener().updateTabName(p);
                }
            }
        }
    }

    /**
     * Final cleanup: teleport players back, remove from ether, notify callback.
     */
    private void finishMatch() {
        state = MatchState.FINISHED;
        FrameCommand fc = plugin.getFrameCommand();

        // Save match history to DB (async-safe: runs on main thread before teleport)
        saveMatchHistory();

        // Restore blocks changed during the match
        restoreBlocks();

        // Remove boss bar
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        // Remove scoreboard
        removeScoreboard();

        for (UUID uuid : players) {
            etherManager.removePlayer(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                if (lobbyLocation != null) {
                    p.teleport(lobbyLocation);
                } else {
                    p.teleport(p.getWorld().getSpawnLocation());
                }
                p.setGameMode(GameMode.ADVENTURE);
                p.setHealth(p.getMaxHealth());
                p.setFoodLevel(20);
                // Restore hotbar items
                if (fc != null) fc.refreshHotbar(p);
            }
        }

        // Return spectators to lobby
        for (UUID uuid : spectators) {
            Player sp = Bukkit.getPlayer(uuid);
            if (sp != null && sp.isOnline()) {
                if (lobbyLocation != null) {
                    sp.teleport(lobbyLocation);
                } else {
                    sp.teleport(sp.getWorld().getSpawnLocation());
                }
                sp.setGameMode(GameMode.ADVENTURE);
                if (fc != null) fc.refreshHotbar(sp);
            }
        }
        spectators.clear();

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
     * Save match history and per-player results to DB.
     */
    private void saveMatchHistory() {
        MatchHistoryDAO matchDAO = new MatchHistoryDAO(plugin.getDatabaseManager(), logger);
        SeasonDAO seasonDAO = new SeasonDAO(plugin.getDatabaseManager(), logger);

        // Determine match type string for DB
        String matchTypeStr = switch (matchType) {
            case SOLO_RANKED -> "solo";
            case TEAM_RANKED -> "team";
            case PRACTICE -> "practice";
        };

        int seasonId = seasonDAO.getActiveSeasonId();
        int durationSec = timeLimit - timeRemaining;
        if (durationSec < 0) durationSec = timeLimit;

        int dbMatchId = matchDAO.insertMatchHistory(matchTypeStr, mapName,
                seasonId > 0 ? seasonId : null, resultType, durationSec);
        if (dbMatchId < 0) {
            logger.warning("Failed to save match history for match #" + matchId);
            return;
        }

        // Determine placement per player
        for (UUID uuid : players) {
            WeaponType wt = playerWeaponTypes.get(uuid);
            String weaponStr = wt != null ? wt.name() : "STRIKER";
            double damage = damageDealt.getOrDefault(uuid, 0.0);
            int ether = etherManager.getEther(uuid);
            int rpChange = rpChanges.getOrDefault(uuid, 0);
            boolean survived = !eliminated.contains(uuid);
            int kills = 0; // kill tracking is done via elimination count
            int deaths = eliminated.contains(uuid) ? 1 : 0;

            // Determine placement: 1=winner, 2=loser
            int placement;
            if (teamData.isEmpty()) {
                // Solo: winner=matchWinner
                if (matchWinner != null && matchWinner.equals(uuid)) {
                    placement = 1;
                } else if (matchWinner != null) {
                    placement = 2;
                } else {
                    placement = 0; // draw
                }
            } else {
                // Team: check winning team
                if (winningTeamIndex >= 0) {
                    Set<UUID> winTeam = teamData.get(winningTeamIndex);
                    placement = (winTeam != null && winTeam.contains(uuid)) ? 1 : 2;
                } else {
                    placement = 0; // draw
                }
            }

            matchDAO.insertMatchResult(dbMatchId, uuid, null, weaponStr,
                    kills, deaths, damage, ether, null, survived, rpChange, placement);
        }

        logger.info("Match #" + matchId + " saved to DB as match_history id=" + dbMatchId);
    }

    /**
     * Teleport players to spawn locations.
     */
    private void teleportToSpawns() {
        if (spawn1 == null || spawn2 == null) {
            logger.warning("Spawn locations not set for match #" + matchId);
            return;
        }

        FrameCommand fc = plugin.getFrameCommand();

        if (teamData.isEmpty()) {
            if (players.size() >= 2) {
                Player p1 = Bukkit.getPlayer(players.get(0));
                Player p2 = Bukkit.getPlayer(players.get(1));
                if (p1 != null) { p1.setGameMode(GameMode.SURVIVAL); p1.teleport(spawn1); p1.setHealth(p1.getMaxHealth()); p1.setFoodLevel(20); if (fc != null) fc.refreshHotbar(p1); }
                if (p2 != null) { p2.setGameMode(GameMode.SURVIVAL); p2.teleport(spawn2); p2.setHealth(p2.getMaxHealth()); p2.setFoodLevel(20); if (fc != null) fc.refreshHotbar(p2); }
            }
        } else {
            Set<UUID> team0 = teamData.get(0);
            Set<UUID> team1 = teamData.get(1);
            if (team0 != null) {
                int offset = 0;
                for (UUID uuid : team0) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.setGameMode(GameMode.SURVIVAL);
                        p.teleport(spawn1.clone().add(offset * 2, 0, 0));
                        p.setHealth(p.getMaxHealth());
                        p.setFoodLevel(20);
                        if (fc != null) fc.refreshHotbar(p);
                        offset++;
                    }
                }
            }
            if (team1 != null) {
                int offset = 0;
                for (UUID uuid : team1) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.setGameMode(GameMode.SURVIVAL);
                        p.teleport(spawn2.clone().add(offset * 2, 0, 0));
                        p.setHealth(p.getMaxHealth());
                        p.setFoodLevel(20);
                        if (fc != null) fc.refreshHotbar(p);
                        offset++;
                    }
                }
            }
        }
    }

    // ==================== Scoreboard ====================

    /**
     * Create the sidebar scoreboard for all match participants.
     */
    private void createScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = scoreboard.registerNewObjective("brb_match", Criteria.DUMMY, "§6§lBRB Match #" + matchId);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        updateScoreboard();

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setScoreboard(scoreboard);
        }
        for (UUID uuid : spectators) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setScoreboard(scoreboard);
        }
    }

    /**
     * Update sidebar scoreboard lines.
     */
    private void updateScoreboard() {
        if (scoreboard == null) return;
        Objective obj = scoreboard.getObjective("brb_match");
        if (obj == null) return;

        // Clear existing entries
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int line = 10;
        String typeName = switch (matchType) {
            case SOLO_RANKED -> "§eソロランク";
            case TEAM_RANKED -> "§dチームランク";
            case PRACTICE -> "§aプラクティス";
        };
        obj.getScore(typeName).setScore(line--);

        // Time
        int minutes = timeRemaining / 60;
        int seconds = timeRemaining % 60;
        String stateStr = state == MatchState.SUDDEN_DEATH ? "§c§lサドンデス" : "§f残り時間";
        obj.getScore(stateStr + " §7" + String.format("%d:%02d", minutes, seconds)).setScore(line--);

        obj.getScore("§8─────────").setScore(line--);

        // Players status
        int alive = 0;
        int total = players.size();
        for (UUID uuid : players) {
            if (!eliminated.contains(uuid)) alive++;
        }
        obj.getScore("§f生存: §a" + alive + " §7/ §f" + total).setScore(line--);

        obj.getScore("§8─────────§r").setScore(line--);
    }

    /**
     * Remove scoreboard from all players and reset to main scoreboard.
     */
    private void removeScoreboard() {
        if (scoreboard == null) return;
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setScoreboard(main);
            }
        }
        for (UUID uuid : spectators) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setScoreboard(main);
            }
        }
        scoreboard = null;
    }

    /**
     * Send a message to all players and spectators in this match.
     */
    public void broadcast(String message) {
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                MessageUtil.send(p, message);
            }
        }
        for (UUID uuid : spectators) {
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
        if (suddenDeathTask != null) {
            suddenDeathTask.cancel();
            suddenDeathTask = null;
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
     * Get spectator location (from map config, or midpoint between spawns).
     */
    public Location getSpectatorLocation() {
        if (spectateLocation != null) return spectateLocation;
        if (spawn1 == null || spawn2 == null) return null;
        return spawn1.clone().add(spawn2).multiply(0.5).add(0, 10, 0);
    }

    /**
     * Add a spectator to this match.
     */
    public void addSpectator(UUID uuid) {
        spectators.add(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.setGameMode(GameMode.SPECTATOR);
            Location specLoc = getSpectatorLocation();
            if (specLoc != null) {
                p.teleport(specLoc);
            }
            if (scoreboard != null) {
                p.setScoreboard(scoreboard);
            }
            if (bossBar != null) {
                bossBar.addPlayer(p);
            }
        }
    }

    /**
     * Remove a spectator from this match.
     */
    public void removeSpectator(UUID uuid) {
        spectators.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            if (bossBar != null) {
                bossBar.removePlayer(p);
            }
        }
    }

    /**
     * Check if a player is spectating this match.
     */
    public boolean hasSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    /**
     * Get all spectators.
     */
    public Set<UUID> getSpectators() {
        return Collections.unmodifiableSet(spectators);
    }

    /**
     * Get damage dealt by a player.
     */
    public double getDamageDealt(UUID uuid) {
        return damageDealt.getOrDefault(uuid, 0.0);
    }

    /**
     * Record original block state before it is changed.
     * Only records the first change per location (original state).
     */
    public void trackBlockChange(Location loc, BlockState originalState) {
        originalBlocks.putIfAbsent(loc, originalState);
    }

    /**
     * Restore all tracked blocks to their original state.
     */
    private void restoreBlocks() {
        if (originalBlocks.isEmpty()) return;
        int count = originalBlocks.size();
        // Restore in reverse order to handle stacked changes correctly
        List<BlockState> states = new ArrayList<>(originalBlocks.values());
        Collections.reverse(states);
        for (BlockState state : states) {
            state.update(true, false);
        }
        originalBlocks.clear();
        logger.info("Match #" + matchId + ": Restored " + count + " blocks.");
    }

}
