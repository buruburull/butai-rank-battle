package com.borderrank.battle.arena;

import com.borderrank.battle.model.MapData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchManager {

    private final Map<Integer, ArenaInstance> activeMatches;
    private int nextMatchId;

    public MatchManager() {
        this.activeMatches = new HashMap<>();
        this.nextMatchId = 1;
    }

    public int createSoloMatch(Set<UUID> players, MapData mapData) {
        return createSoloMatch(players, mapData, false);
    }

    public int createSoloMatch(Set<UUID> players, MapData mapData, boolean practice) {
        if (players.size() < 2) return -1;
        int matchId = nextMatchId++;
        ArenaInstance arena = new ArenaInstance(matchId, mapData, 300);
        arena.setPractice(practice);
        for (UUID uuid : players) {
            arena.addPlayer(uuid);
        }
        activeMatches.put(matchId, arena);
        arena.start();
        return matchId;
    }

    public int createTeamMatch(Map<Integer, Set<UUID>> teamData, MapData mapData) {
        if (teamData.size() < 2) return -1;
        int matchId = nextMatchId++;
        ArenaInstance arena = new ArenaInstance(matchId, mapData, 600, teamData);
        activeMatches.put(matchId, arena);
        arena.start();
        return matchId;
    }

    public ArenaInstance getPlayerMatch(UUID uuid) {
        for (ArenaInstance arena : activeMatches.values()) {
            if (arena.getPlayers().contains(uuid)) return arena;
        }
        return null;
    }

    public boolean isInMatch(UUID uuid) {
        return getPlayerMatch(uuid) != null;
    }

    public void endMatch(int matchId) {
        ArenaInstance arena = activeMatches.remove(matchId);
        if (arena != null) arena.end();
    }

    public void tick() {
        Set<Integer> matchIds = new HashSet<>(activeMatches.keySet());
        for (int matchId : matchIds) {
            ArenaInstance arena = activeMatches.get(matchId);
            if (arena != null) {
                arena.tick();
                if (arena.getState() == ArenaInstance.ArenaState.FINISHED) {
                    activeMatches.remove(matchId);
                }
            }
        }
    }

    /**
     * Gets the match a player is spectating, or null if not spectating.
     */
    public ArenaInstance getSpectatingMatch(UUID uuid) {
        for (ArenaInstance arena : activeMatches.values()) {
            if (arena.isSpectator(uuid)) return arena;
        }
        return null;
    }

    /**
     * Check if a player is spectating any match.
     */
    public boolean isSpectating(UUID uuid) {
        return getSpectatingMatch(uuid) != null;
    }

    public ArenaInstance getMatch(int matchId) { return activeMatches.get(matchId); }
    public java.util.Collection<ArenaInstance> getAllActiveMatches() { return activeMatches.values(); }
    public int getActiveMatchCount() { return activeMatches.size(); }
    public int getTotalPlayersInMatches() {
        int total = 0;
        for (ArenaInstance arena : activeMatches.values()) total += arena.getPlayers().size();
        return total;
    }
}
