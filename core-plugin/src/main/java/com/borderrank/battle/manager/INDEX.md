# Manager Package Index

Quick reference guide for Border Rank Battle manager classes.

## File Locations

```
src/main/java/com/borderrank/battle/manager/
├── TriggerRegistry.java          (4.3 KB)
├── LoadoutManager.java            (8.8 KB)
├── TrionManager.java              (9.4 KB) ★
├── RankManager.java               (7.0 KB)
├── QueueManager.java              (6.7 KB)
├── MapManager.java                (3.0 KB)
├── ScoreboardManager.java         (6.4 KB)
├── README.md                      (architecture overview)
└── INDEX.md                       (this file)
```

## Quick Method Reference

### TriggerRegistry
```java
void load(FileConfiguration config)          // Load triggers from config
TriggerData get(String id)                   // Get trigger by ID
Map<String, TriggerData> getAll()            // Get all triggers
List<TriggerData> getByCategory(Category)    // Filter by category
```

### LoadoutManager
```java
void loadPlayerLoadouts(UUID, Connection)                    // Load from DB
void saveLoadout(UUID, Loadout, Connection)                 // Save to DB
Loadout getActiveLoadout(UUID)                              // Get active
Loadout getLoadout(UUID, String)                            // Get by name
boolean setSlot(UUID, String, int, String, Registry, int)  // Equip trigger
boolean validateLoadout(Loadout, Registry, int)             // Validate
WeaponType getWeaponType(Loadout, Registry)                 // Get weapon type
```

### TrionManager ★
```java
void initPlayer(UUID, int)                          // Initialize
boolean consumeTrion(UUID, double)                  // Deduct
double getTrion(UUID)                              // Get current
int getMaxTrion(UUID)                              // Get max
void activateSustain(UUID, String)                 // Enable sustain
void deactivateSustain(UUID, String)               // Disable sustain
void startTickLoop(Plugin, Set<UUID>)              // Start 1sec tick
void stopTickLoop()                                // Stop tick
void triggerBailout(Player)                        // Emergency bailout
void updateXPBar(Player, double, int)              // Update display
```

### RankManager
```java
int calculateSoloRP(int winner, int loser)         // Elo calculation
int calculateTeamRP(int place, int kills, boolean) // Team RP
boolean checkPromotion(BRBPlayer)                  // Check B rank
boolean checkARankPromotion(BRBPlayer)             // Check A rank
boolean checkSRankPromotion(BRBPlayer)             // Check S rank
List<BRBPlayer> getTopPlayers(WeaponType, int)    // Leaderboard
List<BRBPlayer> getGlobalTopPlayers(int)          // Global leaderboard
String getHighestRankTier(BRBPlayer)               // Get rank tier
```

### QueueManager
```java
void addToSoloQueue(UUID)              // Add solo player
boolean removeFromSoloQueue(UUID)      // Remove solo
int addToTeamQueue(Set<UUID>)          // Add team (returns ID)
boolean removeTeamFromQueue(int)       // Remove team
void removeFromQueues(UUID)            // Remove from all
boolean isInQueue(UUID)                // Check if queued
Set<UUID> trySoloMatch(int min)       // Form solo match
Set<Integer> tryTeamMatch(int min)     // Form team match
int getSoloQueueSize()                 // Solo queue count
int getTeamQueueSize()                 // Team queue count
Set<UUID> getTeamMembers(int teamId)   // Get team members
long getEstimatedWaitTime(UUID)        // Estimate wait time
```

### MapManager
```java
void loadMaps(FileConfiguration)       // Load from config
String selectRandomMap()               // Random selection
List<String> getAvailableMaps()        // Get all maps
boolean mapExists(String)              // Check if exists
void addMap(String)                    // Add manually
boolean removeMap(String)              // Remove manually
int getMapCount()                      // Count maps
void clearMaps()                       // Clear all
```

### ScoreboardManager
```java
void createMatchScoreboard(Player, String, int)              // Create
void updateScoreboard(Player, int, int, int, double)        // Update
void removeScoreboard(Player)                                // Remove
boolean hasScoreboard(UUID)                                  // Check active
void clearAllScoreboards()                                   // Clear all
int getActiveScoreboardCount()                               // Count
```

## Constants & Values

### TrionManager
- `HP_LEAK_COEFFICIENT = 0.5` per second per missing HP
- `WARNING_THRESHOLD_HIGH = 200` (yellow warning)
- `WARNING_THRESHOLD_CRITICAL = 100` (red blinking)

### RankManager
- `BASE_RP_CHANGE = 30`
- `OPPONENT_SCALING = 1.0 / 1000.0`
- `MIN_RP_CHANGE = 5`
- `MAX_RP_CHANGE = 60`
- Rank Thresholds: B=1500, A=3000, S=5000

## Data Structures

### TriggerRegistry
- `Map<String, TriggerData> triggers`

### LoadoutManager
- `Map<UUID, Map<String, Loadout>> playerLoadouts`

### TrionManager
- `Map<UUID, Double> currentTrion`
- `Map<UUID, Integer> maxTrion`
- `Map<UUID, Set<String>> activeSustainTriggers`
- `BukkitTask tickTask`

### RankManager
- Uses `PlayerDAO` for persistence

### QueueManager
- `Set<UUID> soloQueue`
- `Map<Integer, Set<UUID>> teamQueue`

### MapManager
- `List<String> availableMaps`

### ScoreboardManager
- `Map<UUID, Scoreboard> playerScoreboards`
- `Map<UUID, Objective> playerObjectives`

## Dependency Graph

```
TriggerRegistry
    ↑
    └── (used by) LoadoutManager
         ↑
         └── (used by) Game Logic

TrionManager ★ CRITICAL
    ├── (requires) Plugin instance
    ├── (requires) Player objects
    └── (requires) Active player set

RankManager
    └── (uses) PlayerDAO

QueueManager
    └── (no external dependencies)

MapManager
    └── (uses) FileConfiguration

ScoreboardManager
    └── (requires) Player objects
```

## Configuration Examples

### Trigger Definition (triggers.yml)
```yaml
triggers:
  rifle_shot:
    name: "Rifle Shot"
    category: "MAIN_ATTACK"
    cost: 100
    cooldown: 5
    baseAttackPower: 20.0
    weaponType: "RIFLE"
    isMainAttack: true
```

### Map Definition (config.yml)
```yaml
maps:
  arena_01:
    name: "Arena One"
    spawn-points:
      - "100:64:200"
      - "150:64:200"
      - "100:64:250"
```

## Lifecycle

### Match Start
1. `TriggerRegistry.load()` - Load all triggers
2. `MapManager.selectRandomMap()` - Choose map
3. `TrionManager.initPlayer()` - Init each player
4. `LoadoutManager.getActiveLoadout()` - Get loadouts
5. `ScoreboardManager.createMatchScoreboard()` - Create scoreboards
6. `TrionManager.startTickLoop()` - Start 1sec tick

### Match Ongoing
- `TrionManager` ticks every second
- `ScoreboardManager.updateScoreboard()` called frequently
- `RankManager` calculates RP as needed

### Match End
1. `TrionManager.stopTickLoop()` - Stop ticks
2. `ScoreboardManager.removeScoreboard()` - Remove displays
3. `RankManager.calculateSoloRP()` - Calculate RP
4. Save results to database

---

**Last Updated:** 2026-03-03  
**Package:** com.borderrank.battle.manager  
**Status:** Complete and Ready
