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

# BUTAI Rank Battle (BRB) - Project Documentation

## Project Overview

BUTAI Rank Battle (BRB) は「ワールドトリガー」にインスパイアされたフレームベースの競技型Minecraft PvPプラグインです。プレイヤーは「フレーム」（特殊兵装）を装備し、「エーテル」（戦闘エネルギー）を管理しながらランクマッチで対戦します。

### 用語（旧名称→新名称）
- トリガー → **フレーム (Frame)**: 装備する特殊兵装
- トリオン → **エーテル (Ether)**: 戦闘用エネルギー
- ベイルアウト → **エマージェンシーシフト (E-Shift)**: エーテル枯渇時の緊急離脱
- ロードアウト → **フレームセット (FrameSet)**: フレーム装備構成（8スロット）

## Technology Stack

- **Server**: Paper 1.21+ (Spigot fork)
- **Language**: Java 21
- **Build System**: Gradle 8.5 with Kotlin DSL + Shadow plugin
- **Database**: MySQL 8.0 with HikariCP connection pooling (allowPublicKeyRetrieval=true required)
- **GitHub**: https://github.com/buruburull/border-rank-battle.git

## Infrastructure (GCP)

- **VM**: GCE e2-medium (2 vCPU, 4GB RAM)
- **Server JVM**: `-Xmx3G` (do NOT reduce to 2G - causes timeout)
- **Server management**: Pterodactylパネル（Docker経由）
- **Project dir (GCP)**: `~/butai-rank-battle`
- **Gradle location (GCP)**: `~/butai-rank-battle/gradle-8.5/bin/gradle` (local install, NOT wrapper)
- **Pterodactyl volumes**: `/var/lib/pterodactyl/volumes/c1691fe5-4896-4dde-a0b0-a4e7d492f358/`
- **Plugin dir (実際)**: `/var/lib/pterodactyl/volumes/c1691fe5-4896-4dde-a0b0-a4e7d492f358/plugins/`
- **Plugin JAR名**: `BUTAIRankBattle.jar`
- **Plugin data folder**: `plugins/BUTAIRankBattle/`

### 重要: ~/minecraft-server/ は使用しない
Pterodactylパネルが Docker コンテナ内でサーバーを実行するため、`~/minecraft-server/` ディレクトリは実際のサーバーではない。

## Build & Deploy

```bash
# 1. ローカルでpush
cd ~/Desktop/border-rank-battle
git add -u && git commit -m "説明" && git push origin main

# 2. GCPサーバーでpull & ビルド
cd ~/butai-rank-battle
git pull origin main
~/butai-rank-battle/gradle-8.5/bin/gradle :core-plugin:shadowJar

# 3. JARをPterodactylのプラグインディレクトリにコピー
sudo cp ~/butai-rank-battle/core-plugin/build/libs/BUTAIRankBattle-0.1.0-SNAPSHOT.jar /var/lib/pterodactyl/volumes/c1691fe5-4896-4dde-a0b0-a4e7d492f358/plugins/BUTAIRankBattle.jar

# 4. Pterodactylパネルからサーバー再起動
# ※ screen は使用しない（Pterodactylが管理）

# frames.yml を再生成したい場合（設定リセット）:
sudo rm /var/lib/pterodactyl/volumes/c1691fe5-4896-4dde-a0b0-a4e7d492f358/plugins/BUTAIRankBattle/frames.yml
```

## Project Structure

