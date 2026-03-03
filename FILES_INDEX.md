# Border Rank Battle - Complete Files Index

## Location
Base path: `/sessions/vibrant-practical-albattani/mnt/outputs/brb-project/core-plugin/src/main/java/com/borderrank/battle`

## File Listing

### 1. Main Plugin Class (1 file)

#### BRBPlugin.java (176 lines)
**Location**: `BRBPlugin.java`
**Purpose**: Main plugin entry point and manager coordinator
**Extends**: `org.bukkit.plugin.java.JavaPlugin`
**Key Methods**:
- `onEnable()` - Initialize all managers and listeners on startup
- `onDisable()` - Clean up and close database on shutdown
- `getInstance()` - Get singleton instance
- `getDatabaseManager()`, `getTriggerRegistry()`, etc. - Get managers
**Dependencies**: 
- All manager classes
- All command handlers
- All event listeners
- DatabaseManager

**Responsibilities**:
- Load plugin configuration
- Initialize MySQL database connection
- Create and register all managers
- Register all commands (/rank, /trigger, /team, /bradmin)
- Register all event listeners
- Start server ticking tasks

---

### 2. Command Handlers (4 files in `command/`)

#### RankCommand.java (153 lines)
**Location**: `command/RankCommand.java`
**Purpose**: Handle /rank command and its subcommands
**Implements**: `CommandExecutor`, `TabCompleter`
**Subcommands**:
- `solo` - Join solo queue (calls QueueManager.addPlayer)
- `cancel` - Leave queue (calls QueueManager.removePlayer)
- `stats [player]` - Show RP statistics (calls RankManager.getPlayer)
- `top [weapon]` - Show top 10 rankings (calls RankManager.getTopRanked)
**Tab Completion**: 
- Level 1: solo, cancel, stats, top
- Level 2: Player names, weapon types
**Uses**: MessageUtil for user-friendly messages

---

#### TriggerCommand.java (231 lines)
**Location**: `command/TriggerCommand.java`
**Purpose**: Handle /trigger command for loadout management
**Implements**: `CommandExecutor`, `TabCompleter`
**Subcommands**:
- `list [category]` - Show available triggers with costs
- `set <slot> <trigger_id>` - Set trigger in slot 1-8
- `remove <slot>` - Remove trigger from slot
- `view [player]` - Show current loadout
- `preset save <name>` - Save loadout as preset
- `preset load <name>` - Load saved preset
**Tab Completion**: Trigger IDs, slot numbers 1-8, preset names
**Uses**: LoadoutManager for loadout management

---

#### TeamCommand.java (212 lines)
**Location**: `command/TeamCommand.java`
**Purpose**: Handle /team command for team management
**Implements**: `CommandExecutor`, `TabCompleter`
**Subcommands**:
- `create <name>` - Create new team (requires B rank+)
- `invite <player>` - Invite player to team
- `leave` - Leave current team
- `info [name]` - Show team information
**Rank Check**: Team creation requires B rank or higher
**Tab Completion**: Team names, player names
**Uses**: RankManager for team and rank validation

---

#### AdminCommand.java (197 lines)
**Location**: `command/AdminCommand.java`
**Purpose**: Handle /bradmin admin command
**Implements**: `CommandExecutor`, `TabCompleter`
**Permission Required**: `brb.admin`
**Subcommands**:
- `trigger reload` - Reload triggers.yml configuration
- `forcestart` - Force start match with queue players
- `rp set <player> <weapon> <value>` - Manually set player RP
- `season start <name>` - Start new ranking season
- `season end` - End current season
**Tab Completion**: With permission checks
**Uses**: TriggerRegistry, RankManager for admin operations

---

### 3. Event Listeners (3 files in `listener/`)

#### PlayerConnectionListener.java (90 lines)
**Location**: `listener/PlayerConnectionListener.java`
**Purpose**: Handle player join and leave events
**Implements**: `Listener`
**Event Handlers**:
- `@EventHandler onJoin(PlayerJoinEvent)` (async)
  - Load player data from database
  - Create new player if first join
  - Initialize trion resources
  - Cache player data in memory
- `@EventHandler onQuit(PlayerQuitEvent)` (async)
  - Save player data to database
  - Remove from queue if in queue
  - Clean up trion state
  - Remove from cache
**Uses**: RankManager, TrionManager, DatabaseManager

---

#### CombatListener.java (146 lines)
**Location**: `listener/CombatListener.java`
**Purpose**: Handle combat events and damage mechanics
**Implements**: `Listener`
**Event Handlers**:
- `@EventHandler onDamage(EntityDamageByEntityEvent)`
  - Modify damage based on equipped trigger
  - Implement Scorpion backstab (1.5x from behind)
  - Check if attacker is in match
- `@EventHandler onDeath(PlayerDeathEvent)`
  - Record kill in match
  - Check match end conditions
  - Clear item drops
- `@EventHandler onRegen(EntityRegainHealthEvent)`
  - Cancel natural health regen during matches
  - Only cancels SATIATED regen (not potions)
**Helper Methods**:
- `isBehind(Player, Player)` - Detect backstab angle (>90°)
**Uses**: MatchManager, LoadoutManager

---

#### TriggerUseListener.java (238 lines)
**Location**: `listener/TriggerUseListener.java`
**Purpose**: Handle trigger activation and usage
**Implements**: `Listener`
**Event Handlers**:
- `@EventHandler onInteract(PlayerInteractEvent)` (right-click)
  - Handle trigger usage on right-click
  - Check cooldown (500ms)
  - Validate trion consumption
  - Execute trigger effects:
    - GRASSHOPPER: Launch upward with velocity
    - TELEPORTER: Raycast 15 blocks and teleport
    - ESCUDO: Create glass wall (10s duration)
