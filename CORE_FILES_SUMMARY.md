# Border Rank Battle - Core Plugin Files

## Summary
Created 10 new core Java files for the Border Rank Battle 1.21.11 Minecraft plugin using Java 21.

## Files Created

### 1. Main Plugin Class
**File**: `/sessions/vibrant-practical-albattani/mnt/outputs/brb-project/core-plugin/src/main/java/com/borderrank/battle/BRBPlugin.java` (176 lines)

Main plugin class extending JavaPlugin. Features:
- Loads configuration and initializes DatabaseManager with MySQL connection parameters
- Initializes all core managers: TriggerRegistry, LoadoutManager, TrionManager, RankManager, QueueManager, MapManager, ScoreboardManager, MatchManager
- Registers all commands and event listeners
- Manages plugin lifecycle (onEnable/onDisable)
- Provides static getInstance() and getter methods for all managers
- Implements main ticking task for match manager

### 2. Command Handlers

#### RankCommand.java (153 lines)
`/rank` command with subcommands:
- `/rank solo` - Adds player to solo queue
- `/rank cancel` - Removes player from queue
- `/rank stats [player]` - Shows RP for each weapon type
- `/rank top [weapon]` - Shows top 10 rankings
- Tab completion support for subcommands and weapon types

#### TriggerCommand.java (231 lines)
`/trigger` command with subcommands:
- `/trigger list [category]` - Lists available triggers with costs
- `/trigger set <slot 1-8> <trigger_id>` - Sets trigger in loadout slot
- `/trigger remove <slot 1-8>` - Removes trigger from slot
- `/trigger view [player]` - Shows full loadout with costs
- `/trigger preset save <name>` - Saves loadout as preset
- `/trigger preset load <name>` - Loads a preset
- Tab completion for trigger IDs and slot numbers

#### TeamCommand.java (212 lines)
`/team` command with subcommands:
- `/team create <name>` - Creates team (B rank+ required)
- `/team invite <player>` - Invites player to team
- `/team leave` - Removes player from team
- `/team info [name]` - Shows team info with members and RP
- Tab completion for team names and player names

#### AdminCommand.java (197 lines)
`/bradmin` command (permission: brb.admin) with subcommands:
- `/bradmin trigger reload` - Reloads triggers.yml
- `/bradmin forcestart` - Force starts a match
- `/bradmin rp set <player> <weapon> <value>` - Manually sets player RP
- `/bradmin season start <name>` - Starts new season
- `/bradmin season end` - Ends current season
- Tab completion with permission checks

### 3. Event Listeners

#### PlayerConnectionListener.java (90 lines)
Handles player join/leave events:
- `@EventHandler onJoin(PlayerJoinEvent)` - Loads player data asynchronously, creates new players
- `@EventHandler onQuit(PlayerQuitEvent)` - Saves player data, cleans up trion state
- Manages player caching and database synchronization

#### CombatListener.java (146 lines)
Handles combat events:
- `@EventHandler onDamage(EntityDamageByEntityEvent)` - Modifies damage based on equipped trigger (e.g., Scorpion backstab 1.5x)
- `@EventHandler onDeath(PlayerDeathEvent)` - Records kills, checks match end condition
- `@EventHandler onRegen(EntityRegainHealthEvent)` - Cancels natural HP regen during matches
- Helper methods for backstab detection

#### TriggerUseListener.java (238 lines)
Handles trigger activation:
- `@EventHandler onInteract(PlayerInteractEvent)` - Right-click trigger usage with cooldown and trion checks
  - GRASSHOPPER: Launches player upward
  - TELEPORTER: Raycasts 15 blocks and teleports
  - ESCUDO: Places protective glass wall blocks (10s duration)
- `@EventHandler onSwapHand(PlayerSwapHandItemsEvent)` - F key for loadout swapping (main/sub)
- Cooldown system (500ms between uses)
- Trion consumption validation

### 4. Arena Management

#### ArenaInstance.java (304 lines)
Manages a single match instance:
- Enum: ArenaState (WAITING, COUNTDOWN, ACTIVE, ENDING, FINISHED)
- Methods:
  - `start()` - Teleports players to spawns, initializes trion, starts countdown
  - `onKill(UUID, UUID)` - Records kill, checks end condition
  - `tick()` - Called every second, updates scoreboard, checks time limit
  - `end()` - Calculates placements, computes RP changes, sends summary
  - `getAlivePlayers()` - Returns set of alive players
- Tracks: match ID, state, players, kills, map name, time limit

#### MatchManager.java (146 lines)
Orchestrates all active matches:
- `createSoloMatch(Set<UUID>, String)` - Creates solo match (5 min limit)
- `createTeamMatch(Map<Integer, Set<UUID>>, String)` - Creates team match (10 min limit)
- `getPlayerMatch(UUID)` - Gets player's current match
- `isInMatch(UUID)` - Boolean check for match status
- `endMatch(int)` - Cleanup and removal
- `tick()` - Ticks all active arenas, removes finished matches
- Tracks: active matches, next match ID counter

## File Structure
```
com/borderrank/battle/
├── BRBPlugin.java                 [Main plugin class]
├── arena/
│   ├── ArenaInstance.java         [Single match instance]
│   └── MatchManager.java          [Match orchestration]
├── command/
│   ├── RankCommand.java           [/rank command]
│   ├── TriggerCommand.java        [/trigger command]
│   ├── TeamCommand.java           [/team command]
│   └── AdminCommand.java          [/bradmin command]
└── listener/
    ├── PlayerConnectionListener.java
    ├── CombatListener.java
    └── TriggerUseListener.java
```

## Key Features

### Command System
- 4 main commands with multiple subcommands
- Full tab completion support
- Permission checks for admin commands
- User-friendly error messaging via MessageUtil

### Event System
- Player join/leave handling with async data loading
- Combat mechanics with trigger-based damage modification
- Trigger usage with cooldowns, trion consumption, and effects
- Backstab detection for melee triggers

### Match System
- Match creation (solo and team)
- Countdown and active phases
- Kill tracking and match end conditions
- Automatic RP calculation based on placement and kills
- Time limit enforcement

### Manager Integration
- All classes integrate with BRBPlugin.getInstance()
- Proper async/sync task handling
- Database integration for persistence
- Proper resource cleanup

## Dependencies
- Bukkit/Spigot API (Minecraft 1.21.1)
- Custom model classes (BRBPlayer, Trigger, Team)
- Custom managers (RankManager, LoadoutManager, etc.)
- MessageUtil for standardized messaging
- DatabaseManager for persistence

## Total Lines of Code
**1,893 lines** of complete, compilable Java 21 code

All files are ready for compilation and deployment.
