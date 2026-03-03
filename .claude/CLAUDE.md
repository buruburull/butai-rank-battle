# Border Rank Battle - Project Documentation

## Project Overview

Border Rank Battle (BRB) is a competitive Minecraft PvP plugin featuring a trigger-based combat system inspired by the anime "World Trigger". Players equip different triggers (special abilities) to customize their playstyle and compete in ranked matches. The system tracks performance across weapons and maintains season-based rankings.

## Technology Stack

### Core Platform
- **Server**: Paper 1.21.11 (Spigot fork with performance optimizations)
- **Language**: Java 21 (latest LTS with modern features)
- **Build System**: Gradle with Kotlin DSL (kotlin-dsl plugin)

### Dependencies
- **Database**: MySQL 8.0 (relational data storage)
- **Connection Pooling**: HikariCP (high-performance connection management)
- **JSON Processing**: GSON or Jackson (for configuration and data serialization)
- **Logging**: SLF4J with Logback (structured logging)
- **Testing**: JUnit 5 (Jupiter) and Mockito

### Development Tools
- **IDE**: IntelliJ IDEA (recommended)
- **Version Control**: Git with GitHub
- **CI/CD**: GitHub Actions (for automated testing and releases)

## Project Structure

```
brb-project/
├── config/
│   └── triggers.yml              # Trigger definitions and balance config
├── docs/
│   └── schema.sql                # Database schema
├── .gradle/                       # Gradle cache (gitignored)
├── build/                         # Compiled output (gitignored)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/github/brb/
│   │   │       ├── BRBPlugin.java             # Main plugin class
│   │   │       ├── config/
│   │   │       │   ├── ConfigManager.java
│   │   │       │   └── TriggerRegistry.java
│   │   │       ├── database/
│   │   │       │   ├── DatabaseManager.java
│   │   │       │   ├── PlayerRepository.java
│   │   │       │   └── MatchRepository.java
│   │   │       ├── game/
│   │   │       │   ├── Match.java
│   │   │       │   ├── BRBPlayer.java
│   │   │       │   └── Team.java
│   │   │       ├── triggers/
│   │   │       │   ├── Trigger.java            # Base trigger interface
│   │   │       │   ├── AttackerTrigger.java
│   │   │       │   ├── ShooterTrigger.java
│   │   │       │   ├── SniperTrigger.java
│   │   │       │   └── SupportTrigger.java
│   │   │       ├── trion/
│   │   │       │   ├── TrionManager.java       # Trion resource management
│   │   │       │   ├── TrionLeakSystem.java
│   │   │       │   └── TrionBar.java           # XP bar visualization
│   │   │       ├── events/
│   │   │       │   ├── MatchStartEvent.java
│   │   │       │   └── TriggerUseEvent.java
│   │   │       ├── listeners/
│   │   │       │   ├── PlayerListener.java
│   │   │       │   ├── CombatListener.java
│   │   │       │   └── MatchListener.java
│   │   │       ├── commands/
│   │   │       │   ├── BRBCommand.java
│   │   │       │   ├── MatchCommand.java
│   │   │       │   └── TriggerCommand.java
│   │   │       └── util/
│   │   │           ├── DatabaseUtil.java
│   │   │           └── ItemUtils.java
│   │   └── resources/
│   │       └── plugin.yml         # Plugin metadata
│   └── test/
│       └── java/io/github/brb/
│           ├── TrionManagerTest.java
│           └── TriggerRegistryTest.java
├── gradle/
│   └── wrapper/
├── build.gradle.kts               # Gradle configuration
├── settings.gradle.kts            # Gradle settings
├── .gitignore                      # Git ignore rules
├── .claude/
│   └── CLAUDE.md                  # This file (renamed from dotclaude/)
└── README.md                       # Public documentation
```

## Coding Conventions

### Java Style
- **Naming**: camelCase for variables/methods, PascalCase for classes
- **Line Length**: Maximum 120 characters
- **Indentation**: 4 spaces (not tabs)
- **Braces**: Allman style (opening brace on new line for classes/methods)
- **Access Modifiers**: Always explicit (no default package-private without reason)
- **Final Classes**: Classes not meant for inheritance should be final

### Code Organization
- One public class per file (exceptions for nested inner classes)
- Organize imports: java/javax > other packages > project packages
- Use consistent logging: SLF4J Logger named `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);`
- Avoid raw types: use generics consistently

### Documentation
- Javadoc for all public classes and methods
- Explain the "why" not just the "what"
- Document parameters, return values, and exceptions
- Use `@deprecated` with migration guidance when applicable

### Database Access
- Use prepared statements exclusively (prevent SQL injection)
- Connection pooling through HikariCP
- Database operations in separate Repository classes
- All queries should have timeouts

