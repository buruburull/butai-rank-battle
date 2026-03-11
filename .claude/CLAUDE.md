## 言語設定
- すべての応答、説明、確認メッセージは日本語で出力すること
- Yes/Noの選択肢を提示する際も日本語で説明すること

## 必読ファイル
- docs/game-spec.md 仕様書のファイルを必読すること
- docs/TODO.md TODOのファイルを必読すること

## 開発フロー
- 実装完了後、必ずテスト手順（Minecraftで確認すべきコマンドや動作）を日本語で提示すること
- テスト手順は番号付きリストで、具体的なコマンド例を含めること
- テスト完了後、次にやるべきタスクの候補を提示すること
- git push用のコマンドも毎回提示すること（git add -u → git commit → git push origin main）
- 実装中にエラーが出た場合は自動で修正し、修正内容を説明すること

# Border Rank Battle (BRB) - Project Documentation

## Project Overview

Border Rank Battle (BRB) is a competitive Minecraft PvP plugin featuring a trigger-based combat system inspired by the anime "World Trigger". Players equip different triggers (special abilities) to customize their playstyle and compete in ranked matches. The system tracks performance across weapons and maintains season-based rankings.

## Technology Stack

- **Server**: Paper 1.21.11 (Spigot fork)
- **Language**: Java 21
- **Build System**: Gradle 8.5 with Kotlin DSL + Shadow plugin
- **Database**: MySQL 8.0 with HikariCP connection pooling (allowPublicKeyRetrieval=true required)
- **Testing**: JUnit 5, Mockito
- **GitHub**: https://github.com/buruburull/border-rank-battle.git

## Infrastructure (GCP)

- **VM**: GCE e2-medium (2 vCPU, 4GB RAM)
- **Server JVM**: `-Xmx3G` (do NOT reduce to 2G - causes timeout)
- **Project dir**: `~/border-rank-battle`
- **Minecraft server dir**: `~/minecraft-server`
- **Gradle location**: `~/border-rank-battle/gradle-8.5/bin/gradle` (local install, NOT wrapper)
- **Deploy script**: `~/deploy.sh` (pull → build → deploy → restart)

## Build & Deploy

```bash
# Build
./gradle-8.5/bin/gradle :core-plugin:shadowJar

# Deploy JAR
cp core-plugin/build/libs/BorderRankBattle-0.1.0-SNAPSHOT.jar ~/minecraft-server/plugins/BorderRankBattle.jar

# Server restart
screen -S mc -X stuff "stop$(printf '\r')"
sleep 5
cd ~/minecraft-server && screen -S mc -dm bash start.sh

# Or use the deploy script (does all of the above)
~/deploy.sh
```

## Project Structure (Actual)

```
border-rank-battle/
├── .claude/
│   └── CLAUDE.md                    # This file
├── config/
│   └── triggers.yml                 # Trigger definitions, trion config, match config
├── docs/
│   └── schema.sql                   # Full MySQL schema with views and initial data
├── common/                          # Shared module (models, database, utils)
│   ├── build.gradle.kts
│   └── src/main/java/com/borderrank/battle/
│       ├── database/
│       │   ├── DatabaseManager.java     # HikariCP connection pool
│       │   ├── PlayerDAO.java           # Player/WeaponRP CRUD, leaderboard queries
│       │   └── LoadoutDAO.java          # Loadout persistence
│       ├── model/
│       │   ├── BRBPlayer.java           # Player data: UUID, name, rank, trion, weaponRPs
│       │   ├── Team.java                # Team: name, leader, members Set<UUID>
│       │   ├── Loadout.java             # Trigger loadout (8 slots)
│       │   ├── Trigger.java             # Trigger model
│       │   ├── TriggerData.java         # Trigger properties from triggers.yml
│       │   ├── TriggerCategory.java     # Enum: attacker, shooter, sniper, support
│       │   ├── WeaponRP.java            # Per-weapon RP + wins/losses (default 1000)
│       │   ├── WeaponType.java          # Enum: ATTACKER, SHOOTER, SNIPER
│       │   └── RankClass.java           # Enum: S, A, B, C, D, UNRANKED
│       └── util/
│           └── MessageUtil.java         # Chat message formatting with [BRB] prefix
├── core-plugin/                     # Main plugin module
│   ├── build.gradle.kts
│   └── src/main/java/com/borderrank/battle/
│       ├── BRBPlugin.java               # Main entry point, manager initialization
│       ├── arena/
│       │   ├── ArenaInstance.java        # Match instance (solo + team support)
│       │   └── MatchManager.java        # Creates/manages ArenaInstance objects
│       ├── command/
│       │   ├── RankCommand.java         # /rank solo|team|cancel|stats|top
│       │   ├── TeamCommand.java         # /team create|invite|accept|deny|leave|info
│       │   ├── TriggerCommand.java      # /trigger set|view|remove|list
│       │   └── AdminCommand.java        # /bradmin trigger|forcestart|rp|season
│       ├── listener/
│       │   ├── CombatListener.java      # Damage, death, respawn, regen handling
│       │   ├── TriggerUseListener.java  # Trigger activation with trion cost
│       │   └── PlayerConnectionListener.java  # Join/quit handling
│       └── manager/
│           ├── RankManager.java         # RP calculation, rank tiers, team/invite management
│           ├── TrionManager.java        # Trion tick loop, leak, XP bar, bailout
│           ├── QueueManager.java        # Solo/team matchmaking queue
│           ├── LoadoutManager.java      # Player loadout management
│           ├── TriggerRegistry.java     # Loads triggers from triggers.yml
│           ├── MapManager.java          # Arena map management
│           └── ScoreboardManager.java   # Scoreboard display
├── trigger-plugin/                  # Trigger-specific module
│   └── build.gradle.kts
├── build.gradle.kts                 # Root Gradle config
├── settings.gradle.kts              # Multi-module settings
├── .gitignore
└── README.md
```

