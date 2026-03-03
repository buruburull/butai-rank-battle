package com.borderrank.battle.arena;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.manager.ScoreboardManager;
import com.borderrank.battle.manager.TrionManager;
import com.borderrank.battle.model.Trigger;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single match instance.
 * Manages match state, players, kills, and progression.
 */
public class ArenaInstance {

    /**
     * Enum for arena states.
     */
    public enum ArenaState {
        WAITING, COUNTDOWN, ACTIVE, ENDING, FINISHED
    }

    private final int matchId;
    private ArenaState state;
    private final Set<UUID> players;
    private final Set<UUID> alivePlayers;
    private final Map<UUID, Integer> kills;
    private final String mapName;
    private final long startTime;
    private final int timeLimitSec;
    private int countdownRemaining;
    private long lastTickTime;

    /**
     * Create a new arena instance.
     */
    public ArenaInstance(int matchId, String mapName, int timeLimitSec) {
        this.matchId = matchId;
        this.mapName = mapName;
        this.timeLimitSec = timeLimitSec;
        this.state = ArenaState.WAITING;
        this.players = new HashSet<>();
        this.alivePlayers = new HashSet<>();
        this.kills = new HashMap<>();
        this.startTime = System.currentTimeMillis();
        this.countdownRemaining = 10; // Default 10 second countdown
        this.lastTickTime = System.currentTimeMillis();
    }

    /**
     * Start the match.
     */
    public void start() {
        BRBPlugin plugin = BRBPlugin.getInstance();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        TrionManager trionManager = plugin.getTrionManager();
        ScoreboardManager scoreboardManager = plugin.getScoreboardManager();

        state = ArenaState.COUNTDOWN;
        alivePlayers.addAll(players);

        // Initialize kills tracking
        for (UUID uuid : players) {
            kills.put(uuid, 0);
        }

        // Teleport players to spawn points and give items
        int spawnIndex = 0;
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // Teleport to spawn point (placeholder)
                Location spawnLoc = new Location(player.getWorld(), spawnIndex * 10, 64, 0);
                player.teleport(spawnLoc);

                // Clear inventory and give trigger items
                player.getInventory().clear();
                List<Trigger> loadout = loadoutManager.getLoadout(uuid);
                
                // Add triggers to hotbar (slots 0-7)
                for (int i = 0; i < Math.min(loadout.size(), 8); i++) {
                    Trigger trigger = loadout.get(i);
                    if (trigger != null) {
                        ItemStack item = new ItemStack(trigger.getMaterial());
                        player.getInventory().setItem(i, item);
                    }
                }

                // Initialize trion for this match
                trionManager.initializePlayer(uuid, 1000);

                // Disable natural regen
                player.setHealthScale(20);

                MessageUtil.sendInfoMessage(player, "Match starting! Countdown: " + countdownRemaining);
                
                spawnIndex++;
            }
        }
    }

    /**
     * Record a kill.
     */
    public void onKill(UUID killer, UUID victim) {
        if (killer != null && kills.containsKey(killer)) {
            kills.put(killer, kills.get(killer) + 1);
        }

        // Remove victim from alive players
        alivePlayers.remove(victim);

        // Check end condition
        if (alivePlayers.size() <= 1) {
            end();
        }
    }

    /**
     * Tick the match (called every second).
     */
    public void tick() {
        long currentTime = System.currentTimeMillis();
        long deltaMs = currentTime - lastTickTime;
        lastTickTime = currentTime;

        switch (state) {
            case COUNTDOWN -> tickCountdown();
            case ACTIVE -> tickActive(deltaMs);
            case ENDING -> tickEnding();
        }
    }

    /**
     * Tick countdown phase.
     */
    private void tickCountdown() {
        countdownRemaining--;

        if (countdownRemaining <= 0) {
            state = ArenaState.ACTIVE;
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    MessageUtil.sendSuccessMessage(player, "Match started!");
                }
            }
        } else if (countdownRemaining <= 5) {
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    MessageUtil.sendInfoMessage(player, "Starting in " + countdownRemaining + "...");
                }
            }
        }
    }

    /**
     * Tick active match phase.
     */
    private void tickActive(long deltaMs) {
        // Update scoreboard
        BRBPlugin plugin = BRBPlugin.getInstance();
        ScoreboardManager scoreboardManager = plugin.getScoreboardManager();

        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                scoreboardManager.updatePlayerScore(player, kills.getOrDefault(uuid, 0));
            }
        }

        // Check time limit
        long elapsedSec = (System.currentTimeMillis() - startTime) / 1000;
        if (elapsedSec >= timeLimitSec) {
            end();
        }
    }

    /**
     * Tick ending phase.
     */
    private void tickEnding() {
        state = ArenaState.FINISHED;
        // Cleanup handled in end()
    }

    /**
     * End the match.
     */
    public void end() {
        state = ArenaState.ENDING;

        // Calculate placements and RP changes
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        // Get sorted players by kill count
        List<Map.Entry<UUID, Integer>> sortedPlayers = new ArrayList<>(kills.entrySet());
        sortedPlayers.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        // Compute RP changes and save results
        int placement = 1;
        for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
            UUID uuid = entry.getKey();
            int playerKills = entry.getValue();

            // Calculate RP based on placement and kills
            int rpGain = (10 - placement) + playerKills;
            rankManager.addPlayerRP(uuid, "OVERALL", rpGain);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                MessageUtil.sendSuccessMessage(player, 
                    "Match ended! Placement: #" + placement + " | Kills: " + playerKills + 
                    " | RP Gained: +" + rpGain);
                
                // Teleport to lobby
                Location lobbyLoc = new Location(player.getWorld(), 0, 100, 0);
                player.teleport(lobbyLoc);
            }

            placement++;
        }

        // Send summary
        for (UUID uuid : players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                MessageUtil.sendInfoMessage(player, "=== Match Summary ===");
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    Map.Entry<UUID, Integer> entry = sortedPlayers.get(i);
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p != null) {
                        MessageUtil.sendInfoMessage(player, 
                            "#" + (i + 1) + " " + p.getName() + " - " + entry.getValue() + " kills");
                    }
                }
            }
        }
    }

    /**
     * Get all alive players.
     */
    public Set<UUID> getAlivePlayers() {
        return new HashSet<>(alivePlayers);
    }

    /**
     * Add a player to the match.
     */
    public void addPlayer(UUID uuid) {
        players.add(uuid);
    }

    /**
     * Get match ID.
     */
    public int getMatchId() {
        return matchId;
    }

    /**
     * Get current state.
     */
    public ArenaState getState() {
        return state;
    }

    /**
     * Get all players in match.
     */
    public Set<UUID> getPlayers() {
        return new HashSet<>(players);
    }

    /**
     * Get player kills.
     */
    public int getPlayerKills(UUID uuid) {
        return kills.getOrDefault(uuid, 0);
    }

    /**
     * Get map name.
     */
    public String getMapName() {
        return mapName;
    }
}