- `@EventHandler onSwapHand(PlayerSwapHandItemsEvent)` (F key)
  - Swap between main (0-3) and sub (4-7) loadouts
**Helper Methods**:
- `isInMatch(Player)` - Check if player in active match
- `getActiveSlot(Player)` - Get current trigger slot
- `isOnCooldown(UUID)` - Check trigger cooldown
- `setCooldown(UUID)` - Set cooldown with timestamp
**Uses**: TrionManager, LoadoutManager, MatchManager

---

### 4. Arena Management (2 files in `arena/`)

#### ArenaInstance.java (304 lines)
**Location**: `arena/ArenaInstance.java`
**Purpose**: Manage a single match instance
**Key Features**:
- **Inner Enum**: `ArenaState` (WAITING, COUNTDOWN, ACTIVE, ENDING, FINISHED)
- **Field Variables**:
  - `matchId`: Unique match identifier
  - `state`: Current match state
  - `players`: All players in match
  - `alivePlayers`: Currently alive players
  - `kills`: Kill count per player
  - `mapName`: Arena map name
  - `startTime`: Match start timestamp
  - `timeLimitSec`: Match time limit (300 solo, 600 team)
**Key Methods**:
- `start()` - Initialize match
  - Teleport players to spawn points (10 blocks apart)
  - Give trigger items from loadouts
  - Initialize trion (1000 per player)
  - Disable natural regen
  - Start countdown phase
- `onKill(UUID killer, UUID victim)` - Record kill and check end
- `tick()` - Called every second
  - Handle countdown state (decrements timer)
  - Handle active state (update scoreboard)
  - Check time limit expiration
- `end()` - End match and calculate results
  - Sort players by kills
  - Calculate RP gains: (10 - placement) + kills
  - Save results to database
  - Teleport to lobby
  - Send summary message
- `getAlivePlayers()` - Get set of alive players
- `getPlayerKills(UUID)` - Get kill count for player
**Uses**: LoadoutManager, TrionManager, RankManager, ScoreboardManager

---

#### MatchManager.java (146 lines)
**Location**: `arena/MatchManager.java`
**Purpose**: Orchestrate all active matches
**Key Features**:
- **Field Variables**:
  - `activeMatches`: Map of matchId -> ArenaInstance
  - `nextMatchId`: Auto-incrementing match ID
**Key Methods**:
- `createSoloMatch(Set<UUID>, String)` - Create solo match
  - Requires 2+ players
  - 5 minute (300 sec) time limit
  - Returns match ID
- `createTeamMatch(Map<Integer, Set<UUID>>, String)` - Create team match
  - Requires 2+ teams
  - 10 minute (600 sec) time limit
  - Returns match ID
- `getPlayerMatch(UUID)` - Get active match for player or null
- `isInMatch(UUID)` - Boolean check for player in match
- `getMatch(int matchId)` - Get match by ID
- `endMatch(int matchId)` - End and remove match
- `tick()` - Called every second from plugin
  - Ticks all active arenas
  - Removes finished matches automatically
- `getActiveMatchCount()` - Count of active matches
- `getTotalPlayersInMatches()` - Total players in all matches
**Uses**: ArenaInstance

---

## File Statistics

| Category | Count | Files | Lines |
|----------|-------|-------|-------|
| Main Plugin | 1 | BRBPlugin.java | 176 |
| Commands | 4 | RankCommand, TriggerCommand, TeamCommand, AdminCommand | 793 |
| Listeners | 3 | PlayerConnection, Combat, TriggerUse | 474 |
| Arena | 2 | ArenaInstance, MatchManager | 450 |
| **TOTAL** | **10** | | **1,893** |

## Import Dependencies

All files use:
```java
import org.bukkit.*;           // Minecraft API
import org.bukkit.entity.*;    // Entity classes
import org.bukkit.event.*;     // Event system
import org.bukkit.command.*;   // Command system
import java.util.*;            // Collections
import java.util.UUID;         // Player identification
```

Custom imports from this project:
```java
import com.borderrank.battle.*;              // BRBPlugin
import com.borderrank.battle.database.*;     // DatabaseManager
import com.borderrank.battle.manager.*;      // All managers
import com.borderrank.battle.model.*;        // Data models
import com.borderrank.battle.util.*;         // MessageUtil
```

## Method Signatures Reference

### Command Executor Pattern
```java
public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args)
```

### Event Handler Pattern
```java
@EventHandler
public void onEventName(EventType event)
```

### Manager Access Pattern
```java
BRBPlugin plugin = BRBPlugin.getInstance();
ManagerType manager = plugin.getManagerGetter();
```

## Compilation Order
1. BRBPlugin.java (defines managers)
2. Command handlers (use managers)
3. Event listeners (use managers and commands)
4. Arena management (orchestrates matches)

## Configuration Files Needed
- `plugin.yml` - Plugin metadata and commands
- `config.yml` - MySQL and game configuration
- `triggers.yml` - Trigger definitions

---

## Next Steps
1. Create model classes (BRBPlayer, Trigger, Team)
2. Create database manager and queries
3. Create remaining managers
4. Create MessageUtil for messaging
5. Create plugin.yml with commands and permissions
6. Create default configuration files
7. Build and test with Maven
