package com.butai.rankbattle.database;

import java.sql.*;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * DAO for ether growth system.
 * Tracks EP (ether points), growth level, ore mined, and mob killed per player per season.
 */
public class EtherGrowthDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public EtherGrowthDAO(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Ensure the ether_growth and player_upgrades tables exist.
     */
    public void createTableIfNotExists() {
        String growthSql = """
                CREATE TABLE IF NOT EXISTS ether_growth (
                    uuid CHAR(36) NOT NULL,
                    season_id INT NOT NULL,
                    ep_total INT NOT NULL DEFAULT 0,
                    growth_level INT NOT NULL DEFAULT 0,
                    ore_mined INT NOT NULL DEFAULT 0,
                    mob_killed INT NOT NULL DEFAULT 0,
                    shards INT NOT NULL DEFAULT 0,
                    shards_total INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, season_id),
                    INDEX idx_growth_level (growth_level),
                    INDEX idx_ep_total (ep_total)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
        String upgradesSql = """
                CREATE TABLE IF NOT EXISTS player_upgrades (
                    uuid CHAR(36) NOT NULL,
                    upgrade_type VARCHAR(32) NOT NULL,
                    upgrade_level INT NOT NULL DEFAULT 0,
                    total_spent INT NOT NULL DEFAULT 0,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (uuid, upgrade_type)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;
        // Add shards columns to existing tables if missing
        String alterSql1 = "ALTER TABLE ether_growth ADD COLUMN IF NOT EXISTS shards INT NOT NULL DEFAULT 0";
        String alterSql2 = "ALTER TABLE ether_growth ADD COLUMN IF NOT EXISTS shards_total INT NOT NULL DEFAULT 0";
        try (Connection conn = db.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(growthSql);
            stmt.execute(upgradesSql);
            // Safe migration for existing installs
            try { stmt.execute(alterSql1); } catch (SQLException ignored) {}
            try { stmt.execute(alterSql2); } catch (SQLException ignored) {}
        } catch (SQLException e) {
            logger.severe("Failed to create ether_growth/player_upgrades tables: " + e.getMessage());
        }
    }

    /**
     * Get or create a growth record for the player in the given season.
     * Returns an array: [ep_total, growth_level, ore_mined, mob_killed, shards, shards_total]
     */
    public int[] getOrCreateGrowth(UUID uuid, int seasonId) {
        String selectSql = "SELECT ep_total, growth_level, ore_mined, mob_killed, shards, shards_total FROM ether_growth WHERE uuid = ? AND season_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, seasonId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new int[]{
                            rs.getInt("ep_total"),
                            rs.getInt("growth_level"),
                            rs.getInt("ore_mined"),
                            rs.getInt("mob_killed"),
                            rs.getInt("shards"),
                            rs.getInt("shards_total")
                    };
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get growth data: " + e.getMessage());
            return new int[]{0, 0, 0, 0, 0, 0};
        }

        // Create new record
        String insertSql = "INSERT INTO ether_growth (uuid, season_id) VALUES (?, ?)";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, seasonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to create growth record: " + e.getMessage());
        }
        return new int[]{0, 0, 0, 0, 0, 0};
    }

    /**
     * Update growth data for a player (including shards).
     */
    public void updateGrowth(UUID uuid, int seasonId, int epTotal, int growthLevel, int oreMined, int mobKilled, int shards, int shardsTotal) {
        String sql = "UPDATE ether_growth SET ep_total = ?, growth_level = ?, ore_mined = ?, mob_killed = ?, shards = ?, shards_total = ? WHERE uuid = ? AND season_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, epTotal);
            ps.setInt(2, growthLevel);
            ps.setInt(3, oreMined);
            ps.setInt(4, mobKilled);
            ps.setInt(5, shards);
            ps.setInt(6, shardsTotal);
            ps.setString(7, uuid.toString());
            ps.setInt(8, seasonId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to update growth data: " + e.getMessage());
        }
    }

    // ========== Player Upgrades ==========

    /**
     * Get the upgrade level for a specific upgrade type.
     */
    public int getUpgradeLevel(UUID uuid, String upgradeType) {
        String sql = "SELECT upgrade_level FROM player_upgrades WHERE uuid = ? AND upgrade_type = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, upgradeType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("upgrade_level");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get upgrade level: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Get all upgrade levels for a player.
     * Returns a map of upgrade_type -> upgrade_level.
     */
    public java.util.Map<String, Integer> getAllUpgrades(UUID uuid) {
        java.util.Map<String, Integer> upgrades = new java.util.HashMap<>();
        String sql = "SELECT upgrade_type, upgrade_level FROM player_upgrades WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    upgrades.put(rs.getString("upgrade_type"), rs.getInt("upgrade_level"));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get all upgrades: " + e.getMessage());
        }
        return upgrades;
    }

    /**
     * Set or update an upgrade level for a player.
     */
    public void setUpgradeLevel(UUID uuid, String upgradeType, int level, int totalSpent) {
        String sql = """
                INSERT INTO player_upgrades (uuid, upgrade_type, upgrade_level, total_spent)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE upgrade_level = ?, total_spent = ?
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, upgradeType);
            ps.setInt(3, level);
            ps.setInt(4, totalSpent);
            ps.setInt(5, level);
            ps.setInt(6, totalSpent);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to set upgrade level: " + e.getMessage());
        }
    }

    /**
     * Get ether_cap from players table.
     */
    public int getEtherCap(UUID uuid) {
        String sql = "SELECT ether_cap FROM players WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("ether_cap");
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to get ether cap: " + e.getMessage());
        }
        return 1000;
    }

    /**
     * Update ether_cap in players table.
     */
    public void updateEtherCap(UUID uuid, int etherCap) {
        String sql = "UPDATE players SET ether_cap = ? WHERE uuid = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, etherCap);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to update ether cap: " + e.getMessage());
        }
    }

    /**
     * Reset growth progress for all players when season ends.
     * Does NOT reset ether_cap (carried over).
     */
    public void resetSeasonGrowth(int seasonId) {
        // Growth data remains as historical record - new season creates new rows
        logger.info("Season " + seasonId + " growth data preserved as history.");
    }
}
