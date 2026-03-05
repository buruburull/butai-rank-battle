package com.borderrank.battle.arena;

import org.bukkit.Bukkit;

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

    public int createSoloMatch(Set<UUID> players, String mapName) {
        if (players.size() < 2) return -1;
        int matchId = nextMatchId++;
        ArenaInstance arena = new ArenaInstance(matchId, mapName, 300);
        for (UUID uuid : players) {
            arena.addPlayer(uuid);
        }
        activeMatches.put(matchId, arena);
        arena.start();
        return matchId;
    }

    public int createTeamMatch(Map<Integer, Set<UUID>> teamData, String mapName) {
        if (teamData.size() < 2) return -1;
        int matchId = nextMatchId++;
        ArenaInstance arena = new ArenaInstance(matchId, mapName, 600, teamData);
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

    public ArenaInstance getMatch(int matchId) { return activeMatches.get(matchId); }
    public int getActiveMatchCount() { return activeMatches.size(); }
    public int getTotalPlayersInMatches() {
        int total = 0;
        for (ArenaInstance arena : activeMatches.values()) total += arena.getPlayers().size();
        return total;
    }
}
