package com.borderrank.battle.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Manages MySQL database connections using HikariCP for the Border Rank Battle plugin.
 */
public class DatabaseManager {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private HikariDataSource dataSource;

    /**
     * Constructs a DatabaseManager instance.
     *
     * @param host the database host
     * @param port the database port
     * @param database the database name
     * @param username the database username
     * @param password the database password
     */
    public DatabaseManager(String host, int port, String database, String username, String password) {
        this.host = Objects.requireNonNull(host, "Host cannot be null");
        this.port = port;
        this.database = Objects.requireNonNull(database, "Database cannot be null");
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.password = Objects.requireNonNull(password, "Password cannot be null");
    }

    /**
     * Initializes the database connection pool and creates necessary tables.
     *
     * @throws SQLException if a database error occurs
     */
    public void init() throws SQLException {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?allowPublicKeyRetrieval=true&useSSL=false&useUnicode=true&characterEncoding=UTF-8");
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        this.dataSource = new HikariDataSource(config);

        createTables();
    }

    /**
     * Creates the necessary database tables.
     *
     * @throws SQLException if a database error occurs
     */
    private void createTables() throws SQLException {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Players table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS players (" +
                    "  uuid VARCHAR(36) PRIMARY KEY," +
                    "  name VARCHAR(16) NOT NULL," +
                    "  rank_class VARCHAR(10) NOT NULL DEFAULT 'C'," +
                    "  trion_cap INT NOT NULL DEFAULT 15," +
                    "  trion_max INT NOT NULL DEFAULT 1000," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ")"
            );

            // Weapon RP table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS weapon_rp (" +
                    "  id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  uuid VARCHAR(36) NOT NULL," +
                    "  weapon_type VARCHAR(20) NOT NULL," +
                    "  rp INT NOT NULL DEFAULT 1000," +
                    "  wins INT NOT NULL DEFAULT 0," +
                    "  losses INT NOT NULL DEFAULT 0," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY unique_player_weapon (uuid, weapon_type)," +
                    "  FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
                    ")"
            );

            // Loadouts table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS loadouts (" +
                    "  id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  uuid VARCHAR(36) NOT NULL," +
                    "  name VARCHAR(50) NOT NULL," +
                    "  total_cost INT NOT NULL DEFAULT 0," +
                    "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                    "  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "  UNIQUE KEY unique_player_loadout (uuid, name)," +
                    "  FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE" +
                    ")"
            );

            // Loadout slots table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS loadout_slots (" +
                    "  id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  loadout_id INT NOT NULL," +
                    "  slot_index INT NOT NULL," +
                    "  trigger_id VARCHAR(100)," +
                    "  UNIQUE KEY unique_loadout_slot (loadout_id, slot_index)," +
                    "  FOREIGN KEY (loadout_id) REFERENCES loadouts(id) ON DELETE CASCADE" +
                    ")"
            );

            // Seasons table (needed before match_history FK)
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS seasons (" +
                    "  season_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  season_name VARCHAR(64) NOT NULL UNIQUE," +
                    "  start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  end_date TIMESTAMP NULL," +
                    "  is_active BOOLEAN NOT NULL DEFAULT TRUE" +
                    ")"
            );

            // Match history table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS match_history (" +
                    "  match_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  match_type VARCHAR(10) NOT NULL," +
                    "  map_name VARCHAR(64) NOT NULL," +
                    "  season_id INT NOT NULL," +
                    "  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "  ended_at TIMESTAMP NULL," +
                    "  duration_sec INT," +
                    "  INDEX idx_season_id (season_id)," +
                    "  INDEX idx_started_at (started_at)" +
                    ")"
            );

            // Match results table
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS match_results (" +
                    "  result_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "  match_id INT NOT NULL," +
                    "  uuid VARCHAR(36) NOT NULL," +
                    "  team_id INT," +
                    "  weapon_type VARCHAR(20) NOT NULL," +
                    "  loadout_hash VARCHAR(64)," +
                    "  kills INT NOT NULL DEFAULT 0," +
                    "  deaths INT NOT NULL DEFAULT 0," +
                    "  survived BOOLEAN NOT NULL DEFAULT FALSE," +
                    "  rp_change INT NOT NULL DEFAULT 0," +
                    "  placement INT," +
                    "  INDEX idx_match_id (match_id)," +
                    "  INDEX idx_uuid (uuid)" +
                    ")"
            );
        }
    }

    /**
     * Gets a connection from the connection pool.
     * This method is async-safe and can be used in asynchronous contexts.
     *
     * @return a connection from the pool
     * @throws SQLException if unable to obtain a connection
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or is closed");
        }
        return dataSource.getConnection();
    }

    /**
     * Closes the database connection pool and releases all resources.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Checks if the database connection pool is initialized and active.
     *
     * @return true if the pool is active, false otherwise
     */
    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }

    /**
     * Tests the database connection.
     *
     * @return true if the connection is successful, false otherwise
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "DatabaseManager{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", database='" + database + '\'' +
                ", connected=" + isConnected() +
                '}';
    }
}
