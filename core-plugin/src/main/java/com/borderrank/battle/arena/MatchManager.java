package com.borderrank.battle.arena;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates all active matches.
 * Manages match creation, player lookup, and match lifecycle.
 */
public class MatchManager {

    private final Map<Integer, ArenaInstance> activeMatches;
    private int nextMatchId;

    /**
     * Create a new match manager.
     */
    public MatchManager() {
        this.activeMatches = new HashMap<>();
        this.nextMatchId = 1;
    }

    /**
     * Create a solo match with the given players.
     */
    public int createSoloMatch(Set<UUID> players, String mapName) {
        if (players.size() < 2) {
            return -1; // Invalid match
        }

        int matchId = nextMatchId++;
        ArenaInstance arena = new ArenaInstance(matchId, mapName, 300); // 5 minute time limit

        for (UUID uuid : players) {
            arena.addPlayer(uuid);
        }

        activeMatches.put(matchId, arena);
        arena.start();

        return matchId;
    }

    /**
     * Create a team match with the given teams.
     */
    public int createTeamMatch(Map<Integer, Set<UUID>> teams, String mapName) {
        if (teams.size() < 2) {
            return -1; // Invalid match
        }

        int matchId = nextMatchId++;
        ArenaInstance arena = new ArenaInstance(matchId, mapName, 600); // 10 minute time limit

        for (Set<UUID> teamPlayers : teams.values()) {
            for (UUID uuid : teamPlayers) {
                arena.addPlayer(uuid);
            }
        }

        activeMatches.put(matchId, arena);
        arena.start();

        return matchId;
    }

    /**
     * Get the match a player is currently in.
     */
    public ArenaInstance getPlayerMatch(UUID uuid) {
        for (ArenaInstance arena : activeMatches.values()) {
            if (arena.getPlayers().contains(uuid)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Check if a player is currently in a match.
     */
    public boolean isInMatch(UUID uuid) {
        return getPlayerMatch(uuid) != null;
    }

    /**
     * End a match and clean up.
     */
    public void endMatch(int matchId) {
        ArenaInstance arena = activeMatches.remove(matchId);
        if (arena != null) {
            arena.end();
        }
    }

    /**
     * Tick all active matches (called every second).
     * This should be called from the plugin's main tick task.
     */
    public void tick() {
        // Iterate over a copy to avoid concurrent modification
        Set<Integer> matchIds = new HashSet<>(activeMatches.keySet());

        for (int matchId : matchIds) {
            ArenaInstance arena = activeMatches.get(matchId);
            if (arena != null) {
                arena.tick();

                // Remove finished matches
                if (arena.getState() == ArenaInstance.ArenaState.FINISHED) {
                    activeMatches.remove(matchId);
                }
            }
        }
    }

    /**
     * Get an active match by ID.
     */
    public ArenaInstance getMatch(int matchId) {
        return activeMatches.get(matchId);
    }

    /**
     * Get the number of active matches.
     */
    public int getActiveMatchCount() {
        return activeMatches.size();
    }

    /**
     * Get total number of players in all active matches.
     */
    public int getTotalPlayersInMatches() {
        int total = 0;
        for (ArenaInstance arena : activeMatches.values()) {
            total += arena.getPlayers().size();
        }
        return total;
    }
}
