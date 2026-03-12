# BUTAI RANK BATTLE - Phase 1 実装TODO

> **ステータス**: [ ] 未着手 / [~] 進行中 / [x] 完了

---

## Phase 0: 準備（完了）
- [x] ゲーム仕様書作成（docs/game-spec.md）
- [x] 旧コードをlegacy/v1ブランチに退避
- [x] v0-legacyタグ付与
- [x] TODO.md作成（docs/TODO.md）
- [x] GitHubリポジトリ名変更（border-rank-battle → butai-rank-battle）
- [x] ローカルのリモートURL更新

---

## Phase 1: コアシステム

### 1. プロジェクト初期構築（完了）
- [x] mainブランチの旧コードをクリーン化（docs/のみ残す）
- [x] Gradle 8.5 + Kotlin DSL セットアップ（multi-module: common, core-plugin）
- [x] Paper 1.21+ 依存関係設定
- [x] Shadow Plugin 設定
- [x] plugin.yml 作成（BUTAIRankBattle）
- [x] パッケージ構造作成（com.butai.rankbattle）
- [x] .gitignore 更新
- [x] メインクラス BRBPlugin.java 作成（onEnable/onDisable）

### 2. DB設計・スキーマ作成（完了）
- [x] docs/schema.sql 新規作成（新名称対応）
- [x] players テーブル
- [x] weapon_rp テーブル（STRIKER/GUNNER/MARKSMAN）
- [x] frame_master テーブル
- [x] player_framesets テーブル
- [x] teams / team_members テーブル
- [x] seasons テーブル
- [x] match_history / match_results テーブル
- [x] season_snapshots テーブル
- [x] ビュー: player_overall_ranking, recent_matches, weapon_popularity
- [x] DatabaseManager.java（HikariCP接続プール）
- [x] PlayerDAO.java
- [x] FrameSetDAO.java

### 3. オペレーター登録システム（完了）
- [x] BRBPlayer モデル（UUID, name, rankClass, etherCap, weaponRPs）
- [x] WeaponRP モデル（weaponType, rp, wins, losses）
- [x] WeaponType enum（STRIKER, GUNNER, MARKSMAN）
- [x] RankClass enum（S, A, B, C, UNRANKED）
- [x] PlayerConnectionListener（初回参加時の自動DB登録）
- [x] RankManager（プレイヤーキャッシュ、ランク判定）

### 4. フレームシステム（完了）
- [x] Frame モデル
- [x] FrameData モデル（プロパティ: ダメージ、コスト、特殊効果）
- [x] FrameCategory enum（STRIKER, GUNNER, MARKSMAN, SUPPORT）
- [x] config/frames.yml 作成（18フレーム定義）
  - [x] STRIKER: Crescent, Fang, Bastion
  - [x] GUNNER: Pulse, Nova, Seeker, Frost
  - [x] MARKSMAN: Falcon, Volt, Zenith
  - [x] SUPPORT: Leap, Barrier, Cloak, Warp, Rampart, Blast, Tracer, Core Frame
- [x] FrameRegistry.java（frames.yml読み込み）
- [x] /frame list コマンド

### 5. フレームセット（装備構成）（完了）
- [x] FrameSetManager.java（メモリキャッシュ、バリデーション、プリセット管理）
- [x] 装備制約バリデーション
  - [x] 重複禁止チェック
  - [x] スロット1必須（武器系のみ）チェック
  - [x] 武器タイプ決定ロジック（スロット1のカテゴリ）
- [x] /frame set <slot> <name> コマンド
- [x] /frame view コマンド
- [x] /frame remove <slot> コマンド
- [x] /frame preset save/load/list/delete コマンド
- [x] FrameSetDAO.java（DB永続化）※Phase1-2で作成済み
- [x] キュー参加時のバリデーション（validateForQueue）
- [x] ログイン時DB読み込み・ログアウト時DB保存

