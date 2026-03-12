package com.butai.rankbattle.database;

import com.butai.rankbattle.model.Team;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class TeamDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public TeamDAO(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Create a new team. Returns the generated team_id, or -1 on failure.
     */
    public int createTeam(String name, UUID leaderUuid) {
        String sqlTeam = "INSERT INTO teams (team_name, leader_uuid) VALUES (?, ?)";
        String sqlMember = "INSERT INTO team_members (team_id, uuid, role) VALUES (?, ?, 'leader')";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int teamId;
                try (PreparedStatement ps = conn.prepareStatement(sqlTeam, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setString(2, leaderUuid.toString());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            teamId = rs.getInt(1);
                        } else {
                            conn.rollback();
                            return -1;
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(sqlMember)) {
                    ps.setInt(1, teamId);
                    ps.setString(2, leaderUuid.toString());
                    ps.executeUpdate();
                }

                conn.commit();
                return teamId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.severe("Failed to create team: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Add a member to a team.
     */
    public boolean addMember(int teamId, UUID uuid) {
        String sql = "INSERT INTO team_members (team_id, uuid, role) VALUES (?, ?, 'member')";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.severe("Failed to add team member: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove a member from a team.
     */
    public boolean removeMember(int teamId, UUID uuid) {
        String sql = "DELETE FROM team_members WHERE team_id = ? AND uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.severe("Failed to remove team member: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a team (cascade deletes members).
     */
    public boolean deleteTeam(int teamId) {
        String sql = "DELETE FROM teams WHERE team_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, teamId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.severe("Failed to delete team: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load a team by name. Returns null if not found.
     */
    public Team loadTeamByName(String name) {
        String sqlTeam = "SELECT team_id, team_name, leader_uuid FROM teams WHERE team_name = ? AND is_active = TRUE";
        String sqlMembers = "SELECT uuid FROM team_members WHERE team_id = ?";

        try (Connection conn = db.getConnection()) {
            int teamId;
            UUID leaderUuid;
            String teamName;

            try (PreparedStatement ps = conn.prepareStatement(sqlTeam)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    teamId = rs.getInt("team_id");
                    teamName = rs.getString("team_name");
                    leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
                }
            }

            Team team = new Team(teamId, teamName, leaderUuid);

            try (PreparedStatement ps = conn.prepareStatement(sqlMembers)) {
                ps.setInt(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        team.addMember(UUID.fromString(rs.getString("uuid")));
                    }
                }
            }

            return team;
        } catch (SQLException e) {
            logger.severe("Failed to load team: " + e.getMessage());
            return null;
        }
    }

    /**
     * Load the team that a player belongs to. Returns null if not in a team.
     */
    public Team loadTeamByPlayer(UUID uuid) {
        String sql = "SELECT t.team_id, t.team_name, t.leader_uuid "
                + "FROM teams t JOIN team_members tm ON t.team_id = tm.team_id "
                + "WHERE tm.uuid = ? AND t.is_active = TRUE";
        String sqlMembers = "SELECT uuid FROM team_members WHERE team_id = ?";

        try (Connection conn = db.getConnection()) {
            int teamId;
            UUID leaderUuid;
            String teamName;

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    teamId = rs.getInt("team_id");
                    teamName = rs.getString("team_name");
                    leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
                }
            }

            Team team = new Team(teamId, teamName, leaderUuid);

            try (PreparedStatement ps = conn.prepareStatement(sqlMembers)) {
                ps.setInt(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        team.addMember(UUID.fromString(rs.getString("uuid")));
                    }
                }
            }

            return team;
        } catch (SQLException e) {
            logger.severe("Failed to load team by player: " + e.getMessage());
            return null;
        }
    }

    /**
     * Update the team leader.
     */
    public boolean updateLeader(int teamId, UUID newLeader) {
        String sqlTeam = "UPDATE teams SET leader_uuid = ? WHERE team_id = ?";
        String sqlMember = "UPDATE team_members SET role = CASE "
                + "WHEN uuid = ? THEN 'leader' ELSE 'member' END "
                + "WHERE team_id = ?";

        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sqlTeam)) {
                    ps.setString(1, newLeader.toString());
                    ps.setInt(2, teamId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(sqlMember)) {
                    ps.setString(1, newLeader.toString());
                    ps.setInt(2, teamId);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            logger.severe("Failed to update team leader: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a team name is already taken.
     */
    public boolean teamNameExists(String name) {
        String sql = "SELECT 1 FROM teams WHERE team_name = ? AND is_active = TRUE";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.severe("Failed to check team name: " + e.getMessage());
            return false;
        }
    }
}