### Event Handling
- Implement Bukkit's Listener interface
- Use @EventHandler annotation with appropriate priority
- Check isAsynchronous() state when accessing Bukkit API
- Unregister listeners properly in plugin disable

### Trigger System
- All triggers extend the base Trigger interface
- Implement trigger-specific logic in category classes (AttackerTrigger, etc.)
- Use Trion through TrionManager - never direct deductions
- Triggers emit TriggerUseEvent for logging/analytics

### Trion Management
- TrionManager handles all trion operations
- TrionLeakSystem calculates passive loss over time
- TrionBar displays current trion using Minecraft XP bar (0-100 scale mapped to actual trion)
- Prevent trion from going negative - use minimum of 0

## Important Files

### Configuration Files
- **config/triggers.yml**: Define trigger properties, costs, and mechanics. Changes require server reload.
- **plugin.yml**: Plugin metadata including version, description, commands, and permissions.

### Core Classes

#### BRBPlugin.java
Main entry point. Handles initialization, database setup, config loading, and plugin lifecycle.

#### DatabaseManager.java
Singleton managing all database connections through HikariCP. Handles connection pool configuration and queries.

#### TrionManager.java
Manages trion resources for all players. Key responsibilities:
- Deduct trion on trigger use
- Apply trion leaks (passive loss based on leak_coefficient)
- Display trion using XP bar (0-100 scale)
- Prevent negative trion
- Send low/critical trion warnings

#### TriggerRegistry.java
Loads triggers from triggers.yml and manages trigger instances. Provides lookup by name and category.

#### Match.java
Represents an active or completed match. Tracks players, scoring, time, and match state transitions.

#### BRBPlayer.java
Wrapper around Bukkit Player with BRB-specific data: current triggers, trion amount, rating points (RP), match history.

## Database

All player data, match results, and rankings are persisted in MySQL 8.0. Schema includes:
- players: Core player data and account information
- weapon_rp: Rating points per weapon type
- trigger_master: Trigger definitions and properties
- player_loadouts: Saved trigger combinations
- match_history: Completed match metadata
- match_results: Per-player results from matches
- teams: Team information and member lists
- seasons: Ranking seasons and periods

See `docs/schema.sql` for complete schema with indexes.

## Build & Deployment

### Building
```bash
./gradlew build
```
Creates `build/libs/brb-{version}-all.jar` ready for deployment.

### Testing
```bash
./gradlew test
```
Runs all JUnit tests.

### Deployment
1. Backup existing plugin
2. Place JAR in server's plugins/ directory
3. Start/restart server
4. Verify plugin loaded: `/plugins` command should show BRB plugin
5. Configure triggers via `config/triggers.yml`
6. Set up database credentials in configuration file

### Development Workflow
1. Create feature branch from main
2. Write tests first (TDD)
3. Implement feature
4. Run `./gradlew build test` before committing
5. Create pull request with comprehensive description
6. Obtain code review approval
7. Merge to main

## Performance Considerations

- Database queries run asynchronously to prevent main thread blocking
- Use BukkitRunnable for scheduled tasks
- Trion leak calculations optimized to run once per minute, not per player tick
- Match tracking uses in-memory data structures with periodic persistence
- Connection pooling ensures minimal database overhead

## Common Development Tasks

### Adding a New Trigger
1. Create trigger class extending appropriate base (e.g., SupportTrigger)
2. Implement required methods (activate, deactivate, getName, etc.)
3. Add trigger definition to config/triggers.yml
4. Register trigger in TriggerRegistry.java
5. Implement activation logic with TrionManager deductions
6. Test trigger behavior and trion costs

### Modifying Trion System
- All trion deductions go through TrionManager.deductTrion()
- Update leak_coefficient in config/triggers.yml
- Remember that XP bar shows 0-100 scale (multiply actual trion by 100/1000)

### Adding Match Statistics
- Add columns to match_results table
- Update MatchRepository.java to persist new stats
- Modify Match.java to track stat during game
- Update leaderboard queries to include new stat

## Debugging

### Enable Debug Logging
Set log level to DEBUG in logback.xml configuration.

### Common Issues
- **Plugin Not Loading**: Check plugin.yml syntax and java.lang.ClassNotFoundException in logs
- **Database Connection Failed**: Verify MySQL is running, credentials correct, firewall allows connections
- **Triggers Not Appearing**: Check config/triggers.yml YAML syntax, verify TriggerRegistry loaded config
- **Trion Not Displaying**: Check TrionBar.updateDisplay() called, verify player has XP to display

## Contributors

This project uses the convention:
```
Co-Authored-By: [Name] <[email]>
```
in commit messages to credit all contributors on a commit.

## License

Specify your license here (e.g., MIT, GPL, etc.)

---

**Last Updated**: 2026-03-03
**Main Branch**: main
**Development Setup**: IntelliJ IDEA with Paper plugin SDK configured