### 6. エーテルシステム（完了）
- [x] EtherManager.java
- [x] エーテル最大値: 1000
- [x] XPバー表示（レベル数値=残量、バー=割合）
- [x] エーテルリーク: (maxHP - currentHP) * 0.5 /秒
- [x] 使用時消費（Use）処理
- [x] 持続消費（Sustain）処理
- [x] 警告: 200以下で黄色、100以下で赤点滅
- [x] E-Shift: エーテル0でロビーへテレポート（キルではない）
- [x] 二重発動防止フラグ
- [x] Tickループ（20tick=1秒ごと）

### 7. 戦闘システム（完了）
- [x] CombatListener.java
- [x] 背面攻撃判定（角度定義: 120度）
- [x] 背面攻撃 1.5倍ダメージ
- [x] フレーム別ダメージ補正
  - [x] Crescent: 1.3倍（frames.ymlのdamageMultiplierで適用）
  - [x] Fang: 背面時さらに1.5倍（合計2.25倍）
  - [ ] Bastion: シールドモード切替（Phase2で実装予定）
- [x] GUNNER/MARKSMANエーテルコスト消費（射撃時）
- [x] Frost: Slowness II 3秒
- [x] 自然回復無効（試合中）
- [x] フレンドリーファイア防止（チーム戦）
- [x] Cloak: 被ダメ60%カット

### 8. ソロランクマッチ（完了）
- [x] QueueManager.java（ソロキュー管理）
- [x] ArenaInstance.java（マッチインスタンス管理）
- [x] マッチ状態遷移: WAITING → COUNTDOWN → ACTIVE → ENDING → FINISHED
- [x] /rank solo コマンド（キュー参加）
- [x] /rank cancel コマンド（キューキャンセル）
- [x] /rank practice コマンド（プラクティスマッチ）
- [x] カウントダウン（10秒）
- [x] スポーン処理（getHighestBlockAt使用）
- [x] 制限時間: ソロ5分 / チーム10分
- [x] 勝利条件: 相手を倒す or 時間切れ→ジャッジ
- [x] 試合終了→ロビーへ帰還
- [x] 二重終了防止
- [x] 切断時の即E-Shift処理
- [x] ダメージトラッキング（ジャッジスコア用）

### 9. ジャッジシステム・サドンデス（完了）
- [x] ジャッジスコア計算: 与ダメ×0.5 + エーテル残量×0.3 + HP残量×0.2
- [x] 制限時間切れ時のスコア判定
- [x] サドンデス突入判定（スコア差1%以内）
- [x] サドンデス: エーテルリーク3倍加速（0.5→1.5）
- [x] サドンデス: 追加60秒制限
- [x] サドンデス: 引き分け処理（参加ボーナスのみ）
- [x] サドンデス演出（ボスバー赤化、警告メッセージ、ウィザースポーンSE）
- [x] ボスバー（試合タイマー表示、緑→黄→赤の色変化、10分割ノッチ）

### 10. RP・ランクシステム（完了）
- [x] 非対称Elo計算式実装
  - [x] 勝者: base(30) × coefficient, clamp(5, 120)
  - [x] 敗者: winnerGain × 0.7, min 5
  - [x] 参加ボーナス: +5
- [x] 武器タイプ別RP追跡
- [x] ランク判定: S(15000+), A(10000+), B(5000+), C(<5000)
- [x] /rank stats [player] コマンド
- [x] /rank top [weapon] コマンド（TOP10）
- [x] DB保存（weapon_rp更新）

### 11. チームシステム（完了）
- [x] Team モデル（name, leaderId, members）
- [x] /team create <name> コマンド（B rank+必須）
- [x] /team invite <player> コマンド
- [x] /team accept / deny コマンド
- [x] /team leave コマンド
- [x] /team info [name] コマンド
- [x] pendingInvites管理
- [x] DB永続化（teams, team_members）

### 12. チームランクマッチ
- [ ] チームキュー（/rank team）
- [ ] ArenaInstance チーム戦コンストラクタ
- [ ] isTeammate() によるフレンドリーファイア防止
- [ ] チーム勝利条件: 相手チーム全員脱落
- [ ] チーム戦RP計算（非対称Elo + キルボーナス + 生存ボーナス）
- [ ] チーム戦ジャッジ（チーム合算スコア）

