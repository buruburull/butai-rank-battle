package com.borderrank.battle.manager;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * Manages sidebar scoreboards and boss bars for match display.
 */
public class ScoreboardManager {

    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Objective> playerObjectives = new HashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new HashMap<>();

    /**
     * Creates a new sidebar scoreboard and boss bar for a player.
     */
    public void createMatchScoreboard(Player player, String mapName, int timeLimitSec) {
        UUID playerId = player.getUniqueId();

        // Create scoreboard
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "brb_match", "dummy", "\u00a76\u00a7l\u2694 Border Rank Battle \u2694"
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        playerScoreboards.put(playerId, scoreboard);
        playerObjectives.put(playerId, objective);
        player.setScoreboard(scoreboard);

        // Create boss bar (match timer)
        BossBar bossBar = Bukkit.createBossBar(
                "\u00a7e\u00a7l\u23f1 \u6b8b\u308a\u6642\u9593: " + formatTime(timeLimitSec),
                BarColor.YELLOW, BarStyle.SEGMENTED_10
        );
        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);
        playerBossBars.put(playerId, bossBar);

        // Initialize lines
        updateFullScoreboard(player, mapName, 0, 0, timeLimitSec, 1000.0, 1000);
    }

    /**
     * Updates the full scoreboard with all match info.
     */
    public void updateFullScoreboard(Player player, String mapName, int kills, int aliveCount,
                                     int timeRemainingSec, double trion, int maxTrion) {
        UUID playerId = player.getUniqueId();
        Objective objective = playerObjectives.get(playerId);
        Scoreboard scoreboard = playerScoreboards.get(playerId);
        if (objective == null || scoreboard == null) return;

        // Clear old entries
        for (String entry : new HashSet<>(scoreboard.getEntries())) {
            scoreboard.resetScores(entry);
        }

        String timeFormatted = formatTime(timeRemainingSec);

        // Trion bar visualization (10 chars)
        int trionPct = maxTrion > 0 ? (int) (trion / maxTrion * 10) : 0;
        StringBuilder trionBar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            trionBar.append(i < trionPct ? "\u00a7a\u2588" : "\u00a78\u2588");
        }

        // Trion color based on level
        String trionColor = trion > 200 ? "\u00a7a" : (trion > 100 ? "\u00a7e" : "\u00a7c");

        // Set lines (higher score = higher position)
        setLine(scoreboard, objective, "\u00a78\u00a7m-----------", 12);
        setLine(scoreboard, objective, "\u00a7f\u00a7l\u2316 Map: \u00a77" + mapName, 11);
        setLine(scoreboard, objective, "\u00a7e\u23f1 Time: \u00a7f" + timeFormatted, 10);
        setLine(scoreboard, objective, " ", 9);
        setLine(scoreboard, objective, "\u00a7c\u2694 Kills: \u00a7f" + kills, 8);
        setLine(scoreboard, objective, "\u00a7b\u2764 Alive: \u00a7f" + aliveCount, 7);
        setLine(scoreboard, objective, "  ", 6);
        setLine(scoreboard, objective, "\u00a7dTrion: " + trionColor + (int) trion + "\u00a77/" + maxTrion, 5);
        setLine(scoreboard, objective, trionBar.toString(), 4);
        setLine(scoreboard, objective, "   ", 3);
        setLine(scoreboard, objective, "\u00a78borderrank.battle", 2);
    }

    /**
     * Updates boss bar with remaining time.
     */
    public void updateBossBar(Player player, int timeRemainingSec, int timeLimitSec) {
        UUID playerId = player.getUniqueId();
        BossBar bossBar = playerBossBars.get(playerId);
        if (bossBar == null) return;

        double progress = timeLimitSec > 0 ? (double) timeRemainingSec / timeLimitSec : 0;
        progress = Math.max(0, Math.min(1.0, progress));
        bossBar.setProgress(progress);

        // Color changes based on remaining time
        BarColor color;
        if (progress > 0.5) {
            color = BarColor.GREEN;
        } else if (progress > 0.2) {
            color = BarColor.YELLOW;
        } else {
            color = BarColor.RED;
        }
        bossBar.setColor(color);
        bossBar.setTitle("\u00a7e\u00a7l\u23f1 \u6b8b\u308a\u6642\u9593: \u00a7f" + formatTime(timeRemainingSec));
    }

    /**
     * Updates just the kills line quickly.
     */
    public void updatePlayerScore(Player player, int kills) {
        UUID playerId = player.getUniqueId();
        Objective objective = playerObjectives.get(playerId);
        Scoreboard scoreboard = playerScoreboards.get(playerId);
        if (objective == null || scoreboard == null) return;

        setLine(scoreboard, objective, "\u00a7c\u2694 Kills: \u00a7f" + kills, 8);
    }

    /**
     * Removes scoreboard and boss bar for a player.
     */
    public void removeScoreboard(Player player) {
        UUID playerId = player.getUniqueId();

        // Restore default scoreboard
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        playerScoreboards.remove(playerId);
        playerObjectives.remove(playerId);

        // Remove boss bar
        BossBar bossBar = playerBossBars.remove(playerId);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    /**
     * Sets a single scoreboard line using unique invisible-char entries.
     */
    private void setLine(Scoreboard scoreboard, Objective objective, String text, int score) {
        // Use unique entry key per score position
        String key = "\u00a7" + Integer.toHexString(score) + "\u00a7r";

        // Get or create team for this line
        String teamName = "line_" + score;
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.addEntry(key);
        }

        // Split text into prefix (max 64 chars) and suffix
        if (text.length() <= 64) {
            team.setPrefix(text);
            team.setSuffix("");
        } else {
            team.setPrefix(text.substring(0, 64));
            team.setSuffix(text.substring(64, Math.min(text.length(), 128)));
        }

        objective.getScore(key).setScore(score);
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public boolean hasScoreboard(UUID playerId) {
        return playerScoreboards.containsKey(playerId);
    }

    public void clearAllScoreboards() {
        for (UUID playerId : new ArrayList<>(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removeScoreboard(player);
            }
        }
    }

    public int getActiveScoreboardCount() {
        return playerScoreboards.size();
    }
}
