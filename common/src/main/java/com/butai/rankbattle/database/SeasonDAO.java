package com.butai.rankbattle.database;

import java.sql.*;
import java.util.logging.Logger;

/**
 * DAO for season management (seasons and season_snapshots tables).
 */
public class SeasonDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public SeasonDAO(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Create a new season. Deactivates any currently active season first.
     * Returns the new season_id, or -1 on failure.
     */
    public int createSeason(String name) {
        try (Connection conn = db.getConnection()) {
            // Deactivate current active season
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE seasons SET is_active = FALSE WHERE is_active = TRUE")) {
                ps.executeUpdate();
            }

            // Insert new season
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO seasons (season_name, start_date, is_active) VALUES (?, NOW(), TRUE)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        logger.info("Season created: " + name + " (id=" + id + ")");
                        return id;
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to create season: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Get the currently active season ID, or -1 if none.
     */
    public int getActiveSeasonId() {
        String sql = "SELECT season_id FROM seasons WHERE is_active = TRUE LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("season_id");
            }
        } catch (SQLException e) {
            logger.severe("Failed to get active season: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Get the active season name, or null if none.
     */
    public String getActiveSeasonName() {
        String sql = "SELECT season_name FROM seasons WHERE is_active = TRUE LIMIT 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getString("season_name");
            }
        } catch (SQLException e) {
            logger.severe("Failed to get active season name: " + e.getMessage());
        }
        return null;
    }

    /**
     * End the active season: set end_date and is_active=false.
     */
    public boolean endSeason(int seasonId) {
        String sql = "UPDATE seasons SET end_date = NOW(), is_active = FALSE WHERE season_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, seasonId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                logger.info("Season " + seasonId + " ended.");
                return true;
            }
        } catch (SQLException e) {
            logger.severe("Failed to end season: " + e.getMessage());
        }
        return false;
    }

    /**
     * Save RP snapshots for all players at season end.
     * Reads all weapon_rp rows and inserts into season_snapshots.
     */
    public int saveAllSnapshots(int seasonId) {
        String selectSql = "SELECT uuid, weapon_type, rp FROM weapon_rp";
        String insertSql = "INSERT INTO season_snapshots (season_id, uuid, weapon_type, final_rp) VALUES (?, ?, ?, ?)";
        int count = 0;

        try (Connection conn = db.getConnection();
             PreparedStatement selectPs = conn.prepareStatement(selectSql);
             ResultSet rs = selectPs.executeQuery();
             PreparedStatement insertPs = conn.prepareStatement(insertSql)) {

            while (rs.next()) {
                insertPs.setInt(1, seasonId);
                insertPs.setString(2, rs.getString("uuid"));
                insertPs.setString(3, rs.getString("weapon_type"));
                insertPs.setInt(4, rs.getInt("rp"));
                insertPs.addBatch();
                count++;
            }

            if (count > 0) {
                insertPs.executeBatch();
            }

            logger.info("Saved " + count + " season snapshots for season " + seasonId);
        } catch (SQLException e) {
            logger.severe("Failed to save season snapshots: " + e.getMessage());
        }
        return count;
    }

    /**
     * Reset ether growth for a new season.
     * EP/level progress resets, but ether_cap in players table is preserved (carried over).
     * Growth data from the ended season stays as historical record.
     */
    public void resetGrowthForNewSeason() {
        // No deletion needed - new season automatically creates new ether_growth rows.
        // The old season's data remains as history (keyed by season_id).
        logger.info("Growth data will reset for new season (new rows will be created on player login).");
    }

    /**
     * Check if a season name already exists.
     */
    public boolean seasonNameExists(String name) {
        String sql = "SELECT 1 FROM seasons WHERE season_name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.severe("Failed to check season name: " + e.getMessage());
        }
        return false;
    }
}