### 13. 切断処理
- [ ] PlayerQuitEvent検知 → 即E-Shift（脱落）
- [ ] ソロ戦: 相手勝利処理
- [ ] チーム戦: 脱落扱い、残りメンバーで続行
- [ ] 切断カウント追跡（メモリ内、直近1時間）
- [ ] 切断ペナルティ（2回:警告、3回:5分禁止、4回+:15分禁止）
- [ ] ペナルティ中のキュー拒否メッセージ
- [ ] プラクティス除外

### 14. ロビーシステム
- [ ] ロビースポーン座標設定（config.yml）
- [ ] ログイン時ロビーテレポート
- [ ] ゲームモード: ADVENTURE
- [ ] NPC配置（ランクマッチ、プラクティス、チームマッチ、フレームセット、戦績）
  - [ ] AI無効、無敵、サイレント
  - [ ] PersistentDataContainerタグ管理
  - [ ] 右クリック→コマンド実行
- [ ] ホログラム（TextDisplay）
  - [ ] ウェルカムバナー
  - [ ] ランキングTOP10（30秒更新）
- [ ] アクションバー（ランク、RP、ステータス表示）

### 15. 観戦システム
- [ ] /rank spectate [matchId] コマンド
- [ ] /rank spectate leave コマンド
- [ ] 観戦者セット管理（参加者と独立）
- [ ] キュー中・試合中プレイヤーの観戦拒否

### 16. プラクティスバトル
- [ ] /rank practice コマンド
- [ ] プラクティスキュー（ソロキューと別管理）
- [ ] RP変動なし
- [ ] 切断ペナルティ対象外

### 17. シーズンシステム
- [ ] /bradmin season start <name> コマンド
- [ ] /bradmin season end コマンド
- [ ] シーズン開始: seasonsテーブルにINSERT, is_active=true
- [ ] シーズン終了: スナップショット保存 → 全RP1000リセット
- [ ] season_snapshotsテーブル永続化

### 18. 管理者コマンド
- [ ] /bradmin frame reload（frames.yml再読み込み）
- [ ] /bradmin forcestart（キュー内で強制開始）
- [ ] /bradmin rp set <player> <weapon> <value>
- [ ] /bradmin map list
- [ ] /bradmin map info <name>
- [ ] 権限: brb.admin

### 19. UI/HUD
- [ ] 試合中スコアボード（サイドバー: マップ名、残り時間、エーテル、残り人数、キル数）
- [ ] ボスバー（試合タイマー、緑色、時間経過で減少）
- [ ] チャットプレフィックス（[S級] PlayerName）
- [ ] タブリスト（[A級] PlayerName）
- [ ] キルフィード（脱落通知メッセージ）

### 20. CLAUDE.md更新
- [ ] 新プロジェクト構造に合わせてCLAUDE.md書き直し
- [ ] 新名称（フレーム、エーテル、E-Shift等）反映
- [ ] 新コマンド体系反映
- [ ] Known Issues更新

---

## Phase 2以降（参考）

- [ ] 全フレーム特殊効果の完全実装（Seeker ホーミング、Bastion シールドモード等）
- [ ] マッチ履歴のDB保存・閲覧（/rank history）
- [ ] 複数アリーナマップ対応
- [ ] フレームセットプリセット保存・ロード
- [ ] シーズン報酬システム
- [ ] リソースパック（カスタム武器モデル、SE、エフェクト）
- [ ] マッチメイキング改善（RP近似マッチング）
- [ ] チームランキング
- [ ] 観戦者HUD（フリーカメラ、プレイヤー切替）
- [ ] カスタムマッチ（ルームコード、ルール設定）
- [ ] トーナメントモード
- [ ] フレームスキン、称号システム、ミッション
- [ ] 統計ダッシュボード（Web連携）

---

*最終更新: 2026-03-11*
