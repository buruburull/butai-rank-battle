package com.butai.rankbattle.database;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class FrameSetDAO {

    private final DatabaseManager db;
    private final Logger logger;

    public FrameSetDAO(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Save or update a frameset preset.
     * slots is a String[8] array (index 0 = slot_1, etc.), null for empty slots.
     */
    public void saveFrameSet(UUID uuid, String name, String[] slots) {
        String sql = "INSERT INTO player_framesets (uuid, frameset_name, slot_1, slot_2, slot_3, slot_4, slot_5, slot_6, slot_7, slot_8) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE slot_1=?, slot_2=?, slot_3=?, slot_4=?, slot_5=?, slot_6=?, slot_7=?, slot_8=?, last_modified=CURRENT_TIMESTAMP";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            // INSERT values (slots 1-8)
            for (int i = 0; i < 8; i++) {
                ps.setString(3 + i, slots[i]);
            }
            // UPDATE values (slots 1-8)
            for (int i = 0; i < 8; i++) {
                ps.setString(11 + i, slots[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("Failed to save frameset: " + e.getMessage());
        }
    }

    /**
     * Load a frameset preset by name.
     * Returns String[8] or null if not found.
     */
    public String[] loadFrameSet(UUID uuid, String name) {
        String sql = "SELECT slot_1, slot_2, slot_3, slot_4, slot_5, slot_6, slot_7, slot_8 "
                + "FROM player_framesets WHERE uuid = ? AND frameset_name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String[] slots = new String[8];
                    for (int i = 0; i < 8; i++) {
                        slots[i] = rs.getString("slot_" + (i + 1));
                    }
                    return slots;
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to load frameset: " + e.getMessage());
        }
        return null;
    }

    /**
     * List all frameset preset names for a player.
     */
    public List<String> listFrameSets(UUID uuid) {
        List<String> names = new ArrayList<>();
        String sql = "SELECT frameset_name FROM player_framesets WHERE uuid = ? ORDER BY last_modified DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    names.add(rs.getString("frameset_name"));
                }
            }
        } catch (SQLException e) {
            logger.severe("Failed to list framesets: " + e.getMessage());
        }
        return names;
    }

    /**
     * Delete a frameset preset.
     */
    public boolean deleteFrameSet(UUID uuid, String name) {
        String sql = "DELETE FROM player_framesets WHERE uuid = ? AND frameset_name = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.severe("Failed to delete frameset: " + e.getMessage());
        }
        return false;
    }
}