## Completed Features (Tested & Working)

### 1. Player System
- Player registration on first join (auto-create in DB)
- BRBPlayer model with UUID, name, rankClass, trionCap, trionMax, weaponRPs
- Player data persistence via PlayerDAO (MySQL)
- Player cache in RankManager for performance

### 2. Trigger System
- 15 triggers across 4 categories loaded from config/triggers.yml
  - Attacker: Kogetsu (netherite sword), Scorpion (golden sword, 1.5x backstab), Raygust (iron sword)
  - Shooter: Asteroid (bow), Meteora (crossbow, explosion), Hound (trident, homing), Viper (bow, curve)
  - Sniper: Egret (charged), Lightning (piercing), Ibis (max power)
  - Support: Grasshopper (jump), Shield, Bagworm (sustain toggle), Teleporter, Escudo (barrier), Meteora Sub (TNT), Red Bullet (marking), Star Trigger (free)
- `/trigger set <slot> <trigger>` - Equips trigger with item in inventory
- `/trigger view` - Shows current loadout
- `/trigger remove <slot>` - Removes trigger from slot
- `/trigger list` - Lists all available triggers
- TriggerUseListener handles activation with actual trion costs from TriggerData

### 3. Trion System
- 1000 max trion per match
- HP leak: `(maxHP - currentHP) * 0.5` per second
- Sustain cost: 1.0 per active sustain trigger per second
- XP bar display: level = trion amount, bar = percentage
- Warnings: yellow at 200, red blinking at 100
- Bailout at 0: teleport to hub directly (NO kill), notify match via `match.onKill(null, uuid)`
- `bailedOut` set prevents double-bailout
- Tick loop: every 20 ticks (1 second) via BukkitRunnable

### 4. Combat & Death System
- CombatListener handles damage events with backstab 1.5x multiplier
- **Death handling**: Player dies → presses Minecraft respawn button → PlayerRespawnEvent teleports to world spawn (hub)
- **NO auto-respawn** (`spigot().respawn()` causes frozen state - removed)
- **NO spectator mode** (conflicts with Minecraft native respawn - removed)
- Natural health regen cancelled during matches
- `matchDeaths` set tracks players who died in match for respawn handling
- Friendly fire prevention via `match.isTeammate()` check

### 5. Match System
- Solo queue: `/rank solo` → QueueManager → auto-match when 2 players queued
- Queue checker runs every 100 ticks (5 seconds) in BRBPlugin
- ArenaInstance manages match lifecycle: start → combat → end
- Spawn uses `getHighestBlockAt()` (not hardcoded Y=64)
- Time limits: solo 5min, team 10min
- Trion tick loop started in `start()`, stopped in `end()`
- Double-end prevention in `end()` method

### 6. RP & Ranking System
- Elo-like formula: base=30, coefficient=1.0+(opponentRP-playerRP)/1000, clamped ±5 to ±60
- Per-weapon RP tracking (ATTACKER, SHOOTER, SNIPER)
- Rank tiers: S(5000+), A(3000+), B(1500+), C(<1500)
- `/rank stats [player]` - Decorated stats with rank colors, win rate %, weapon breakdown, team info
- `/rank top` - Global TOP10 with gold/white/gray position colors
- `/rank top attacker|shooter|sniper` - Weapon-specific TOP10
- Tab completion for all subcommands

