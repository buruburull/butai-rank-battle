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

BUTAI Rank Battle (BRB) is a competitive Minecraft PvP plugin featuring a frame-based combat system inspired by the anime "World Trigger". Players equip different frames (special abilities) to customize their playstyle and compete in ranked matches. The system tracks performance across weapons and maintains season-based rankings.

## Technology Stack

- **Server**: Paper 1.21+ (Spigot fork)
- **Language**: Java 21
- **Build System**: Gradle 8.5 with Kotlin DSL + Shadow plugin
- **Database**: MySQL 8.0 with HikariCP connection pooling (allowPublicKeyRetrieval=true required)
- **Testing**: JUnit 5, Mockito
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
Pterodactylパネルが Docker コンテナ内でサーバーを実行するため、`~/minecraft-server/` ディレクトリは実際のサーバーではない。プラグインJARやデータフォルダは上記Pterodactyl volumes内に配置する。

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

## Configuration Files

- **config.yml**: データベース接続設定のみ（host, port, name, username, password）
- **frames.yml**: ゲーム関連の全設定
  - フレーム定義（16種: striker, gunner, marksman, support）
  - ロビー設定（spawn location）
  - NPC設定（Villager NPCs with PDC tags）
  - ホログラム設定（TextDisplay: welcome banner + ranking TOP10）
  - アクションバー設定（ランク・RP・ステータス表示）

### 注意: saveDefaultConfig() / saveResource()
`saveDefaultConfig()` と `saveResource(name, false)` は既存ファイルを上書きしない。新しい設定項目を追加した場合、サーバー上の古いファイルを削除してから再起動する必要がある。

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
- **Ether bailout must NOT kill player**: Must teleport directly to hub and call `match.onKill(null, uuid)` to end match
- **Server memory**: e2-medium needs -Xmx3G. Reducing to 2G causes connection timeouts

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

Tables: players, weapon_rp, trigger_master, player_loadouts, teams, team_members, seasons, match_history, match_results, season_snapshots
Views: player_overall_ranking, recent_matches, weapon_popularity
See `docs/schema.sql` for full schema.

---

**Last Updated**: 2026-03-13
**Main Branch**: main
**GitHub**: https://github.com/buruburull/border-rank-battle.git
