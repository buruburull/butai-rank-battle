package com.butai.rankbattle.database;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class PlayerDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public PlayerDAO(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Register a new player or update name on login.
     */
    public void registerOrUpdate(UUID uuid, String name) {
        String sql = "INSERT INTO players (uuid, name) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE name = ?, last_login = CURRENT_TIMESTAMP";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to register/update player: " + e.getMessage());
        }
    }

    /**
     * Initialize weapon RP rows for a new player (STRIKER, GUNNER, MARKSMAN = 1000 each).
     */
    public void initializeWeaponRP(UUID uuid) {
        String sql = "INSERT IGNORE INTO weapon_rp (uuid, weapon_type, rp, wins, losses) VALUES (?, ?, 1000, 0, 0)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (String type : new String[]{"STRIKER", "GUNNER", "MARKSMAN"}) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            logger.severe("Failed to initialize weapon RP: " + e.getMessage());
        }
    }

    /**
     * Get rank_class for a player.
     */
    public String getRankClass(UUID uuid) {
        String sql = "SELECT rank_class FROM players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("rank_class");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get rank class: " + e.getMessage());
        }
        return "UNRANKED";
    }

    /**
     * Update rank_class for a player.
     */
    public void updateRankClass(UUID uuid, String rankClass) {
        String sql = "UPDATE players SET rank_class = ? WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rankClass);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to update rank class: " + e.getMessage());
        }
    }

    /**
     * Get RP for a specific weapon type.
     */
    public int getWeaponRP(UUID uuid, String weaponType) {
        String sql = "SELECT rp FROM weapon_rp WHERE uuid = ? AND weapon_type = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, weaponType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("rp");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get weapon RP: " + e.getMessage());
        }
        return 1000;
    }

    /**
     * Get all weapon RPs for a player. Returns map of weaponType -> rp.
     */
    public Map<String, Integer> getAllWeaponRP(UUID uuid) {
        Map<String, Integer> rpMap = new LinkedHashMap<>();
        String sql = "SELECT weapon_type, rp FROM weapon_rp WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rpMap.put(rs.getString("weapon_type"), rs.getInt("rp"));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get all weapon RP: " + e.getMessage());
        }
        return rpMap;
    }

    /**
     * Get wins and losses for a specific weapon type.
     * Returns int[] { wins, losses }.
     */
    public int[] getWinLoss(UUID uuid, String weaponType) {
        String sql = "SELECT wins, losses FROM weapon_rp WHERE uuid = ? AND weapon_type = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, weaponType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[]{rs.getInt("wins"), rs.getInt("losses")};
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get win/loss: " + e.getMessage());
        }
        return new int[]{0, 0};
    }

    /**
     * Update RP and win/loss after a match.
     */
    public void updateMatchResult(UUID uuid, String weaponType, int rpChange, boolean isWin) {
        String sql = "UPDATE weapon_rp SET rp = rp + ?, "
                + (isWin ? "wins = wins + 1" : "losses = losses + 1")
                + " WHERE uuid = ? AND weapon_type = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rpChange);
            ps.setString(2, uuid.toString());
            ps.setString(3, weaponType);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to update match result: " + e.getMessage());
        }
    }

    /**
     * Set RP to a specific value (admin command).
     */
    public void setWeaponRP(UUID uuid, String weaponType, int rp) {
        String sql = "UPDATE weapon_rp SET rp = ? WHERE uuid = ? AND weapon_type = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, rp);
            ps.setString(2, uuid.toString());
            ps.setString(3, weaponType);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to set weapon RP: " + e.getMessage());
        }
    }

    /**
     * Get total RP (sum of all weapon RPs) for a player.
     */
    public int getTotalRP(UUID uuid) {
        String sql = "SELECT COALESCE(SUM(rp), 3000) AS total_rp FROM weapon_rp WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total_rp");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get total RP: " + e.getMessage());
        }
        return 3000;
    }

    /**
     * Get TOP N players by total RP.
     * Returns list of maps with keys: uuid, name, rank_class, total_rp
     */
    public List<Map<String, Object>> getTopPlayers(int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT p.uuid, p.name, p.rank_class, COALESCE(SUM(wr.rp), 3000) AS total_rp "
                + "FROM players p LEFT JOIN weapon_rp wr ON p.uuid = wr.uuid "
                + "GROUP BY p.uuid, p.name, p.rank_class "
                + "ORDER BY total_rp DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("rank_class", rs.getString("rank_class"));
                    row.put("total_rp", rs.getInt("total_rp"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get top players: " + e.getMessage());
        }
        return list;
    }

    /**
     * Get TOP N players by a specific weapon type.
     */
    public List<Map<String, Object>> getTopByWeapon(String weaponType, int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT wr.uuid, p.name, p.rank_class, wr.rp, wr.wins, wr.losses "
                + "FROM weapon_rp wr JOIN players p ON wr.uuid = p.uuid "
                + "WHERE wr.weapon_type = ? ORDER BY wr.rp DESC LIMIT ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, weaponType);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("uuid", rs.getString("uuid"));
                    row.put("name", rs.getString("name"));
                    row.put("rank_class", rs.getString("rank_class"));
                    row.put("rp", rs.getInt("rp"));
                    row.put("wins", rs.getInt("wins"));
                    row.put("losses", rs.getInt("losses"));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get top by weapon: " + e.getMessage());
        }
        return list;
    }

    /**
     * Check if player exists in DB.
     */
    public boolean playerExists(UUID uuid) {
        String sql = "SELECT 1 FROM players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.severe("Failed to check player exists: " + e.getMessage());
        }
        return false;
    }

    /**
     * Reset all weapon RP to 1000 (season end).
     */
    public void resetAllRP() {
        String sql = "UPDATE weapon_rp SET rp = 1000";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            logger.info("All weapon RP reset to 1000.");
        } catch (SQLException e) {
            logger.severe("Failed to reset all RP: " + e.getMessage());
        }
    }
}
