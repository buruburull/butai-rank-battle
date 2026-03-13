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

### 12. チームランクマッチ（完了）
- [x] チームキュー（/rank team）
- [x] ArenaInstance チーム戦コンストラクタ（addTeams, teleportToSpawns）
- [x] isTeammate() によるフレンドリーファイア防止
- [x] チーム勝利条件: 相手チーム全員脱落
- [x] チーム戦RP計算（非対称Elo + 生存ボーナス+10 + 貢献ボーナス+5）
- [x] チーム戦ジャッジ（チーム合算スコア）

### 13. 切断処理（完了）
- [x] PlayerQuitEvent検知 → 即E-Shift（脱落）
- [x] ソロ戦: 相手勝利処理
- [x] チーム戦: 脱落扱い、残りメンバーで続行
- [x] 切断カウント追跡（メモリ内、直近1時間）
- [x] 切断ペナルティ（2回:警告、3回:5分禁止、4回+:15分禁止）
- [x] ペナルティ中のキュー拒否メッセージ
- [x] プラクティス除外

### 14. ロビーシステム（完了）
- [x] ロビースポーン座標設定（config.yml）
- [x] ログイン時ロビーテレポート
- [x] ゲームモード: ADVENTURE
- [x] NPC配置（ランクマッチ、プラクティス、チームマッチ、フレームセット、戦績）
  - [x] AI無効、無敵、サイレント
  - [x] PersistentDataContainerタグ管理
  - [x] 右クリック→コマンド実行
- [x] ホログラム（TextDisplay）
  - [x] ウェルカムバナー
  - [x] ランキングTOP10（30秒更新）
- [x] アクションバー（ランク、RP、ステータス表示）

### 15. 観戦システム（完了）
- [x] /rank spectate [matchId] コマンド
- [x] /rank spectate leave コマンド
- [x] 観戦者セット管理（参加者と独立）
- [x] キュー中・試合中プレイヤーの観戦拒否

### 16. プラクティスバトル（完了）
- [x] /rank practice コマンド
- [x] プラクティスキュー（ソロキューと別管理）
- [x] RP変動なし
- [x] 切断ペナルティ対象外

### 17. シーズンシステム（完了）
- [x] /bradmin season start <name> コマンド
- [x] /bradmin season end コマンド
- [x] シーズン開始: seasonsテーブルにINSERT, is_active=true
- [x] シーズン終了: スナップショット保存 → 全RP1000リセット
- [x] season_snapshotsテーブル永続化

### 18. 管理者コマンド（完了）
- [x] /bradmin frame reload（frames.yml再読み込み）
- [x] /bradmin forcestart（スタブ実装）
- [x] /bradmin rp set <player> <weapon> <value>
- [x] /bradmin rp info <player>
- [x] /bradmin map list
- [x] /bradmin map info <name>
- [x] 権限: brb.admin

### 19. UI/HUD（完了）
- [x] 試合中スコアボード（サイドバー: マッチタイプ、残り時間、生存者数、エーテル表示）
- [x] ボスバー（試合タイマー、緑色、時間経過で減少）※Phase1-8で実装済み
- [x] チャットプレフィックス（[S級] PlayerName）
- [x] タブリスト（[A級] PlayerName）
- [x] キルフィード（脱落通知メッセージ）※Phase1-8で実装済み

### 20. CLAUDE.md更新（完了）
- [x] 新プロジェクト構造に合わせてCLAUDE.md書き直し
- [x] 新名称（フレーム、エーテル、E-Shift等）反映
- [x] 新コマンド体系反映
- [x] Known Issues更新

---

## Phase 2: 機能拡張

### 21. マッチ履歴DB保存（完了）
- [x] MatchHistoryDAO作成（match_history + match_results テーブルへのINSERT/SELECT）
- [x] ArenaInstance.finishMatch()でDB保存（マッチタイプ、マップ名、結果タイプ、所要時間）
- [x] 各プレイヤー結果保存（武器タイプ、ダメージ、エーテル残量、RP変動、順位）
- [x] 結果タイプの正確な追跡（kill/judge/sudden_death/draw/disconnect）
- [x] 切断時のresultType設定（onPlayerDisconnected）
- [x] /rank history [player] コマンド（直近10件表示）
- [x] タブ補完対応

### 22. 複数アリーナマップ対応（完了）
- [x] QueueManagerでFrameRegistryからランダムマップ選択
- [x] ArenaInstanceにマップ名・観戦座標・ボーダー半径を渡す
- [x] frames.ymlへの追加マップ定義（default, forest, ruins の3マップ）
- [x] ワールドボーダー適用（試合開始時にセット、終了時にリセット）
- [x] 観戦座標をマップ設定から取得（フォールバック: スポーン中間点）
- [x] マッチ開始時のマップ名表示

### 23. チームランキング
- [ ] team_rpの更新ロジック
- [ ] /team rank コマンド
- [ ] TeamDAO.updateTeamRP()

### 24. ~~RP近似マッチング~~（削除: マッチング速度低下のため、FIFOに戻した）

### 25. フレーム特殊効果の完全実装（完了）
- [x] Seeker: ホーミング射撃（20ブロック追尾、軌道修正）
- [x] Bastion: シールドモード切替（攻撃力半減・被ダメ60%カット）
- [x] Nova: 着弾時爆発（半径4.0、威力5.0）
- [x] Falcon: チャージ2.0秒→2.5倍（線形スケール）
- [x] Zenith: チャージ3.0秒→3.0倍（線形スケール）
- [x] Volt: 貫通3体（Arrow.setPierceLevel）
- [x] Leap: ジャンプブースト（上方+前方モメンタム）
- [x] Warp: 瞬間移動32m（レイトレース安全着地）
- [x] Rampart: 障壁生成（3x3バリアブロック、4秒消滅）
- [x] Blast: 爆発（半径5.0、威力6.0、距離減衰）
- [x] Tracer: 発光効果5秒（視線10度コーン内検索）
- [x] Barrier: 吸収8.0付与（Absorption II、10秒）
- [x] Cloak: 右クリックトグル発動/解除
- [x] FrameEffectListener新規作成（右クリック発動系フレーム）
- [x] CombatListener拡張（Nova爆発、Volt貫通、Falcon/Zenithチャージ、Bastion被ダメ）

## Phase 2以降（参考）

- [ ] シーズン報酬システム
- [ ] リソースパック（カスタム武器モデル、SE、エフェクト）
- [ ] 観戦者HUD（フリーカメラ、プレイヤー切替）
- [ ] カスタムマッチ（ルームコード、ルール設定）
- [ ] トーナメントモード
- [ ] フレームスキン、称号システム、ミッション
- [ ] 統計ダッシュボード（Web連携）

---

*最終更新: 2026-03-14*