### 7. Team System
- `/team create <name>` - Create team (B rank+ required)
- `/team invite <player>` - Send invitation (leader only)
- `/team accept` - Accept pending invitation
- `/team deny` - Deny invitation
- `/team leave` - Leave team
- `/team info [team]` - Team details with member list
- RankManager tracks: teams map, playerTeams map, pendingInvites map
- Team model: name, leaderId, members Set<UUID>

### 8. Team Match (Code Ready, Needs 4-Player Test)
- `/rank team` - Team leader queues team for ranked match
- ArenaInstance has team constructor with `Map<Integer, Set<UUID>> teamData`
- `isTeammate()` method for friendly fire check
- Team-based win condition: all members of a team dead → other team wins
- `endTeamMatch()` and `endSoloMatch()` separate logic

### 9. Admin Commands
- `/bradmin trigger reload` - Reload triggers.yml
- `/bradmin forcestart` - Force start match
- `/bradmin rp set <player> <weapon> <value>` - Set player RP
- `/bradmin season start|end` - Season management (STUB - not yet implemented)

## Pending / TODO Features

### Priority 1: Season System (Next Task)
DB schema already exists (seasons, season_snapshots tables). AdminCommand has stub. Need to implement:
- `RankManager.startSeason(name)`: Insert new season row, set is_active=true
- `RankManager.endSeason()`: Snapshot current RP to season_snapshots, set end_date, reset all player RP to 1000
- `/bradmin season start <name>` and `/bradmin season end` already wired in AdminCommand
- `/rank stats` should show current season info
- Consider: season history view command

### Priority 2: Trigger Balance & Mechanics
- Implement actual trigger effects (currently only trion cost works, not the special effects)
- Hound homing, Viper curve shots, Egret/Ibis charge time
- Meteora explosion on impact
- Raygust shield mode toggle
- Bagworm damage reduction (currently just sustain cost)
- Red Bullet glowing effect on hit

### Priority 3: UI Improvements
- Scoreboard during match (timer, trion, kills)
- Tab list with rank colors
- Chat prefix with rank tier
- Boss bar for match timer
- Kill feed messages

### Priority 4: Additional Features
- Match history persistence to DB (match_history, match_results tables exist but not populated)
- Loadout save/load from DB (LoadoutDAO exists)
- Multiple arenas/maps support (MapManager exists as stub)
- Spectator mode for non-participants watching matches
- Practice/unranked mode

## Known Issues & Gotchas

### Critical
- **Never use auto-respawn** (`player.spigot().respawn()`): Causes frozen state where player appears stuck to others
- **Never use spectator mode** for match participants: Conflicts with Minecraft respawn flow
- **Trion bailout must NOT kill player**: Must teleport directly to hub and call `match.onKill(null, uuid)` to end match
- **Server memory**: e2-medium needs -Xmx3G. Reducing to 2G causes connection timeouts

### Git
- `.gitignore` excludes: `build/`, `*/build/`, `*.jar`, `*.class`, `.gradle/`, `gradle-8.5/`, `gradle.zip`, `.idea/`, `*.iml`
- Previous issue: gradle-8.5/ and gradle.zip were committed and caused push failure. Fixed with `git reset --soft` and clean recommit
- GCP server has credential cache (`git config credential.helper 'cache --timeout=86400'`)

### Database
- JDBC URL requires `allowPublicKeyRetrieval=true` (was added to DatabaseManager)
- WeaponType enum in DB uses item names (NETHERITE_SWORD etc.), but Java enum uses ATTACKER/SHOOTER/SNIPER - there's a mismatch that may need attention

### Deployment
- Always kill existing screen sessions before restart: `pkill -9 -f paper.jar; screen -wipe` if port conflicts
- If session.lock error: `rm -f ~/minecraft-server/world/session.lock`
- External IP changes on VM restart - update Minecraft client server address

## Coding Conventions

- **Package**: `com.borderrank.battle`
- **Naming**: camelCase methods/variables, PascalCase classes
- **Indentation**: 4 spaces
- **Line Length**: 120 chars max
- **Messages**: Japanese for player-facing, English for code/logs
- **Trion**: Always through TrionManager, never direct deductions
- **DB**: Prepared statements only, via PlayerDAO/LoadoutDAO
- **Events**: @EventHandler with appropriate priority, check for cancelled events
- **Null safety**: Always null-check player data lookups

## Database Schema Summary

Tables: players, weapon_rp, trigger_master, player_loadouts, teams, team_members, seasons, match_history, match_results, season_snapshots
Views: player_overall_ranking, recent_matches, weapon_popularity
See `docs/schema.sql` for full schema.

---

**Last Updated**: 2026-03-05
**Main Branch**: main
**GitHub**: https://github.com/buruburull/border-rank-battle.git
