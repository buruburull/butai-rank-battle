# Border Rank Battle - Common Module

This is the common module for the Border Rank Battle Minecraft plugin (Paper 1.21.11).

## Package Structure

### `com.borderrank.battle.model`
Data models for the plugin:

- **BRBPlayer.java** - Player data model with UUID, name, rank class, trion stats, and weapon RP mapping
  - Fields: uuid, name, rankClass, trionCap (15), trionMax (1000)
  - Contains Map<WeaponType, WeaponRP> for multi-weapon tracking
  
- **WeaponType.java** - Enum with three weapon types: ATTACKER, SHOOTER, SNIPER

- **RankClass.java** - Enum with four rank classes (C, B, A, S) with Japanese display names and ChatColor

- **WeaponRP.java** - Ranking points data for each weapon
  - Fields: type, rp (default 1000), wins, losses
  - Methods: addWin(), addLoss(), getRp(), setRp(), getWinRate()

- **TriggerData.java** - Trigger definition loaded from YAML
  - Fields: id, name, category (ATTACKER/SHOOTER/SNIPER/SUPPORT), cost, trionUse, trionSustain
  - SlotType enum (MAIN/SUB/BOTH), Minecraft item, cooldown, description
  - Includes Builder pattern for flexible construction

- **Loadout.java** - Player loadout with 8 slots (4 main, 4 sub)
  - Methods: getSlot(), setSlot(), calculateTotalCost(), isValid(), getMainTriggers(), getSubTriggers()

### `com.borderrank.battle.database`
Database access layer:

- **DatabaseManager.java** - MySQL connection pool manager using HikariCP
  - Constructor takes host, port, database, username, password
  - init() method creates tables
  - getConnection() returns pooled connection
  - close() shuts down the pool
  - Async-safe operations

- **PlayerDAO.java** - Data access for players and weapon RP
  - loadPlayer(UUID): Load player from database
  - savePlayer(BRBPlayer): Upsert player data
  - loadWeaponRP(UUID): Load all weapon RP data
  - saveWeaponRP(UUID, WeaponType, WeaponRP): Upsert weapon RP
  - getTopPlayers(WeaponType, limit): Get ranking list
  - getTopPlayersByWins(limit): Get top players by total wins
  - deletePlayer(UUID): Remove player from database

### `com.borderrank.battle.util`
Utility classes:

- **MessageUtil.java** - Chat message formatting and sending
  - prefix(): Returns "[BRB] " in gold color
  - send(Player, String): Send prefixed message
  - sendError(Player, String): Send red error message
  - sendSuccess(Player, String): Send green success message
  - sendInfo(Player, String): Send aqua info message
  - sendWarning(Player, String): Send yellow warning message
  - broadcast(String): Broadcast to all players
  - formatRP(int): Format RP with color (green >1500, yellow >1000, red <=1000)
  - formatWinRate(double): Format win rate with color
  - centerMessage(String): Create centered message

## Database Schema

The DatabaseManager automatically creates the following tables:

- **players** - Player profiles with rank class and trion stats
- **weapon_rp** - RP, wins, and losses for each weapon type per player
- **loadouts** - Named loadout configurations
- **loadout_slots** - Individual trigger slots for loadouts

## Dependencies

- Paper API 1.21.4-R0.1-SNAPSHOT
- HikariCP (for connection pooling)
- MySQL JDBC Driver
- Java 21+

## Notes

- All classes use proper JavaDoc documentation
- The module is designed to be async-safe for database operations
- UUIDs are used throughout for player identification
- The plugin supports multiple weapon types with independent RP tracking