```
butai-rank-battle/
├── common/src/main/java/com/butai/rankbattle/
│   ├── database/          # DAO classes
│   │   ├── DatabaseManager.java   # HikariCP connection pool
│   │   ├── PlayerDAO.java         # Player CRUD
│   │   ├── FrameSetDAO.java       # FrameSet persistence
│   │   ├── TeamDAO.java           # Team persistence
│   │   └── SeasonDAO.java         # Season/snapshot management
│   ├── model/             # Data models
│   │   ├── BRBPlayer.java         # Player data (UUID, rank, RP)
│   │   ├── FrameData.java         # Frame definition (damage, cost, etc.)
│   │   ├── FrameCategory.java     # STRIKER/GUNNER/MARKSMAN/SUPPORT
│   │   ├── WeaponRP.java          # Per-weapon RP stats
│   │   ├── WeaponType.java        # STRIKER/GUNNER/MARKSMAN
│   │   ├── RankClass.java         # S/A/B/C/UNRANKED
│   │   ├── Team.java              # Team data
│   │   └── ArenaMap.java          # Arena map definition
│   └── util/
│       └── MessageUtil.java       # Japanese message utility
│
├── core-plugin/src/main/java/com/butai/rankbattle/
│   ├── BRBPlugin.java             # Main plugin class
│   ├── command/
│   │   ├── FrameCommand.java      # /frame (set/view/remove/list/preset)
│   │   ├── RankCommand.java       # /rank (solo/team/practice/cancel/stats/top/history/spectate)
│   │   ├── TeamCommand.java       # /team (create/invite/accept/deny/leave/info)
│   │   └── AdminCommand.java      # /bradmin (frame/forcestart/rp/season/map)
│   ├── listener/
│   │   ├── CombatListener.java    # Damage, backstab, frame effects, ether cost
│   │   ├── PlayerConnectionListener.java  # Join/quit, data load/save
│   │   ├── LobbyListener.java     # NPC interaction
│   │   ├── ChatTabListener.java   # Rank prefix in chat/tab
│   │   └── BlockChangeListener.java # Block tracking for map restoration
│   ├── manager/
│   │   ├── RankManager.java       # Player cache, rank/RP calculation, team management
│   │   ├── QueueManager.java      # Solo/team/practice queues, matchmaking
│   │   ├── FrameRegistry.java     # Frame definitions from frames.yml, arena maps
│   │   ├── FrameSetManager.java   # Player frameset (8 slots), presets
│   │   ├── EtherManager.java      # Ether (1000 max), leak, E-Shift
│   │   ├── LobbyManager.java      # NPCs, holograms, action bar
│   │   └── DisconnectTracker.java # Disconnect penalty tracking
│   └── arena/
│       └── ArenaInstance.java     # Match lifecycle, judge, sudden death, block restore
│
├── core-plugin/src/main/resources/
│   ├── plugin.yml          # Plugin registration
│   ├── config.yml          # DB connection only
│   └── frames.yml          # All game settings (frames, lobby, NPC, hologram, maps)
│
└── docs/
    ├── game-spec.md        # Game specification
    ├── TODO.md             # Implementation progress
    └── schema.sql          # Database schema
```

## Configuration Files

- **config.yml**: データベース接続設定のみ（host, port, name, username, password）
- **frames.yml**: ゲーム関連の全設定
  - フレーム定義（16種: striker 3, gunner 4, marksman 3, support 6）
  - ロビー設定（spawn location）
  - NPC設定（Villager NPCs with PDC tags: 5体）
  - ホログラム設定（TextDisplay: welcome banner + ranking TOP10, 60秒更新）
  - アクションバー設定（ランク・RP・ステータス表示, 2秒更新）
  - アリーナマップ設定（スポーン座標、境界半径）

### 注意: saveDefaultConfig() / saveResource()
`saveDefaultConfig()` と `saveResource(name, false)` は既存ファイルを上書きしない。新しい設定項目を追加した場合、サーバー上の古いファイルを削除してから再起動する必要がある。

## Command System

### Player Commands
| コマンド | 説明 |
|---|---|
| `/frame set <slot> <name>` | フレーム装備 |
| `/frame view` | フレームセット確認 |
| `/frame remove <slot>` | フレーム解除 |
| `/frame list [category]` | フレーム一覧 |
| `/frame preset save/load/list/delete <name>` | プリセット管理 |
| `/rank solo` | ソロランクマッチにキュー |
| `/rank team` | チームランクマッチにキュー |
| `/rank practice` | プラクティスにキュー |
| `/rank cancel` | キューキャンセル |
| `/rank stats [player]` | 戦績表示 |
| `/rank top [weapon]` | ランキングTOP10 |
| `/rank history [player]` | マッチ履歴（直近10件） |
| `/rank spectate [matchId]` | 試合観戦 |
| `/rank spectate leave` | 観戦終了 |
| `/team create <name>` | チーム作成（Bランク以上） |
| `/team invite <player>` | メンバー招待 |
| `/team accept / deny` | 招待応答 |
| `/team leave` | チーム脱退 |
| `/team info [name]` | チーム情報 |

