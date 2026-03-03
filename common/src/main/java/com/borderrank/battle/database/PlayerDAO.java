package com.borderrank.battle.database;

import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.model.RankClass;
import com.borderrank.battle.model.WeaponRP;
import com.borderrank.battle.model.WeaponType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Data access object for player and weapon RP database operations.
 */
public class PlayerDAO {
    private final DatabaseManager databaseManager;

    /**
     * Constructs a PlayerDAO instance.
     *
     * @param databaseManager the database manager
     */
    public PlayerDAO(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Loads a player from the database.
     *
     * @param uuid the player's UUID
     * @return the BRBPlayer object, or null if not found
     * @throws SQLException if a database error occurs
     */
    public BRBPlayer loadPlayer(UUID uuid) throws SQLException {
        String query = "SELECT uuid, name, rank_class, trion_cap, trion_max FROM players WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("name");
                    RankClass rankClass = RankClass.valueOf(rs.getString("rank_class"));
                    int trionCap = rs.getInt("trion_cap");
                    int trionMax = rs.getInt("trion_max");

                    BRBPlayer player = new BRBPlayer(playerUuid, name, rankClass, trionCap, trionMax);

                    // Load weapon RP data
                    Map<WeaponType, WeaponRP> weaponRPMap = loadWeaponRP(uuid);
                    weaponRPMap.forEach(player::setWeaponRP);

                    return player;
                }
            }
        }

        return null;
    }

    /**
     * Saves a player to the database (creates or updates).
     *
     * @param player the player to save
     * @throws SQLException if a database error occurs
     */
    public void savePlayer(BRBPlayer player) throws SQLException {
        String query = "INSERT INTO players (uuid, name, rank_class, trion_cap, trion_max) " +
                       "VALUES (?, ?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE name = ?, rank_class = ?, trion_cap = ?, trion_max = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, player.getUuid().toString());
            stmt.setString(2, player.getName());
            stmt.setString(3, player.getRankClass().name());
            stmt.setInt(4, player.getTrionCap());
            stmt.setInt(5, player.getTrionMax());
            stmt.setString(6, player.getName());
            stmt.setString(7, player.getRankClass().name());
            stmt.setInt(8, player.getTrionCap());
            stmt.setInt(9, player.getTrionMax());

            stmt.executeUpdate();
        }

        // Save weapon RP data
        for (WeaponRP weaponRP : player.getWeaponRPMap().values()) {
            saveWeaponRP(player.getUuid(), weaponRP.getType(), weaponRP);
        }
    }

    /**
     * Loads all weapon RP data for a player.
     *
     * @param uuid the player's UUID
     * @return a map of weapon type to weapon RP
     * @throws SQLException if a database error occurs
     */
    public Map<WeaponType, WeaponRP> loadWeaponRP(UUID uuid) throws SQLException {
        Map<WeaponType, WeaponRP> weaponRPMap = new EnumMap<>(WeaponType.class);
        String query = "SELECT weapon_type, rp, wins, losses FROM weapon_rp WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    WeaponType type = WeaponType.valueOf(rs.getString("weapon_type"));
                    int rp = rs.getInt("rp");
                    int wins = rs.getInt("wins");
                    int losses = rs.getInt("losses");

                    weaponRPMap.put(type, new WeaponRP(type, rp, wins, losses));
                }
            }
        }

        // Ensure all weapon types are present
        for (WeaponType type : WeaponType.values()) {
            weaponRPMap.putIfAbsent(type, new WeaponRP(type));
        }

        return weaponRPMap;
    }

    /**
     * Saves or updates weapon RP data for a player.
     *
     * @param uuid the player's UUID
     * @param type the weapon type
     * @param weaponRP the weapon RP data
     * @throws SQLException if a database error occurs
     */
    public void saveWeaponRP(UUID uuid, WeaponType type, WeaponRP weaponRP) throws SQLException {
        String query = "INSERT INTO weapon_rp (uuid, weapon_type, rp, wins, losses) " +
                       "VALUES (?, ?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE rp = ?, wins = ?, losses = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, type.name());
            stmt.setInt(3, weaponRP.getRp());
            stmt.setInt(4, weaponRP.getWins());
            stmt.setInt(5, weaponRP.getLosses());
            stmt.setInt(6, weaponRP.getRp());
            stmt.setInt(7, weaponRP.getWins());
            stmt.setInt(8, weaponRP.getLosses());

            stmt.executeUpdate();
        }
    }

    /**
     * Gets the top players for a specific weapon type.
     *
     * @param type the weapon type
     * @param limit the maximum number of players to return
     * @return a list of BRBPlayer objects, ordered by RP descending
     * @throws SQLException if a database error occurs
     */
    public List<BRBPlayer> getTopPlayers(WeaponType type, int limit) throws SQLException {
        List<BRBPlayer> topPlayers = new ArrayList<>();
        String query = "SELECT p.uuid, p.name, p.rank_class, p.trion_cap, p.trion_max " +
                       "FROM players p " +
                       "JOIN weapon_rp w ON p.uuid = w.uuid " +
                       "WHERE w.weapon_type = ? " +
                       "ORDER BY w.rp DESC " +
                       "LIMIT ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, type.name());
            stmt.setInt(2, Math.max(1, limit));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("name");
                    RankClass rankClass = RankClass.valueOf(rs.getString("rank_class"));
                    int trionCap = rs.getInt("trion_cap");
                    int trionMax = rs.getInt("trion_max");

                    BRBPlayer player = new BRBPlayer(uuid, name, rankClass, trionCap, trionMax);

                    // Load weapon RP data
                    Map<WeaponType, WeaponRP> weaponRPMap = loadWeaponRP(uuid);
                    weaponRPMap.forEach(player::setWeaponRP);

                    topPlayers.add(player);
                }
            }
        }

        return topPlayers;
    }

    /**
     * Gets the top players across all weapon types by total wins.
     *
     * @param limit the maximum number of players to return
     * @return a list of BRBPlayer objects, ordered by total wins descending
     * @throws SQLException if a database error occurs
     */
    public List<BRBPlayer> getTopPlayersByWins(int limit) throws SQLException {
        List<BRBPlayer> topPlayers = new ArrayList<>();
        String query = "SELECT p.uuid, p.name, p.rank_class, p.trion_cap, p.trion_max, " +
                       "SUM(w.wins) as total_wins " +
                       "FROM players p " +
                       "JOIN weapon_rp w ON p.uuid = w.uuid " +
                       "GROUP BY p.uuid " +
                       "ORDER BY total_wins DESC " +
                       "LIMIT ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, Math.max(1, limit));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String name = rs.getString("name");
                    RankClass rankClass = RankClass.valueOf(rs.getString("rank_class"));
                    int trionCap = rs.getInt("trion_cap");
                    int trionMax = rs.getInt("trion_max");

                    BRBPlayer player = new BRBPlayer(uuid, name, rankClass, trionCap, trionMax);

                    // Load weapon RP data
                    Map<WeaponType, WeaponRP> weaponRPMap = loadWeaponRP(uuid);
                    weaponRPMap.forEach(player::setWeaponRP);

                    topPlayers.add(player);
                }
            }
        }

        return topPlayers;
    }

    /**
     * Deletes a player from the database.
     *
     * @param uuid the player's UUID
     * @throws SQLException if a database error occurs
     */
    public void deletePlayer(UUID uuid) throws SQLException {
        String query = "DELETE FROM players WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        }
    }
}
