# Border Rank Battle - Implementation Details

## Architecture Overview

### Design Patterns Used

#### 1. **Singleton Pattern**
- `BRBPlugin.getInstance()` provides single access point
- Static instance initialized on plugin enable
- All managers accessed through main plugin class

#### 2. **Manager Pattern**
Multiple specialized managers handle specific domains:
- **RankManager**: Player rankings and RP
- **LoadoutManager**: Trigger loadouts and presets
- **TrionManager**: Trion resource management
- **QueueManager**: Player queuing for matches
- **MapManager**: Map loading and management
- **ScoreboardManager**: Player scoreboard updates
- **MatchManager**: Match orchestration
- **TriggerRegistry**: Trigger configuration

#### 3. **Observer Pattern (Event Listeners)**
- PlayerConnectionListener: Join/leave events
- CombatListener: Combat events
- TriggerUseListener: Interaction events
- Each uses `@EventHandler` annotations

#### 4. **Factory Pattern**
- `MatchManager.createSoloMatch()`: Creates solo matches
- `MatchManager.createTeamMatch()`: Creates team matches
- `ArenaInstance`: Encapsulates match logic

### Thread Safety

#### Async Database Operations
```java
plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
    // Database operation
    BRBPlayer player = rankManager.getPlayer(uuid);
    rankManager.savePlayer(player);
});
```

#### Sync Callbacks
```java
plugin.getServer().getScheduler().runTask(plugin, () -> {
    // Back to main thread for Bukkit API calls
    MessageUtil.sendInfoMessage(player, "Message");
});
```

## Command Implementation Pattern

All commands follow this structure:

```java
public class XXXCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            // Handle non-player
            return true;
        }
        
        if (args.length == 0) {
            // Show usage
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        switch(subcommand) {
            case "action1" -> handleAction1(player, args);
            case "action2" -> handleAction2(player, args);
            default -> MessageUtil.sendErrorMessage(player, "Unknown: " + subcommand);
        }
        
        return true;
    }
    
    private void handleActionX(Player player, String[] args) {
        // Implementation
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        // Tab completion logic
    }
}
```

## Event Listener Pattern

Standard Bukkit event listening:

```java
public class XXXListener implements Listener {
    @EventHandler
    public void onEventName(EventType event) {
        // Implementation with null checks
        if (event.getEntity() instanceof Player player) {
            // Handle player event
        }
    }
}
```

## Manager Integration Points

### How Managers Work Together

1. **Player Login**
   - PlayerConnectionListener.onJoin() triggers
   - RankManager.getPlayer() loads from database
   - TrionManager.initializePlayer() sets trion
   - ScoreboardManager updates display

2. **Match Creation**
   - QueueManager has 2+ players
   - MatchManager.createSoloMatch() creates ArenaInstance
   - ArenaInstance.start() teleports and initializes
   - Match enters COUNTDOWN state

3. **Combat**
   - CombatListener.onDamage() checks equipped trigger
   - LoadoutManager.getLoadout() retrieves loadout
   - Damage multiplied based on trigger type
   - Scorpion backstab detection via angle calculation

4. **Trigger Usage**
   - TriggerUseListener.onInteract() fires on right-click
   - TrionManager checks sufficiency
   - Trion consumed and trigger effect applied
   - Cooldown tracked with timestamp map

5. **Match End**
   - Death count reaches limit or time expires
   - ArenaInstance.end() calculates results
   - RankManager.addPlayerRP() updates rankings
   - Players teleported to lobby

## Key Implementation Details

### 1. Coordinate System
Matches use coordinate-based spawning:
```java
Location spawnLoc = new Location(player.getWorld(), spawnIndex * 10, 64, 0);
```

### 2. Backstab Detection
Uses yaw angle comparison:
```java
float victimYaw = victim.getLocation().getYaw();
double attackerAngle = Math.atan2(dz, dx) * 180 / Math.PI;
// Attacker behind if angle diff > 90 degrees
```

### 3. Raytracing for Teleporter
```java
RayTraceResult result = player.getWorld().rayTraceBlocks(
    player.getEyeLocation(),
    player.getLocation().getDirection(),
    15.0
);
```

### 4. Cooldown System
Map-based cooldown tracking:
```java
Map<UUID, Long> cooldowns = new HashMap<>();
boolean isOnCooldown = (currentTime - lastUse) < COOLDOWN_MS;
```

### 5. Match State Transitions
```
WAITING → COUNTDOWN → ACTIVE → ENDING → FINISHED
           (10s)              ↑
                    (time limit or end condition)
```

## Configuration Loading

### Config Structure
```yaml
mysql:
  host: localhost
  port: 3306
  database: brb
  username: root
  password: ""
```

### Default Config Creation
```java
saveDefaultConfig(); // Creates config.yml if missing
String host = getConfig().getString("mysql.host", "localhost");
```

## Error Handling

### Database Operations
Wrapped in try-catch or async error handling:
```java
try {
    player = rankManager.getPlayer(uuid);
} catch (Exception e) {
    getLogger().severe("Failed to load player: " + e.getMessage());
}
```

### Command Validation
```java
if (targetPlayer == null) {
    MessageUtil.sendErrorMessage(player, "Player not found: " + args[1]);
    return true;
}
```

### Permission Checks
```java
if (!sender.hasPermission("brb.admin")) {
    MessageUtil.sendErrorMessage(sender, "No permission");
    return true;
}
```

## Performance Considerations

### 1. Match Ticking
- Only active matches are ticked
- Finished matches removed immediately
- Efficiently iterates over Set copy to avoid concurrent modification

### 2. Trigger Cooldowns
- UUID-based map for O(1) lookup
- Cleanup on player disconnect
- Millisecond-precision timing

### 3. Async Operations
- All database I/O is async
- Player data cached after loading
- Uncached on disconnect

### 4. Collection Usage
- HashMap for O(1) lookups
- HashSet for membership checks
- Immutable copies for iterator safety

## Integration with Other Systems

### Dependencies on Other Modules
- **model.BRBPlayer**: Player data model
- **model.Trigger**: Trigger definition model
- **model.Team**: Team data model
- **database.DatabaseManager**: MySQL operations
- **manager.***: All specialized managers
- **util.MessageUtil**: Standardized messaging

### Expected Interfaces
```java
public class BRBPlayer {
    UUID getUniqueId();
    String getPlayerName();
    String getRankClass();
    int getTotalRP();
    int getRPByWeapon(String type);
}

public class Trigger {
    String getId();
    String getName();
    String getCategory();
    int getCost();
    int getTrionConsumption();
    Material getMaterial();
}

public class Team {
    String getName();
    UUID getLeaderId();
    Set<UUID> getMembers();
}
```

## Future Enhancement Points

1. **Match Rating System**: ELO or similar ranking
2. **Seasonal Rewards**: Rank-based cosmetics
3. **Custom Arenas**: Per-map spawn points
4. **Trigger Balancing**: Economy adjustments
5. **Anti-Cheat**: Damage validation
6. **Replay System**: Match recording
7. **Leaderboards**: Database querying
8. **Statistics**: Win rates, K/D ratios

## Testing Strategy

### Unit Test Areas
1. Trigger cost validation
2. Trion consumption
3. Cooldown calculations
4. Backstab angle detection
5. Rank requirement checks

### Integration Test Areas
1. Player join/leave cycle
2. Match creation and progression
3. Kill recording and RP calculation
4. Command execution and tab completion
5. Database persistence

### Load Test Scenarios
1. Multiple simultaneous matches
2. High player count
3. Database operation throughput
4. Event handler performance
