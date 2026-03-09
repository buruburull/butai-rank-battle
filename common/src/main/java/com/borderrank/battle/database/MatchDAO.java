package com.borderrank.battle.database;

import com.borderrank.battle.model.WeaponType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data access object for match history and match results.
 */
public class MatchDAO {
    private final DatabaseManager databaseManager;

    public MatchDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Inserts a new match_history record and returns the generated match_id.
     *
     * @param matchType "solo" or "team"
     * @param mapName   the arena/map name
     * @param seasonId  the active season ID
     * @return the generated match_id, or -1 on failure
     */
    public int createMatch(String matchType, String mapName, int seasonId) throws SQLException {
        String sql = "INSERT INTO match_history (match_type, map_name, season_id, started_at) VALUES (?, ?, ?, NOW())";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, matchType);
            stmt.setString(2, mapName);
            stmt.setInt(3, seasonId);
            stmt.executeUpdate();

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return -1;
    }

    /**
     * Finalizes a match by setting ended_at and duration_sec.
     *
     * @param dbMatchId   the DB match_id
     * @param durationSec the match duration in seconds
     */
    public void endMatch(int dbMatchId, int durationSec) throws SQLException {
        String sql = "UPDATE match_history SET ended_at = NOW(), duration_sec = ? WHERE match_id = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, durationSec);
            stmt.setInt(2, dbMatchId);
            stmt.executeUpdate();
        }
    }

    /**
     * Inserts a player's result for a specific match.
     */
    public void insertResult(int dbMatchId, UUID playerUuid, Integer teamId,
                             WeaponType weaponType, int kills, int deaths,
                             boolean survived, int rpChange, int placement) throws SQLException {
        String sql = "INSERT INTO match_results (match_id, uuid, team_id, weapon_type, kills, deaths, survived, rp_change, placement) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, dbMatchId);
            stmt.setString(2, playerUuid.toString());
            if (teamId != null) {
                stmt.setInt(3, teamId);
            } else {
                stmt.setNull(3, java.sql.Types.INTEGER);
            }
            stmt.setString(4, weaponType.name());
            stmt.setInt(5, kills);
            stmt.setInt(6, deaths);
            stmt.setBoolean(7, survived);
            stmt.setInt(8, rpChange);
            stmt.setInt(9, placement);
            stmt.executeUpdate();
        }
    }

    /**
     * Gets recent match history for a specific player.
     *
     * @param playerUuid the player UUID
     * @param limit      max number of matches
     * @return list of MatchRecord objects, most recent first
     */
    public List<MatchRecord> getPlayerHistory(UUID playerUuid, int limit) throws SQLException {
        String sql = "SELECT mh.match_id, mh.match_type, mh.map_name, mh.started_at, mh.duration_sec, "
                   + "mr.weapon_type, mr.kills, mr.deaths, mr.survived, mr.rp_change, mr.placement "
                   + "FROM match_results mr "
                   + "JOIN match_history mh ON mr.match_id = mh.match_id "
                   + "WHERE mr.uuid = ? AND mh.ended_at IS NOT NULL "
                   + "ORDER BY mh.started_at DESC "
                   + "LIMIT ?";

        List<MatchRecord> records = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    records.add(new MatchRecord(
                        rs.getInt("match_id"),
                        rs.getString("match_type"),
                        rs.getString("map_name"),
                        rs.getTimestamp("started_at"),
                        rs.getInt("duration_sec"),
                        rs.getString("weapon_type"),
                        rs.getInt("kills"),
                        rs.getInt("deaths"),
                        rs.getBoolean("survived"),
                        rs.getInt("rp_change"),
                        rs.getInt("placement")
                    ));
                }
            }
        }
        return records;
    }

    /**
     * Gets recent matches globally (for admin view).
     *
     * @param limit max number of matches
     * @return list of RecentMatch objects, most recent first
     */
    public List<RecentMatch> getRecentMatches(int limit) throws SQLException {
        String sql = "SELECT mh.match_id, mh.match_type, mh.map_name, mh.started_at, mh.duration_sec, "
                   + "COUNT(mr.uuid) as player_count "
                   + "FROM match_history mh "
                   + "LEFT JOIN match_results mr ON mh.match_id = mr.match_id "
                   + "WHERE mh.ended_at IS NOT NULL "
                   + "GROUP BY mh.match_id, mh.match_type, mh.map_name, mh.started_at, mh.duration_sec "
                   + "ORDER BY mh.started_at DESC "
                   + "LIMIT ?";

        List<RecentMatch> matches = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    matches.add(new RecentMatch(
                        rs.getInt("match_id"),
                        rs.getString("match_type"),
                        rs.getString("map_name"),
                        rs.getTimestamp("started_at"),
                        rs.getInt("duration_sec"),
                        rs.getInt("player_count")
                    ));
                }
            }
        }
        return matches;
    }

    /**
     * Gets participant names for a list of match IDs.
     *
     * @param matchIds the list of match IDs
     * @return map of matchId -> list of MatchParticipant (uuid, playerName, kills, deaths, placement)
     */
    public Map<Integer, List<MatchParticipant>> getMatchParticipants(List<Integer> matchIds) throws SQLException {
        if (matchIds.isEmpty()) return new HashMap<>();

        // Build IN clause
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < matchIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }

        String sql = "SELECT mr.match_id, mr.uuid, p.player_name, mr.kills, mr.deaths, mr.placement "
                   + "FROM match_results mr "
                   + "JOIN players p ON mr.uuid = p.uuid "
                   + "WHERE mr.match_id IN (" + placeholders + ") "
                   + "ORDER BY mr.match_id, mr.placement ASC";

        Map<Integer, List<MatchParticipant>> result = new HashMap<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < matchIds.size(); i++) {
                stmt.setInt(i + 1, matchIds.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int matchId = rs.getInt("match_id");
                    result.computeIfAbsent(matchId, k -> new ArrayList<>()).add(
                        new MatchParticipant(
                            rs.getString("uuid"),
                            rs.getString("player_name"),
                            rs.getInt("kills"),
                            rs.getInt("deaths"),
                            rs.getInt("placement")
                        )
                    );
                }
            }
        }
        return result;
    }

    /**
     * Gets total match count and stats.
     */
    public MatchStats getMatchStats() throws SQLException {
        String sql = "SELECT COUNT(*) as total, "
                   + "SUM(CASE WHEN match_type = 'solo' THEN 1 ELSE 0 END) as solo_count, "
                   + "SUM(CASE WHEN match_type = 'team' THEN 1 ELSE 0 END) as team_count, "
                   + "AVG(duration_sec) as avg_duration "
                   + "FROM match_history WHERE ended_at IS NOT NULL";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return new MatchStats(
                    rs.getInt("total"),
                    rs.getInt("solo_count"),
                    rs.getInt("team_count"),
                    rs.getInt("avg_duration")
                );
            }
        }
        return new MatchStats(0, 0, 0, 0);
    }

    /**
     * Summary of a recent match (no per-player details).
     */
    public static class RecentMatch {
        private final int matchId;
        private final String matchType;
        private final String mapName;
        private final Timestamp startedAt;
        private final int durationSec;
        private final int playerCount;

        public RecentMatch(int matchId, String matchType, String mapName,
                           Timestamp startedAt, int durationSec, int playerCount) {
            this.matchId = matchId;
            this.matchType = matchType;
            this.mapName = mapName;
            this.startedAt = startedAt;
            this.durationSec = durationSec;
            this.playerCount = playerCount;
        }

        public int getMatchId() { return matchId; }
        public String getMatchType() { return matchType; }
        public String getMapName() { return mapName; }
        public Timestamp getStartedAt() { return startedAt; }
        public int getDurationSec() { return durationSec; }
        public int getPlayerCount() { return playerCount; }
    }

    /**
     * Aggregate match statistics.
     */
    public static class MatchStats {
        private final int totalMatches;
        private final int soloMatches;
        private final int teamMatches;
        private final int avgDurationSec;

        public MatchStats(int totalMatches, int soloMatches, int teamMatches, int avgDurationSec) {
            this.totalMatches = totalMatches;
            this.soloMatches = soloMatches;
            this.teamMatches = teamMatches;
            this.avgDurationSec = avgDurationSec;
        }

        public int getTotalMatches() { return totalMatches; }
        public int getSoloMatches() { return soloMatches; }
        public int getTeamMatches() { return teamMatches; }
        public int getAvgDurationSec() { return avgDurationSec; }
    }

    /**
     * Participant info for a match.
     */
    public static class MatchParticipant {
        private final String uuid;
        private final String playerName;
        private final int kills;
        private final int deaths;
        private final int placement;

        public MatchParticipant(String uuid, String playerName, int kills, int deaths, int placement) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.kills = kills;
            this.deaths = deaths;
            this.placement = placement;
        }

        public String getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public int getPlacement() { return placement; }
    }

    /**
     * Simple record class holding a single player's match result joined with match info.
     */
    public static class MatchRecord {
        private final int matchId;
        private final String matchType;
        private final String mapName;
        private final Timestamp startedAt;
        private final int durationSec;
        private final String weaponType;
        private final int kills;
        private final int deaths;
        private final boolean survived;
        private final int rpChange;
        private final int placement;

        public MatchRecord(int matchId, String matchType, String mapName, Timestamp startedAt,
                           int durationSec, String weaponType, int kills, int deaths,
                           boolean survived, int rpChange, int placement) {
            this.matchId = matchId;
            this.matchType = matchType;
            this.mapName = mapName;
            this.startedAt = startedAt;
            this.durationSec = durationSec;
            this.weaponType = weaponType;
            this.kills = kills;
            this.deaths = deaths;
            this.survived = survived;
            this.rpChange = rpChange;
            this.placement = placement;
        }

        public int getMatchId() { return matchId; }
        public String getMatchType() { return matchType; }
        public String getMapName() { return mapName; }
        public Timestamp getStartedAt() { return startedAt; }
        public int getDurationSec() { return durationSec; }
        public String getWeaponType() { return weaponType; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }
        public boolean isSurvived() { return survived; }
        public int getRpChange() { return rpChange; }
        public int getPlacement() { return placement; }
    }
}