### Admin Commands (permission: brb.admin)
| コマンド | 説明 |
|---|---|
| `/bradmin frame reload` | フレーム設定リロード |
| `/bradmin forcestart` | 強制マッチ開始 |
| `/bradmin rp set <player> <weapon> <value>` | RP手動設定 |
| `/bradmin rp info <player>` | RP情報表示 |
| `/bradmin season start <name>` | シーズン開始 |
| `/bradmin season end` | シーズン終了 |
| `/bradmin map list` | マップ一覧 |
| `/bradmin map info <name>` | マップ詳細 |

## Package & Naming

- **Package**: `com.butai.rankbattle`
- **Plugin name** (plugin.yml): `BUTAIRankBattle`
- **Naming**: camelCase methods/variables, PascalCase classes
- **Indentation**: 4 spaces
- **Line Length**: 120 chars max
- **Messages**: Japanese for player-facing, English for code/logs

## Known Issues & Gotchas

### Critical
- **Never use auto-respawn** (`player.spigot().respawn()`): Causes frozen state where player appears stuck to others
- **Never use spectator mode** for match participants: Conflicts with Minecraft respawn flow
- **E-Shift must NOT kill player**: Must teleport directly to lobby (not death). Call `match.onPlayerEliminated(uuid)` for match processing
- **Server memory**: e2-medium needs -Xmx3G. Reducing to 2G causes connection timeouts
- **エーテル操作は必ずEtherManager経由**: 直接減算禁止
- **ロビー座標はEtherManager.getLobbyLocation()経由で取得**: `plugin.getConfig()`はconfig.yml（DB設定のみ）を返すため、frames.ymlのlobby座標は取得できない
- **NPC VillagerのCustomNameはnull**: 名前表示はTextDisplayで行う。CustomNameを設定するとホログラムと重複表示される

### Deployment
- サーバーはPterodactylパネルから再起動する（screenは使用しない）
- `~/minecraft-server/` は実際のサーバーディレクトリではない
- config.yml にはDB設定のみ、ゲーム設定は frames.yml に配置
- External IP changes on VM restart - update Minecraft client server address

### Database
- JDBC URL requires `allowPublicKeyRetrieval=true`
- サーバー上のDB接続: host=172.17.0.1 (Docker bridge), username=brb, password=brb_password

### Git
- `.gitignore` excludes: `build/`, `*/build/`, `*.jar`, `*.class`, `.gradle/`, `gradle-8.5/`, `gradle.zip`, `.idea/`, `*.iml`
- GCP server has credential cache (`git config credential.helper 'cache --timeout=86400'`)

## Database Schema Summary

Tables: players, weapon_rp, frame_master, player_framesets, teams, team_members, seasons, match_history, match_results, season_snapshots
Views: player_overall_ranking, recent_matches, weapon_popularity
See `docs/schema.sql` for full schema.

## Key Systems Summary

- **フレーム**: 16種（STRIKER 3, GUNNER 4, MARKSMAN 3, SUPPORT 6）。frames.ymlで定義
- **エーテル**: 最大1000。XPバー表示。HPダメージに連動してリーク（(maxHP-currentHP)*0.5/秒）
- **E-Shift**: エーテル0で自動発動。ロビーテレポート（キルではない）
- **ランク**: S(15000+), A(10000+), B(5000+), C(<5000), UNRANKED
- **RP計算**: 非対称Elo方式（base 30, clamp 5-120, 敗者は勝者の70%, 参加ボーナス+5）
- **マッチ**: ソロ5分/チーム10分。ジャッジスコア=与ダメ×0.5+エーテル残×0.3+HP残×0.2
- **サドンデス**: スコア差1%以内で突入。エーテルリーク3倍。追加60秒
- **ブロック復元**: 試合中のブロック変更を記録し、試合終了時に自動復元

---

**Last Updated**: 2026-03-13
**Main Branch**: main
**GitHub**: https://github.com/buruburull/border-rank-battle.git
