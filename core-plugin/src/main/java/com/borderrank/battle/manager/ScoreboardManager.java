package com.borderrank.battle.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * Manages sidebar scoreboards for match display.
 * Uses Bukkit Scoreboard API with team-based line management to minimize flicker.
 */
public class ScoreboardManager {
    
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private final Map<UUID, Objective> playerObjectives = new HashMap<>();

    /**
     * Creates a new sidebar scoreboard for a player displaying match information.
     *
     * @param player the player to create the scoreboard for
     * @param mapName the name of the map being played
     * @param timeRemaining the remaining match time in seconds
     */
    public void createMatchScoreboard(Player player, String mapName, int timeRemaining) {
        UUID playerId = player.getUniqueId();
        
        // Create new scoreboard
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(
                "brb_match",
                "dummy",
                "§6Border Rank Battle"
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        
        // Store references
        playerScoreboards.put(playerId, scoreboard);
        playerObjectives.put(playerId, objective);
        
        // Set player's scoreboard
        player.setScoreboard(scoreboard);
        
        // Initialize scoreboard lines
        updateScoreboard(player, 0, 0, timeRemaining, 0.0);
    }

    /**
     * Updates an existing scoreboard with match statistics.
     * Uses team-based line management to prevent flickering.
     *
     * @param player the player whose scoreboard to update
     * @param kills the player's current kill count
     * @param alive the number of alive players/teams remaining
     * @param timeRemaining the remaining match time in seconds
     * @param trionLeak the current trion leak rate per second
     */
    public void updateScoreboard(Player player, int kills, int alive, int timeRemaining, double trionLeak) {
        UUID playerId = player.getUniqueId();
        Objective objective = playerObjectives.get(playerId);
        Scoreboard scoreboard = playerScoreboards.get(playerId);
        
        if (objective == null || scoreboard == null) {
            return;
        }
        
        // Format time as MM:SS
        String timeFormatted = String.format("%02d:%02d", timeRemaining / 60, timeRemaining % 60);
        
        // Clear existing scores to prepare for update
        for (String entry : scoreboard.getEntries()) {
            if (!entry.startsWith("§")) {
                scoreboard.resetScores(entry);
            }
        }
        
        // Line 1: Time remaining (score 9)
        setScoreboardLine(scoreboard, objective, "§e⏱ Time: §f" + timeFormatted, 9);
        
        // Line 2: Players alive (score 8)
        setScoreboardLine(scoreboard, objective, "§b👥 Alive: §f" + alive, 8);
        
        // Line 3: Kills (score 7)
        setScoreboardLine(scoreboard, objective, "§c⚔ Kills: §f" + kills, 7);
        
        // Line 4: Spacer (score 6)
        setScoreboardLine(scoreboard, objective, "", 6);
        
        // Line 5: Trion leak (score 5)
        String leakColor = trionLeak > 1.0 ? "§c" : "§e";
        setScoreboardLine(scoreboard, objective, leakColor + "Trion Leak: §f" + String.format("%.1f", trionLeak), 5);
        
        // Line 6: Spacer (score 4)
        setScoreboardLine(scoreboard, objective, "  ", 4);
        
        // Line 7: Footer (score 3)
        setScoreboardLine(scoreboard, objective, "§7world.borderrank.battle", 3);
    }

    /**
     * Sets or updates a single line in the scoreboard using team-based management.
     * This approach minimizes flickering when updating.
     *
     * @param scoreboard the scoreboard to update
     * @param objective the objective to add the score to
     * @param lineText the text to display on this line
     * @param score the score value (determines line position)
     */
    private void setScoreboardLine(Scoreboard scoreboard, Objective objective, String lineText, int score) {
        String entry = "line_" + score;
        
        // Remove old entry if it exists
        if (scoreboard.getEntries().contains(entry)) {
            scoreboard.resetScores(entry);
        }
        
        // Create or get team for this line
        Team team = scoreboard.getTeam(entry);
        if (team == null) {
            team = scoreboard.registerNewTeam(entry);
        }
        
        // Clear existing members and set new ones
        for (String member : new HashSet<>(team.getEntries())) {
            team.removeEntry(member);
        }
        team.addEntry(lineText);
        
        // Set score
        objective.getScore(lineText).setScore(score);
    }

    /**
     * Removes and clears a player's scoreboard.
     *
     * @param player the player whose scoreboard to remove
     */
    public void removeScoreboard(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Restore default scoreboard
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        
        playerScoreboards.remove(playerId);
        playerObjectives.remove(playerId);
    }

    /**
     * Checks if a player has an active scoreboard.
     *
     * @param playerId the UUID of the player
     * @return true if a scoreboard is active for the player
     */
    public boolean hasScoreboard(UUID playerId) {
        return playerScoreboards.containsKey(playerId);
    }

    /**
     * Clears all player scoreboards.
     */
    public void clearAllScoreboards() {
        for (UUID playerId : new ArrayList<>(playerScoreboards.keySet())) {
            var player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                removeScoreboard(player);
            }
        }
    }

    /**
     * Gets the number of active scoreboards.
     *
     * @return the count of active scoreboards
     */
    public int getActiveScoreboardCount() {
        return playerScoreboards.size();
    }
}
