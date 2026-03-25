-- BUTAI Rank Battle Database Schema
-- MySQL 8.0 compatible schema for BRB plugin
-- New naming: Frame, Ether, E-Shift, FrameSet, STRIKER/GUNNER/MARKSMAN

-- ============================================
-- PLAYERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS players (
    uuid CHAR(36) PRIMARY KEY COMMENT 'Player UUID',
    name VARCHAR(16) NOT NULL COMMENT 'Player display name',
    rank_class ENUM('S', 'A', 'B', 'C', 'UNRANKED') NOT NULL DEFAULT 'UNRANKED' COMMENT 'Overall rank tier',
    ether_cap INT NOT NULL DEFAULT 1000 COMMENT 'Maximum ether capacity',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Account creation timestamp',
    last_login TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Last login timestamp',

    INDEX idx_name (name),
    INDEX idx_rank_class (rank_class)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Core player (operator) account data';

-- ============================================
-- WEAPON RATING POINTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS weapon_rp (
    uuid CHAR(36) NOT NULL,
    weapon_type ENUM('STRIKER', 'GUNNER', 'MARKSMAN') NOT NULL COMMENT 'Weapon type',
    rp INT NOT NULL DEFAULT 1000 COMMENT 'Rating points (initial: 1000)',
    wins INT NOT NULL DEFAULT 0 COMMENT 'Total wins',
    losses INT NOT NULL DEFAULT 0 COMMENT 'Total losses',

    PRIMARY KEY (uuid, weapon_type),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_rp (rp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Per-weapon rating points and win/loss statistics';

-- ============================================
-- FRAME MASTER TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS frame_master (
    frame_id VARCHAR(64) PRIMARY KEY COMMENT 'Unique frame identifier (e.g., crescent, fang)',
    frame_name VARCHAR(64) NOT NULL UNIQUE COMMENT 'Display name',
    category ENUM('striker', 'gunner', 'marksman', 'support') NOT NULL COMMENT 'Frame category',
    ether_use INT NOT NULL DEFAULT 0 COMMENT 'Ether consumed per activation',
    ether_sustain INT NOT NULL DEFAULT 0 COMMENT 'Ether consumed per second while active',
    mc_item VARCHAR(64) NOT NULL COMMENT 'Minecraft item representing this frame',
    description TEXT COMMENT 'Frame description',
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether frame is available',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_category (category),
    INDEX idx_is_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Master list of all available frames and their properties';

-- ============================================
-- PLAYER FRAMESETS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS player_framesets (
    frameset_id INT AUTO_INCREMENT PRIMARY KEY COMMENT 'Unique frameset identifier',
    uuid CHAR(36) NOT NULL COMMENT 'Player UUID',
    frameset_name VARCHAR(64) NOT NULL COMMENT 'User-defined frameset name',
    slot_1 VARCHAR(64) COMMENT 'Frame ID in slot 1 (main weapon, required)',
    slot_2 VARCHAR(64) COMMENT 'Frame ID in slot 2',
    slot_3 VARCHAR(64) COMMENT 'Frame ID in slot 3',
    slot_4 VARCHAR(64) COMMENT 'Frame ID in slot 4',
    slot_5 VARCHAR(64) COMMENT 'Frame ID in slot 5 (sub)',
    slot_6 VARCHAR(64) COMMENT 'Frame ID in slot 6 (sub)',
    slot_7 VARCHAR(64) COMMENT 'Frame ID in slot 7 (sub)',
    slot_8 VARCHAR(64) COMMENT 'Frame ID in slot 8 (sub)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (slot_1) REFERENCES frame_master(frame_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_2) REFERENCES frame_master(frame_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_3) REFERENCES frame_master(frame_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_4) REFERENCES frame_master(frame_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_5) REFERENCES frame_master(frame_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_6) REFERENCES frame_master(frame_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_7) REFERENCES frame_master(frame_id) ON DELETE SET NULL,
    FOREIGN KEY (slot_8) REFERENCES frame_master(frame_id) ON DELETE SET NULL,
    UNIQUE KEY unique_frameset (uuid, frameset_name),
    INDEX idx_uuid (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Saved frame loadout presets for players';

-- ============================================
-- TEAMS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS teams (
    team_id INT AUTO_INCREMENT PRIMARY KEY,
    team_name VARCHAR(64) NOT NULL UNIQUE COMMENT 'Team name',
    leader_uuid CHAR(36) NOT NULL COMMENT 'Team leader UUID',
    team_rp INT NOT NULL DEFAULT 1000 COMMENT 'Team rating points',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (leader_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_team_name (team_name),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Team information';

-- ============================================
-- TEAM MEMBERS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS team_members (
    team_id INT NOT NULL,
    uuid CHAR(36) NOT NULL,
    role ENUM('leader', 'member') NOT NULL DEFAULT 'member',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (team_id, uuid),
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE CASCADE,
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    INDEX idx_uuid (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Team membership';

-- ============================================
-- SEASONS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS seasons (
    season_id INT AUTO_INCREMENT PRIMARY KEY,
    season_name VARCHAR(64) NOT NULL UNIQUE,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NULL DEFAULT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Ranking seasons';

-- ============================================
-- MATCH HISTORY TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS match_history (
    match_id INT AUTO_INCREMENT PRIMARY KEY,
    match_type ENUM('solo', 'team', 'practice') NOT NULL,
    map_name VARCHAR(64) NOT NULL,
    season_id INT DEFAULT NULL,
    result_type ENUM('kill', 'judge', 'sudden_death', 'draw', 'disconnect') NOT NULL DEFAULT 'kill' COMMENT 'How match was decided',
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL DEFAULT NULL,
    duration_sec INT DEFAULT NULL,

    FOREIGN KEY (season_id) REFERENCES seasons(season_id) ON DELETE SET NULL,
    INDEX idx_match_type (match_type),
    INDEX idx_season_id (season_id),
    INDEX idx_started_at (started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Historical record of all matches';

-- ============================================
-- MATCH RESULTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS match_results (
    result_id INT AUTO_INCREMENT PRIMARY KEY,
    match_id INT NOT NULL,
    uuid CHAR(36) NOT NULL,
    team_id INT DEFAULT NULL,
    weapon_type ENUM('STRIKER', 'GUNNER', 'MARKSMAN') NOT NULL,
    kills INT NOT NULL DEFAULT 0,
    deaths INT NOT NULL DEFAULT 0,
    damage_dealt DOUBLE NOT NULL DEFAULT 0 COMMENT 'Total damage dealt',
    ether_remaining INT NOT NULL DEFAULT 0 COMMENT 'Ether at match end',
    judge_score DOUBLE DEFAULT NULL COMMENT 'Judge score (if time expired)',
    survived BOOLEAN NOT NULL DEFAULT FALSE,
    rp_change INT NOT NULL DEFAULT 0,
    placement INT DEFAULT NULL COMMENT '1=winner',

    FOREIGN KEY (match_id) REFERENCES match_history(match_id) ON DELETE CASCADE,
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(team_id) ON DELETE SET NULL,
    INDEX idx_match_id (match_id),
    INDEX idx_uuid (uuid),
    INDEX idx_weapon_type (weapon_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Per-player match results and statistics';

-- ============================================
-- SEASON SNAPSHOTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS season_snapshots (
    snapshot_id INT AUTO_INCREMENT PRIMARY KEY,
    season_id INT NOT NULL,
    uuid CHAR(36) NOT NULL,
    weapon_type ENUM('STRIKER', 'GUNNER', 'MARKSMAN') NOT NULL,
    final_rp INT NOT NULL,
    placement INT DEFAULT NULL,

    FOREIGN KEY (season_id) REFERENCES seasons(season_id) ON DELETE CASCADE,
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    UNIQUE KEY unique_snapshot (season_id, uuid, weapon_type),
    INDEX idx_season_id (season_id),
    INDEX idx_final_rp (final_rp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Seasonal RP snapshots at season end';

-- ============================================
-- VIEWS
-- ============================================

-- Overall leaderboard (total RP = sum of all weapon RPs)
CREATE OR REPLACE VIEW player_overall_ranking AS
SELECT
    p.uuid,
    p.name,
    p.rank_class,
    COALESCE(SUM(wr.rp), 3000) AS total_rp,
    SUM(COALESCE(wr.wins, 0)) AS total_wins,
    SUM(COALESCE(wr.losses, 0)) AS total_losses,
    ROUND(SUM(COALESCE(wr.wins, 0)) /
        NULLIF(SUM(COALESCE(wr.wins, 0) + COALESCE(wr.losses, 0)), 0) * 100, 2) AS win_rate,
    p.created_at
FROM players p
LEFT JOIN weapon_rp wr ON p.uuid = wr.uuid
GROUP BY p.uuid, p.name, p.rank_class, p.created_at
ORDER BY total_rp DESC;

-- Recent matches
CREATE OR REPLACE VIEW recent_matches AS
SELECT
    mh.match_id,
    mh.match_type,
    mh.map_name,
    mh.result_type,
    mh.started_at,
    COUNT(DISTINCT mr.uuid) AS player_count,
    mh.duration_sec
FROM match_history mh
LEFT JOIN match_results mr ON mh.match_id = mr.match_id
WHERE mh.ended_at IS NOT NULL
GROUP BY mh.match_id, mh.match_type, mh.map_name, mh.result_type, mh.started_at, mh.duration_sec
ORDER BY mh.started_at DESC
LIMIT 100;

-- Weapon popularity
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
-- INITIAL DATA: FRAME MASTER
-- ============================================
INSERT IGNORE INTO frame_master (frame_id, frame_name, category, ether_use, ether_sustain, mc_item, description) VALUES
-- STRIKER
('crescent',  'Crescent',  'striker',  0,  0, 'NETHERITE_SWORD', '近接1.3倍ダメージ。高火力近接フレーム。'),
('fang',      'Fang',      'striker',  0,  0, 'GOLDEN_SWORD',    '背面攻撃1.5倍（合計2.25倍）。暗殺型フレーム。'),
('bastion',   'Bastion',   'striker',  0,  0, 'IRON_SWORD',      'シールドモード切替可能。攻防一体型。'),
-- GUNNER
('pulse',     'Pulse',     'gunner',  10,  0, 'BOW',             '標準射撃フレーム。安定した命中率。'),
('nova',      'Nova',      'gunner',  20,  0, 'CROSSBOW',        '着弾時爆発（半径4.0, 威力5）。範囲攻撃型。'),
('seeker',    'Seeker',    'gunner',  15,  0, 'TRIDENT',         'ホーミング射撃（射程20.0）。追尾型。'),
('frost',     'Frost',     'gunner',  15,  0, 'BOW',             '命中時Slowness II 3秒。制圧型。'),
-- MARKSMAN
('falcon',    'Falcon',    'marksman', 25,  0, 'BOW',            'チャージ2.0秒で2.5倍ダメージ。精密射撃型。'),
('volt',      'Volt',      'marksman', 20,  0, 'BOW',            '貫通3体、1.2倍ダメージ。貫通型。'),
('zenith',    'Zenith',    'marksman', 35,  0, 'BOW',            'チャージ3.0秒で3.0倍ダメージ。最大火力。'),
-- SUPPORT
('leap',       'Leap',       'support', 40,  0, 'FEATHER',        'ジャンプ2.5倍。空中機動用。CT8秒。'),
('barrier',    'Barrier',    'support', 30,  0, 'SHIELD',         '吸収8.0付与。CT30秒。'),
('cloak',      'Cloak',      'support',  0, 15, 'LEATHER_HELMET', '持続トグル。被ダメ60%カット。毎秒15エーテル。'),
('warp',       'Warp',       'support', 60,  0, 'ENDER_PEARL',    '瞬間移動32.0m。CT12秒。'),
('rampart',    'Rampart',    'support', 50,  0, 'GOLD_BLOCK',     '障壁生成。強度10.0、4秒持続。'),
('blast',      'Blast',      'support', 35,  0, 'TNT',            '爆発（半径5.0, 威力6.0）。'),
('tracer',     'Tracer',     'support', 20,  0, 'SPECTRAL_ARROW', '発光効果5秒。視認距離64.0。'),
('core_frame', 'Core Frame', 'support',  0,  0, 'IRON_INGOT',     'フリー枠。コストなし。初心者向け。');

-- ============================================
-- INITIAL SEASON
-- ============================================
INSERT IGNORE INTO seasons (season_name, start_date, end_date, is_active) VALUES
('Season 1', '2026-01-01 00:00:00', NULL, TRUE);

-- ============================================
-- ETHER GROWTH TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS ether_growth (
    uuid CHAR(36) NOT NULL COMMENT 'Player UUID',
    season_id INT NOT NULL COMMENT 'Season this growth belongs to',
    ep_total INT NOT NULL DEFAULT 0 COMMENT 'Total ether points earned this season',
    growth_level INT NOT NULL DEFAULT 0 COMMENT 'Current growth level (0-40)',
    ore_mined INT NOT NULL DEFAULT 0 COMMENT 'Total ores mined this season',
    mob_killed INT NOT NULL DEFAULT 0 COMMENT 'Total mobs killed this season',
    shards INT NOT NULL DEFAULT 0 COMMENT 'Current shard balance',
    shards_total INT NOT NULL DEFAULT 0 COMMENT 'Total shards earned this season',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (uuid, season_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE,
    FOREIGN KEY (season_id) REFERENCES seasons(season_id) ON DELETE CASCADE,
    INDEX idx_growth_level (growth_level),
    INDEX idx_ep_total (ep_total)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Per-player ether growth progress per season';

-- ============================================
-- PLAYER UPGRADES TABLE (permanent upgrades)
-- ============================================
CREATE TABLE IF NOT EXISTS player_upgrades (
    uuid CHAR(36) NOT NULL COMMENT 'Player UUID',
    upgrade_type VARCHAR(32) NOT NULL COMMENT 'Upgrade type (ether_cap, tower_hp)',
    upgrade_level INT NOT NULL DEFAULT 0 COMMENT 'Current upgrade level',
    total_spent INT NOT NULL DEFAULT 0 COMMENT 'Total shards spent on this upgrade',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (uuid, upgrade_type),
    FOREIGN KEY (uuid) REFERENCES players(uuid) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Permanent upgrades purchased with shards';

-- ============================================
-- COMPOSITE INDEXES
-- ============================================
ALTER TABLE match_results ADD INDEX idx_match_uuid (match_id, uuid);
ALTER TABLE weapon_rp ADD INDEX idx_uuid_rp (uuid, rp);
