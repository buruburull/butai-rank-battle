-- Border Rank Battle Database Schema
-- MySQL 8.0 compatible schema for BRB plugin
-- This schema manages player data, triggers, matches, rankings, and seasons

-- ============================================
-- PLAYERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS players (
    uuid CHAR(36) PRIMARY KEY COMMENT 'Player UUID (unique identifier)',
    name VARCHAR(16) NOT NULL COMMENT 'Player display name',
    rank_class ENUM('S', 'A', 'B', 'C', 'D', 'UNRANKED') NOT NULL DEFAULT 'UNRANKED' COMMENT 'Overall rank tier',
    trion_cap INT NOT NULL DEFAULT 15 COMMENT 'Maximum number of triggers player can equip',
    trion_max INT NOT NULL DEFAULT 1000 COMMENT 'Maximum trion capacity',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Account creation timestamp',
    last_login TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last login timestamp',
    
    INDEX idx_name (name),
    INDEX idx_rank_class (rank_class),
    INDEX idx_created_at (created_at),
    INDEX idx_last_login (last_login)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Core player account and profile data';

-- ============================================
-- WEAPON RATING POINTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS weapon_rp (
    uuid CHAR(36) NOT NULL,
    weapon_type ENUM(
        'ATTACKER', 'SHOOTER', 'SNIPER'
    ) NOT NULL COMMENT 'Type of weapon/trigger',
    rp INT NOT NULL DEFAULT 1000 COMMENT 'Rating points for this weapon (1000 = middle rank)',
    wins INT NOT NULL DEFAULT 0 COMMENT 'Total wins with this weapon',
    losses INT NOT NULL DEFAULT 0 COMMENT 'Total losses with this weapon',
    
    PRIMARY KEY (uuid, weapon_type),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_rp (rp),
    INDEX idx_weapon_type (weapon_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Per-weapon rating points and statistics';

-- ============================================
-- TRIGGER MASTER TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS trigger_master (
    trigger_id VARCHAR(64) PRIMARY KEY COMMENT 'Unique trigger identifier (e.g., kogetsu, scorpion)',
    trigger_name VARCHAR(64) NOT NULL UNIQUE COMMENT 'Display name of the trigger',
    category ENUM('attacker', 'shooter', 'sniper', 'support') NOT NULL COMMENT 'Trigger category/type',
    cost INT NOT NULL COMMENT 'Slot cost to equip this trigger (affects total loadout cost)',
    trion_use INT NOT NULL DEFAULT 0 COMMENT 'Trion consumed per activation',
    trion_sustain INT NOT NULL DEFAULT 0 COMMENT 'Trion consumed per second while active',
    slot_type VARCHAR(64) COMMENT 'Slot category if applicable',
    mc_item VARCHAR(64) NOT NULL COMMENT 'Minecraft item representing this trigger',
    description TEXT COMMENT 'Trigger description and mechanics',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether trigger is available for use',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When trigger was added to system',
    
    INDEX idx_category (category),
    INDEX idx_cost (cost),
    INDEX idx_is_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Master list of all available triggers and their properties';

-- ============================================
-- PLAYER LOADOUTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS player_loadouts (
    loadout_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique loadout identifier',
    uuid CHAR(36) NOT NULL COMMENT 'Player UUID',
    loadout_name VARCHAR(64) NOT NULL COMMENT 'User-defined loadout name',
    slot_1 VARCHAR(64) COMMENT 'Trigger ID in slot 1',
    slot_2 VARCHAR(64) COMMENT 'Trigger ID in slot 2',
    slot_3 VARCHAR(64) COMMENT 'Trigger ID in slot 3',
    slot_4 VARCHAR(64) COMMENT 'Trigger ID in slot 4',
    slot_5 VARCHAR(64) COMMENT 'Trigger ID in slot 5',
    slot_6 VARCHAR(64) COMMENT 'Trigger ID in slot 6',
    slot_7 VARCHAR(64) COMMENT 'Trigger ID in slot 7',
    slot_8 VARCHAR(64) COMMENT 'Trigger ID in slot 8',
    total_cost INT NOT NULL COMMENT 'Sum of costs of all triggers in loadout',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When loadout was created',
    last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last modification timestamp',
    
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (slot_1) REFERENCES trigger_master(trigger_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_2) REFERENCES trigger_master(trigger_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_3) REFERENCES trigger_master(trigger_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_4) REFERENCES trigger_master(trigger_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_5) REFERENCES trigger_master(trigger_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_6) REFERENCES trigger_master(trigger_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_7) REFERENCES trigger_master(trigger_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_8) REFERENCES trigger_master(trigger_id) ON DELETE SET NULL,
    UNIQUE KEY unique_loadout (uuid, loadout_name),
    INDEX idx_uuid (uuid),
    INDEX idx_total_cost (total_cost)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Saved trigger loadouts (combinations) for players';

-- ============================================
-- TEAMS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS teams (
    team_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique team identifier',
    team_name VARCHAR(64) NOT NULL UNIQUE COMMENT 'Team name',
    leader_uuid CHAR(36) NOT NULL COMMENT 'UUID of team leader',
    team_rp INT NOT NULL DEFAULT 1000 COMMENT 'Overall team rating points',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether team is still active',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Team creation timestamp',
    
    FOREIGN KEY (leader_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_team_name (team_name),
    INDEX idx_leader_uuid (leader_uuid),
    INDEX idx_is_active (is_active),
    INDEX idx_team_rp (team_rp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Team information and leadership';

-- ============================================
-- TEAM MEMBERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS team_members (
    team_id INT NOT NULL COMMENT 'Team identifier',
    uuid CHAR(36) NOT NULL COMMENT 'Player UUID',
    role ENUM('leader', 'member', 'recruit') NOT NULL DEFAULT 'member' COMMENT 'Player role within team',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'When player joined team',
    
    PRIMARY KEY (team_id, uuid),
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_role (role),
    INDEX idx_uuid (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Team membership and roles';

-- ============================================
-- SEASONS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS seasons (
    season_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique season identifier',
    season_name VARCHAR(64) NOT NULL UNIQUE COMMENT 'Season display name (e.g., "Season 1")',
    start_date TIMESTAMP NOT NULL COMMENT 'Season start date',
    end_date TIMESTAMP COMMENT 'Season end date (NULL if ongoing)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether season is currently active',
    
    INDEX idx_season_name (season_name),
    INDEX idx_is_active (is_active),
    INDEX idx_start_date (start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Ranking seasons (time periods for competitive play)';

-- ============================================
-- MATCH HISTORY TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS match_history (
    match_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique match identifier',
    match_type ENUM('solo', 'team') NOT NULL COMMENT 'Whether match was solo or team-based',
    map_name VARCHAR(64) NOT NULL COMMENT 'Name of the map played on',
    season_id INT NOT NULL COMMENT 'Season this match occurred in',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Match start time',
    ended_at TIMESTAMP COMMENT 'Match end time (NULL if still ongoing)',
    duration_sec INT COMMENT 'Match duration in seconds',
    
    FOREIGN KEY (season_id) REFERENCES seasons(season_id) ON DELETE CASCADE,
    INDEX idx_match_type (match_type),
    INDEX idx_map_name (map_name),
    INDEX idx_season_id (season_id),
    INDEX idx_started_at (started_at),
    INDEX idx_ended_at (ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Historical record of all matches played';

-- ============================================
-- MATCH RESULTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS match_results (
    result_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique result record identifier',
    match_id INT NOT NULL COMMENT 'Match this result belongs to',
    uuid CHAR(36) NOT NULL COMMENT 'Player UUID',
    team_id INT COMMENT 'Team ID if in team match (NULL for solo)',
    weapon_type ENUM(
        'ATTACKER', 'SHOOTER', 'SNIPER'
    ) NOT NULL COMMENT 'Primary weapon used in match',
    loadout_hash VARCHAR(64) COMMENT 'Hash of trigger loadout used',
    kills INT NOT NULL DEFAULT 0 COMMENT 'Number of eliminations',
    deaths INT NOT NULL DEFAULT 0 COMMENT 'Number of times eliminated',
    survived BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether player survived to end of match',
    rp_change INT NOT NULL DEFAULT 0 COMMENT 'Rating point change from this match',
    placement INT COMMENT 'Final placement in match (1 = winner)',
    
    FOREIGN KEY (match_id) REFERENCES match_history(match_id) ON DELETE CASCADE,
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE SET NULL,
    INDEX idx_match_id (match_id),
    INDEX idx_uuid (uuid),
    INDEX idx_team_id (team_id),
    INDEX idx_weapon_type (weapon_type),
    INDEX idx_placement (placement)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Per-player results and statistics from each match';

-- ============================================
-- SEASON SNAPSHOTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS season_snapshots (
    snapshot_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique snapshot identifier',
    season_id INT NOT NULL COMMENT 'Season identifier',
    uuid CHAR(36) NOT NULL COMMENT 'Player UUID',
    weapon_type ENUM(
        'ATTACKER', 'SHOOTER', 'SNIPER'
    ) NOT NULL COMMENT 'Weapon type',
    final_rp INT NOT NULL COMMENT 'Final rating points for this weapon at season end',
    placement INT COMMENT 'Final leaderboard placement for this weapon',
    
    FOREIGN KEY (season_id) REFERENCES seasons(season_id) ON DELETE CASCADE,
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    UNIQUE KEY unique_snapshot (season_id, uuid, weapon_type),
    INDEX idx_season_id (season_id),
    INDEX idx_uuid (uuid),
    INDEX idx_final_rp (final_rp),
    INDEX idx_placement (placement)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Seasonal snapshots of player rankings per weapon at season end';

-- ============================================
-- CREATE VIEWS FOR COMMON QUERIES
-- ============================================

-- Overall leaderboard view
CREATE OR REPLACE VIEW player_overall_ranking AS
SELECT 
    p.uuid,
    p.name,
    p.rank_class,
    ROUND(AVG(COALESCE(wr.rp, 1000))) AS avg_rp,
    SUM(COALESCE(wr.wins, 0)) AS total_wins,
    SUM(COALESCE(wr.losses, 0)) AS total_losses,
    ROUND(SUM(COALESCE(wr.wins, 0)) / 
        NULLIF(SUM(COALESCE(wr.wins, 0) + COALESCE(wr.losses, 0)), 0) * 100, 2) AS win_rate,
    p.created_at
FROM players p
LEFT JOIN weapon_rp wr ON p.uuid = wr.uuid
GROUP BY p.uuid, p.name, p.rank_class, p.created_at
ORDER BY avg_rp DESC;

-- Recent match activity view
CREATE OR REPLACE VIEW recent_matches AS
SELECT 
    mh.match_id,
    mh.match_type,
    mh.map_name,
    mh.started_at,
    COUNT(DISTINCT mr.uuid) AS player_count,
    mh.duration_sec
FROM match_history mh
LEFT JOIN match_results mr ON mh.match_id = mr.match_id
WHERE mh.ended_at IS NOT NULL
GROUP BY mh.match_id, mh.match_type, mh.map_name, mh.started_at, mh.duration_sec
ORDER BY mh.started_at DESC
LIMIT 100;

-- Weapon popularity view
CREATE OR REPLACE VIEW weapon_popularity AS
SELECT 
    weapon_type,
    COUNT(*) AS times_used,
    ROUND(AVG(kills), 2) AS avg_kills,
    ROUND(AVG(deaths), 2) AS avg_deaths,
    ROUND(SUM(CASE WHEN placement = 1 THEN 1 ELSE 0 END) / 
        COUNT(*) * 100, 2) AS win_rate
FROM match_results
GROUP BY weapon_type
ORDER BY times_used DESC;

-- ============================================
-- INITIAL DATA POPULATION
-- ============================================

-- Insert trigger master data (from triggers.yml)
INSERT IGNORE INTO trigger_master (trigger_id, trigger_name, category, cost, trion_use, slot_type, mc_item, description) VALUES
-- Attackers
('kogetsu', 'Kogetsu', 'attacker', 4, 0, NULL, 'NETHERITE_SWORD', 'High-damage sword trigger. Deals 8 damage per hit with enhanced reach.'),
('scorpion', 'Scorpion', 'attacker', 2, 0, NULL, 'GOLDEN_SWORD', 'Agile sword trigger. Deals increased damage from behind enemies.'),
('raygust', 'Raygust', 'attacker', 3, 0, NULL, 'IRON_SWORD', 'Versatile sword trigger with defensive shield capabilities.'),
-- Shooters
('asteroid', 'Asteroid', 'shooter', 3, 0, NULL, 'BOW', 'Basic bow trigger. Fires standard arrows with reliable accuracy.'),
('meteora', 'Meteora', 'shooter', 4, 0, NULL, 'CROSSBOW', 'Explosive crossbow trigger. Fires bolts that create explosions on impact.'),
('hound', 'Hound', 'shooter', 4, 0, NULL, 'TRIDENT', 'Magical trident trigger with homing capabilities.'),
('viper', 'Viper', 'shooter', 3, 0, NULL, 'BOW', 'Advanced bow trigger capable of curving shots.'),
-- Snipers
('egret', 'Egret', 'sniper', 4, 0, NULL, 'BOW', 'High-powered bow trigger requiring 2 seconds of charging.'),
('lightning', 'Lightning', 'sniper', 3, 0, NULL, 'BOW', 'Bow trigger with piercing arrows.'),
('ibis', 'Ibis', 'sniper', 5, 0, NULL, 'BOW', 'Ultimate sniper trigger with maximum power output.'),
-- Support
('grasshopper', 'Grasshopper', 'support', 2, 40, NULL, 'FEATHER', 'Jump enhancement trigger. Grants enhanced mobility and high jump.'),
('shield_trigger', 'Shield', 'support', 2, 30, NULL, 'SHIELD', 'Damage absorption trigger. Creates a protective shield.'),
('bagworm', 'Bagworm', 'support', 1, 15, NULL, 'LEATHER_HELMET', 'Sustained defense trigger. Provides continuous damage reduction.'),
('teleporter', 'Teleporter', 'support', 3, 60, NULL, 'ENDER_PEARL', 'Teleportation trigger. Instantly transport to a target location.'),
('escudo', 'Escudo', 'support', 2, 50, NULL, 'GOLD_BLOCK', 'Barrier creation trigger. Creates a temporary protective barrier.'),
('meteora_sub', 'Meteora Sub', 'support', 2, 35, NULL, 'TNT', 'Explosive support trigger. Detonates explosive charges in an area.'),
('red_bullet', 'Red Bullet', 'support', 1, 20, NULL, 'SPECTRAL_ARROW', 'Marking trigger. Reveals enemy positions to teammates.'),
('star_trigger', 'Star Trigger', 'support', 0, 0, NULL, 'IRON_INGOT', 'Universal support trigger. No cost, serves as placeholder.');

-- Insert initial season
INSERT IGNORE INTO seasons (season_name, start_date, end_date, is_active) VALUES
('Season 1', '2026-01-01 00:00:00', NULL, TRUE);

-- ============================================
-- INDEXES FOR OPTIMIZATION
-- ============================================
-- Additional composite indexes for common query patterns
ALTER TABLE match_results ADD INDEX idx_match_uuid (match_id, uuid);
ALTER TABLE match_results ADD INDEX idx_uuid_season (uuid, placement);
ALTER TABLE player_loadouts ADD INDEX idx_uuid_cost (uuid, total_cost);
ALTER TABLE weapon_rp ADD INDEX idx_uuid_rp (uuid, rp);

-- ============================================
-- TRIGGERS FOR MAINTENANCE
-- ============================================

-- Automatically update last_login on SELECT (use sparingly or prefer explicit update)
DELIMITER //
CREATE TRIGGER IF NOT EXISTS update_player_last_login_on_join
AFTER INSERT ON match_results
FOR EACH ROW
BEGIN
    UPDATE players 
    SET last_login = NOW() 
    WHERE uuid = NEW.uuid;
END//
DELIMITER ;

