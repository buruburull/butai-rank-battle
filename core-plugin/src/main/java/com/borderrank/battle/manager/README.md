# Border Rank Battle - Manager Package

This package (`com.borderrank.battle.manager`) contains the core business logic managers for the Border Rank Battle plugin v1.21.11.

## Package Contents

### 7 Core Manager Classes

1. **TriggerRegistry** - Trigger definition loading and caching
2. **LoadoutManager** - Player loadout management with database persistence
3. **TrionManager** - Real-time resource management during matches
4. **RankManager** - RP calculation and rank progression
5. **QueueManager** - Matchmaking queue system
6. **MapManager** - Arena/map selection and management
7. **ScoreboardManager** - In-match display and statistics

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│             Border Rank Battle Managers                 │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │ TriggerRegistry                                  │  │
│  │ - Loads trigger definitions from config         │  │
│  │ - Provides trigger lookup and filtering         │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↑                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │ LoadoutManager                                   │  │
│  │ - Manages player loadouts (cache + DB)          │  │
│  │ - Validates loadouts against TriggerRegistry    │  │
│  │ - Determines weapon types                       │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↑                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │ TrionManager ★ CRITICAL                         │  │
│  │ - Real-time resource management (1sec tick)     │  │
│  │ - HP leak calculation                           │  │
│  │ - Sustain cost tracking                         │  │
│  │ - Emergency bailout triggers                    │  │
│  │ - XP bar & action bar updates                   │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↑                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │ RankManager                                      │  │
│  │ - Solo match RP (Elo-like formula)              │  │
│  │ - Team match RP (placement + kills + survival)  │  │
│  │ - Rank tier checking (S/A/B/C/D)               │  │
│  │ - Leaderboard queries                           │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↑                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │ QueueManager                                     │  │
│  │ - Solo and team matchmaking queues              │  │
│  │ - Match formation with threshold checks         │  │
│  │ - Queue status tracking                         │  │
│  │ - Wait time estimation                          │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↑                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │ MapManager                                       │  │
│  │ - Loads available maps from config              │  │
│  │ - Random map selection                          │  │
│  │ - Arena availability checking                   │  │
│  └──────────────────────────────────────────────────┘  │
│                          ↑                               │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ScoreboardManager                                │  │
│  │ - Creates/updates match scoreboards             │  │
│  │ - Real-time stat display (kills, players, time) │  │
│  │ - Team-based line management (no flicker)       │  │
│  │ - Per-player sidebar management                 │  │
│  └──────────────────────────────────────────────────┘  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

## Usage Example

```java
public class BRBGameManager {
    private final TriggerRegistry triggers;
    private final LoadoutManager loadouts;
    private final TrionManager trion;
    private final RankManager rank;
    private final QueueManager queue;
    private final MapManager maps;
    private final ScoreboardManager scoreboards;

    public BRBGameManager() {
        triggers = new TriggerRegistry();
        loadouts = new LoadoutManager(loadoutDAO);
        trion = new TrionManager();
        rank = new RankManager(playerDAO);
        queue = new QueueManager();
        maps = new MapManager();
        scoreboards = new ScoreboardManager();
    }

    public void initializeMatch(Set<UUID> players) {
        // Initialize trion for all players
        for (UUID playerId : players) {
            trion.initPlayer(playerId, 5000);
        }

        // Start tick loop
        trion.startTickLoop(plugin, players);

        // Create scoreboards
        for (UUID playerId : players) {
            Player p = Bukkit.getPlayer(playerId);
            String map = maps.selectRandomMap();
            scoreboards.createMatchScoreboard(p, map, 600); // 10 minute match
        }
    }

    public void endMatch(UUID winnerId, UUID loserId) {
        // Calculate RP
        int winnerRP = getPlayerRP(winnerId);
        int loserRP = getPlayerRP(loserId);
        int rpChange = rank.calculateSoloRP(winnerRP, loserRP);

        // Stop trion manager
        trion.stopTickLoop();

        // Remove scoreboards
        scoreboards.clearAllScoreboards();
    }
}
```

## Key Design Decisions

1. **Trion Manager is Critical** - Marked with star comment. It runs a recurring task every second and must be properly lifecycle-managed.

2. **Layered Architecture** - Managers are relatively independent but some depend on others (e.g., LoadoutManager depends on TriggerRegistry).

3. **Database Abstraction** - LoadoutManager and RankManager use DAO pattern for database operations, allowing easy switching of storage backends.

4. **No Static State** - All managers use instance variables, allowing multiple plugin instances or testing scenarios.

5. **Null Safety** - Comprehensive null checks throughout, never assuming data existence.

6. **Event-Free Design** - Managers focus on state management and business logic, not event handling (see event package for that).

## Compilation Requirements

- Java 21+
- Bukkit/Spigot API
- Models: TriggerData, TriggerCategory, Loadout, BRBPlayer, WeaponType
- DAOs: LoadoutDAO, PlayerDAO

## Testing Considerations

- Mock the Bukkit scheduler for TrionManager testing
- Mock database connections for LoadoutManager and RankManager
- Create fixture data for queue and map managers
- Test boundary conditions in RP calculations
- Verify scoreboard updates don't cause flicker

---

**Package:** `com.borderrank.battle.manager`  
**Java Version:** 21  
**Created:** 2026-03-03  
**Status:** Complete and Ready for Compilation
