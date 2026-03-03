package com.borderrank.battle.manager;

import java.util.*;

/**
 * Manages matchmaking queues for solo and team matches.
 * Provides queue operations and match formation logic.
 */
public class QueueManager {
    
    private final Set<UUID> soloQueue = new HashSet<>();
    private final Map<Integer, Set<UUID>> teamQueue = new HashMap<>();
    private int nextTeamId = 0;

    /**
     * Adds a player to the solo queue.
     *
     * @param playerId the UUID of the player
     */
    public void addToSoloQueue(UUID playerId) {
        soloQueue.add(playerId);
    }

    /**
     * Removes a player from the solo queue.
     *
     * @param playerId the UUID of the player
     * @return true if the player was in the queue, false otherwise
     */
    public boolean removeFromSoloQueue(UUID playerId) {
        return soloQueue.remove(playerId);
    }

    /**
     * Adds a team to the team queue.
     * Creates a new team ID for the group of players.
     *
     * @param teamMembers a set of player UUIDs forming the team
     * @return the team ID assigned to this team
     */
    public int addToTeamQueue(Set<UUID> teamMembers) {
        int teamId = nextTeamId++;
        teamQueue.put(teamId, new HashSet<>(teamMembers));
        return teamId;
    }

    /**
     * Removes a team from the team queue.
     *
     * @param teamId the ID of the team to remove
     * @return true if the team was in the queue, false otherwise
     */
    public boolean removeTeamFromQueue(int teamId) {
        return teamQueue.remove(teamId) != null;
    }

    /**
     * Removes a player from all queues (solo and team).
     *
     * @param playerId the UUID of the player
     */
    public void removeFromQueues(UUID playerId) {
        soloQueue.remove(playerId);
        
        var teamIdsToRemove = new ArrayList<Integer>();
        for (var entry : teamQueue.entrySet()) {
            if (entry.getValue().remove(playerId)) {
                if (entry.getValue().isEmpty()) {
                    teamIdsToRemove.add(entry.getKey());
                }
            }
        }
        
        for (int teamId : teamIdsToRemove) {
            teamQueue.remove(teamId);
        }
    }

    /**
     * Checks if a player is in any queue.
     *
     * @param playerId the UUID of the player
     * @return true if the player is in solo queue or on a team in team queue
     */
    public boolean isInQueue(UUID playerId) {
        if (soloQueue.contains(playerId)) {
            return true;
        }
        
        return teamQueue.values().stream()
                .anyMatch(team -> team.contains(playerId));
    }

    /**
     * Checks if a player is in the solo queue.
     *
     * @param playerId the UUID of the player
     * @return true if the player is in solo queue
     */
    public boolean isInSoloQueue(UUID playerId) {
        return soloQueue.contains(playerId);
    }

    /**
     * Attempts to form a solo match with the specified minimum number of players.
     * If successful, removes matched players from the queue.
     *
     * @param minPlayers the minimum number of players required for a match
     * @return a set of matched player UUIDs, or an empty set if not enough players
     */
    public Set<UUID> trySoloMatch(int minPlayers) {
        if (soloQueue.size() < minPlayers) {
            return new HashSet<>();
        }
        
        Set<UUID> matched = new HashSet<>();
        var iterator = soloQueue.iterator();
        
        for (int i = 0; i < minPlayers && iterator.hasNext(); i++) {
            matched.add(iterator.next());
            iterator.remove();
        }
        
        return matched;
    }

    /**
     * Attempts to form a team match with the specified minimum number of teams.
     * If successful, removes matched teams from the queue.
     *
     * @param minTeams the minimum number of teams required for a match
     * @return a set of matched team IDs, or an empty set if not enough teams
     */
    public Set<Integer> tryTeamMatch(int minTeams) {
        if (teamQueue.size() < minTeams) {
            return new HashSet<>();
        }
        
        Set<Integer> matched = new HashSet<>();
        var iterator = teamQueue.keySet().iterator();
        
        for (int i = 0; i < minTeams && iterator.hasNext(); i++) {
            int teamId = iterator.next();
            matched.add(teamId);
            iterator.remove();
        }
        
        return matched;
    }

    /**
     * Gets the current size of the solo queue.
     *
     * @return the number of players in solo queue
     */
    public int getSoloQueueSize() {
        return soloQueue.size();
    }

    /**
     * Gets the current size of the team queue.
     *
     * @return the number of teams in team queue
     */
    public int getTeamQueueSize() {
        return teamQueue.size();
    }

    /**
     * Gets the total number of players in the team queue.
     *
     * @return the total player count across all teams in queue
     */
    public int getTeamQueuePlayerCount() {
        return teamQueue.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    /**
     * Gets a copy of the solo queue.
     *
     * @return a new set containing all solo queue players
     */
    public Set<UUID> getSoloQueuePlayers() {
        return new HashSet<>(soloQueue);
    }

    /**
     * Gets the members of a team by team ID.
     *
     * @param teamId the ID of the team
     * @return a set of player UUIDs on the team, or an empty set if team not found
     */
    public Set<UUID> getTeamMembers(int teamId) {
        var members = teamQueue.get(teamId);
        return members != null ? new HashSet<>(members) : new HashSet<>();
    }

    /**
     * Clears all queues.
     */
    public void clearQueues() {
        soloQueue.clear();
        teamQueue.clear();
        nextTeamId = 0;
    }

    /**
     * Gets an estimate of wait time in seconds based on queue position.
     *
     * @param playerId the UUID of the player
     * @return estimated wait time in seconds, or -1 if player not in queue
     */
    public long getEstimatedWaitTime(UUID playerId) {
        // Estimate: ~30 seconds per player in queue before them, or ~60 seconds per team
        if (soloQueue.contains(playerId)) {
            int position = new ArrayList<>(soloQueue).indexOf(playerId);
            return (position + 1) * 30L;
        }
        
        int teamPosition = 0;
        for (int teamId : teamQueue.keySet()) {
            if (teamQueue.get(teamId).contains(playerId)) {
                return (teamPosition + 1) * 60L;
            }
            teamPosition++;
        }
        
        return -1;
    }
}
