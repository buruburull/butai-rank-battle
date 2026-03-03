# Border Rank Battle - Quick Reference Guide

## File Locations
All files are located in:
```
/sessions/vibrant-practical-albattani/mnt/outputs/brb-project/core-plugin/src/main/java/com/borderrank/battle/
```

## Main Plugin Class
**BRBPlugin.java** - Entry point for the entire plugin
- Extends: `JavaPlugin`
- Key methods:
  - `onEnable()` - Initializes all managers and registers commands/listeners
  - `onDisable()` - Cleanup and database closure
  - `getInstance()` - Static singleton accessor
  - Getter methods for all managers

### Usage Example:
```java
BRBPlugin plugin = BRBPlugin.getInstance();
RankManager rankManager = plugin.getRankManager();
QueueManager queueManager = plugin.getQueueManager();
```

## Commands Reference

### /rank Command
```
/rank solo              → Joins solo queue
/rank cancel            → Leaves queue
/rank stats [player]    → Shows player RP by weapon
/rank top [weapon]      → Shows top 10 rankings
```

### /trigger Command
```
/trigger list [category]         → Lists available triggers
/trigger set <1-8> <trigger_id>  → Sets trigger in loadout slot
/trigger remove <1-8>            → Removes trigger from slot
/trigger view                    → Shows your loadout
/trigger preset save <name>      → Saves loadout as preset
/trigger preset load <name>      → Loads saved preset
```

### /team Command
```
/team create <name>   → Creates team (B rank+)
/team invite <player> → Invites player to team
/team leave           → Leaves current team
/team info [name]     → Shows team information
```

### /bradmin Command (brb.admin permission)
```
/bradmin trigger reload              → Reloads triggers.yml
/bradmin forcestart                  → Force starts match
/bradmin rp set <player> <weapon> <value> → Set player RP
/bradmin season start <name>         → Starts new season
/bradmin season end                  → Ends current season
```

## Event Listeners

### PlayerConnectionListener
- **onJoin**: Async player data loading and initialization
- **onQuit**: Player data saving and cleanup

### CombatListener
- **onDamage**: Applies trigger-based damage modifications
- **onDeath**: Records kills and checks match end
- **onRegen**: Cancels natural regen in matches

### TriggerUseListener
- **onInteract**: Right-click trigger usage (Grasshopper, Teleporter, Escudo)
- **onSwapHand**: F key for loadout swapping

## Trigger Mechanics

### Grasshopper
- Launches player upward
- Cost: 100 trion
- Cooldown: 500ms

### Teleporter
- Raycasts 15 blocks forward
- Teleports to hit location
- Cost: 50 trion
- Cooldown: 500ms

### Escudo
- Creates protective glass wall
- Duration: 10 seconds
- Cost: 200 trion
- Cooldown: 500ms

### Scorpion
- Backstab multiplier: 1.5x damage
- Detection: Behind victim (>90° angle)
- Melee trigger

## Arena Management

### Match States
```
WAITING   → Match created, waiting to start
COUNTDOWN → Countdown phase (10s solo, 30s team)
ACTIVE    → Match in progress
ENDING    → Match ending, calculating results
FINISHED  → Match complete, removed from system
```

### Match Lifecycle
1. Players queue up
2. Match created with 2+ players
3. Countdown phase starts
4. Active phase with kill tracking
5. End condition: All dead except winner(s) or time limit
6. Results calculated and saved
7. Players teleported to lobby

## Database Integration

### Player Data
- UUID and name
- Ranking points by weapon type
- Team membership
- Loadout presets
- Match history

### Async Operations
All database operations use async tasks:
```java
plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
    // Async DB operation
});
```

## Important Notes

1. **Thread Safety**: Use async tasks for database operations
2. **Manager Access**: Always use `BRBPlugin.getInstance()` to get managers
3. **Config Loading**: Default config is saved on first run
4. **Permissions**: 
   - Admin commands require `brb.admin`
   - Team creation requires B rank or higher
5. **Cooldowns**: 500ms global trigger cooldown
6. **Trion**: Each player has 1000 starting trion per match

## Compilation Requirements
- Java 21+
- Spigot/Bukkit API
- Maven or similar build tool

## Testing Checklist
- [ ] Commands tab-complete properly
- [ ] Player data loads on join
- [ ] Matches start with 2+ players
- [ ] Kills are recorded correctly
- [ ] RP is calculated and saved
- [ ] Triggers consume trion
- [ ] Cooldowns work properly
- [ ] Admin commands require permission
- [ ] Database persists player data
