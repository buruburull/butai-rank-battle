# ワールドトリガー風ランク戦サーバー 企画書

**プロジェクト名:** Border Rank Battle（仮称）
**バージョン:** v3.0
**最終更新:** 2026-03-03
**対象:** Minecraft Java Edition 1.21.11（Paper Server）

---

## 1. プロジェクト概要

### コンセプト

ワールドトリガーのランク戦システムをMinecraft上に再現する。プレイヤーは「隊員」として個人戦・チーム戦に参加し、武器（トリガー）ごとのランクポイント（RP）を獲得・喪失しながらランクを上げていく。原作のトリガーセット（メイン/サブの8スロット構成）とトリオンコスト制を導入し、ロードアウトの戦略性を再現する。

### ターゲット

- 規模: 同時接続 10〜30人、登録ユーザー数百人
- 層: ワールドトリガーファン × Minecraft PvP 好き
- プラットフォーム: Minecraft Java Edition

### 原作から採用するコア要素

| 採用する要素 | 原作の対応要素 | 優先度 |
|---|---|---|
| ランクポイント制 | 個人ポイント・チームポイント | ★★★ |
| 階級システム | C級→B級→A級 | ★★★ |
| 武器種別ランク | アタッカー/シューター/スナイパー各RP | ★★★ |
| トリオンコスト制 | トリガーセットのコスト管理 | ★★★ |
| メイン/サブトリガー構成 | 右手トリガー・左手トリガー | ★★★ |
| トリオン総量（リソース） | トリオン体のHP・トリオン漏出 | ★★★ |
| チーム戦（3〜4人） | B級ランク戦 | ★★★ |
| 個人戦 | ソロランク戦 | ★★★ |
| マップランダム選出 | 戦闘エリア抽選 | ★★☆ |
| 観戦モード | 実況解説席 | ★☆☆ |

---

## 2. サーバーバージョン適性評価

### Paper 1.21.111 の評価

MC 1.21.11（"Mounts of Mayhem"）は2025年12月9日にリリースされた。ノーチラス、スピア、ネザライトの馬鎧などが追加されたバージョンで、**Java 21を要求する最後のバージョン**にあたる。

| 評価項目 | 判定 | 詳細 |
|---|---|---|
| **安定性** | ◎ | 2025年12月リリース。2026年3月時点で十分に安定 |
| **Java要件** | ○ | **Java 21必須**（Java 21を要求する最後のバージョン） |
| **API成熟度** | ○ | Paper APIは成熟しているが、**ハードフォーク後**のため1.21.1以前とは内部構造が異なる |
| **プラグイン互換性** | △ | **Spigot NMSマッピング非対応**。Spigot内部名（EntityHuman等）を使うプラグインは動作しない |
| **Mojangマッピング** | ◎ | 完全にMojangマッピングに移行済み。新規開発なら逆にクリーン |
| **主要プラグイン** | ○ | WorldEdit 7.4.0が1.21.11対応済み。ProtocolLibは要確認 |
| **将来性** | ◎ | 26.x系（2026年～）への移行パスが最も近い。ハードフォーク後のAPI知識がそのまま活きる |
| **新機能活用** | ◎ | **スピア（Spear）** が新武器として追加。トリガーシステムの武器候補に使える |
| **参考資料** | △ | ハードフォーク後のためSpigotフォーラムの旧情報がそのまま使えないケースあり |

### 1.21.1 との比較

```
              Paper 1.21.11              Paper 1.21.111
─────────────────────────────────────────────────────────
ベース       Spigotベース（最終世代）    Paperハードフォーク後
リリース     2024年8月                  2025年12月
Java         Java 21                    Java 21（最後のJava21版）
NMS互換      Spigotマッピング利用可     Spigotマッピング非対応 ★
既存Plugin   ほぼ全て動く               NMS依存プラグインは非互換
参考資料     Spigotフォーラム活用可      Paper公式ドキュメント中心
将来性       26.x系移行に追加作業必要    26.x系へ最も移行しやすい
新アイテム   Mace, Trial Chamber        ＋ Spear, Nautilus Armor等
```

### バージョン選択の結論

**Paper 1.21.111 は採用可能だが、1.21.1と比べて開発難易度がやや上がる。**

最大のポイントは**Paperハードフォーク後**であること。1.21.4以降PaperはSpigotから独立したため、Spigotの内部クラス名（NMSマッピング）を使ったプラグインが動作しない。ただし今回のプロジェクトは**新規開発**なので、NMS互換性の問題は自分たちのコードには影響しない。問題になるのはWorldEditなどの外部プラグインだが、WorldEdit 7.4.0が対応済みなのでここはクリア。

一方で、中級者チームにとっての注意点は参考資料の少なさ。Spigotフォーラムの既存ナレッジがそのまま使えないケースがあるため、Paper公式ドキュメントとJavadocを主な情報源にする必要がある。Claude Codeで開発する場合はClaudeがAPIの差異を吸収してくれるため、この影響は軽減される。

**メリット:** 26.x系への移行が最も容易。スピア（Spear）を新トリガーに活用可能。ハードフォーク後のクリーンなAPI。
**デメリット:** 旧Spigotプラグインの互換性リスク。ネット上の日本語情報がまだ少ない。

### 1.21.11 採用による開発期間への影響

```
フェーズ1（基盤構築）:     +0〜1週間（環境構築でハマる可能性）
フェーズ2（トリガー）:     +1〜2週間（API差異の学習コスト）
フェーズ3（試合システム）:  ±0（試合ロジック自体はバージョン非依存）
フェーズ4（チーム戦）:     ±0
フェーズ5（演出・UX）:     ±0
フェーズ6（テスト）:       +0〜1週間（外部プラグイン互換テスト）
────────────────────────────────────────────
合計影響: +1〜4週間

改定後の想定期間: 約 22〜30週間（5.5〜7.5ヶ月）
（1.21.1版の見込み: 20〜26週間）
```

この追加期間の大部分は、ハードフォーク後のPaper API差異に慣れるための学習コスト。Claude Codeを活用すれば差異の吸収が自動化されるため、実質的には +1〜2週間程度に抑えられる見込み。

### 将来のバージョンアップパス

```
現在: Paper 1.21.111（ハードフォーク後・Java 21）
  ↓ 2026年中に検討
将来: Paper 26.x系（新バージョン体系・Java 22以降?）
  ※ 1.21.11 → 26.x はハードフォーク後同士のため移行コストが最小
  ※ バージョンパーサー（"1."始まりの前提）の変更が必要
  ※ GameRule名のsnake_case移行は1.21.11で既に対応済み
```

### スピア（Spear）のトリガー活用案

1.21.11で追加されたスピア（投擲可能な近接武器）は、原作の「槍」系トリガーとして活用できる。

```
候補案:
  トリガー名: ゲイレール（仮）
  MCアイテム: スピア（1.21.11新アイテム）
  カテゴリ: ATTACKER
  コスト: 3 TP
  トリオン消費: 投擲時25/回
  特徴: 近接・投擲両用。投擲後に回収が必要（忠誠なし）
  → 原作のゲイレールのような突進攻撃をイメージ

  ※ バランステスト後にv2以降で追加を検討
```

---

## 3. ゲームシステム設計

### 3.1 ランク・階級システム

```
【階級】          【必要条件】                    【特典】
C級（訓練生）    → 初期状態                       → 個人戦のみ参加可
B級（正隊員）    → いずれかの武器RPが1,500以上     → チーム結成可、チーム戦参加可
A級（上位隊員）  → チーム戦RP上位10チーム          → シーズン報酬、称号
S級（特別）      → 運営認定（イベント用）          → 特別権限
```

### 3.2 ランクポイント（RP）計算

#### 個人戦RP

```
勝利時: +基礎RP × 補正係数
敗北時: -基礎RP × 補正係数
引き分け: ±0

基礎RP = 30
補正係数 = 1.0 + (相手RP - 自分RP) / 1000
  → 格上に勝つと大きく上がり、格下に負けると大きく下がる
最低変動: ±5（RP差が極端でも最低保証）
最大変動: ±60
```

#### チーム戦RP

```
基礎RP = 50
キル数ボーナス: キル1つにつき +5
生存ボーナス: 最後まで生存で +10
チーム順位ボーナス:
  1位: +30  2位: +10  3位: -10  4位: -20
```

#### 武器別RP

各武器種ごとに個別のRPを持つ。試合で**メイン武器として選択した武器種**のRPのみが変動する（サブトリガーのRPは変動しない）。

```
武器種: アタッカー / シューター / スナイパー
各武器RPの初期値: 1,000
```

#### B級昇格条件の詳細

「いずれかの武器RPが1,500以上」とは、3武器種の合算ではなく**1つの武器種を極めること**で昇格できる仕組み。

---

### 3.3 トリオン総量（リアルタイムリソース）システム

原作のトリオン体を再現する。プレイヤーは試合中に「トリオン」というリソースを持ち、トリガーの使用やダメージによって消費される。トリオンが0になると**強制緊急脱出（ベイルアウト）＝デス扱い**となる。

#### 基本パラメータ

```
トリオン最大値: 1000（全プレイヤー共通）
  ※ 将来的に階級やキャラクター設定でトリオン量を変動させることも可能

表示: Minecraftの経験値バー（XP Bar）でトリオン残量を表示
  → XP Level 表示: 現在のトリオン値（数値）
  → XP Bar:        トリオン残量の割合（1000 = 満タン、0 = 空）
```

#### トリオン消費：トリガー使用時

サブトリガー（補助系）を使用するたびにトリオンを消費する。攻撃トリガー（弧月・アステロイド等）の通常攻撃はトリオンを消費しない（原作でも通常の攻撃トリガーは常時使用可能なため）。

| トリガー | 消費タイプ | 消費量 | 備考 |
|---|---|---|---|
| グラスホッパー | 1回使用ごと | **40** | 機動力の代償。連続使用でトリオンが急減 |
| シールド | 被弾吸収ごと | **30** | ダメージカットのたびに消費 |
| バッグワーム | 毎秒持続消費 | **15/秒** | 長時間の隠密はトリオンを大量に消費 |
| テレポーター | 1回使用ごと | **60** | 高コスト。乱用するとすぐ枯渇 |
| エスクード | 1回使用ごと | **50** | 壁生成は重い |
| メテオラ（サブ） | 1回使用ごと | **35** | 爆発物は消費が大きい |
| レッドバレット | 1回使用ごと | **20** | 軽量デバフ弾 |
| レイガスト（シールドモード） | 毎秒持続消費 | **10/秒** | 防御姿勢中のみ消費 |

#### トリオン漏出：HP連動ダメージシステム ★新システム

原作では、トリオン体が損傷するとトリオンが漏出する。これをMinecraftのHP減少と連動させる。

```
【漏出メカニクス】

トリオン漏出量/秒 = (最大HP - 現在HP) × 漏出係数

漏出係数: 2.5 トリオン / 秒 / 1HP欠損

例:
  HP 20/20（無傷）  → 漏出なし
  HP 18/20（2ダメ） → (20 - 18) × 2.5 = 5.0 トリオン/秒
  HP 15/20（5ダメ） → (20 - 15) × 2.5 = 12.5 トリオン/秒
  HP 10/20（半分）  → (20 - 10) × 2.5 = 25.0 トリオン/秒
  HP  5/20（瀕死）  → (20 -  5) × 2.5 = 37.5 トリオン/秒
  HP  1/20（致命的）→ (20 -  1) × 2.5 = 47.5 トリオン/秒
```

```
【デス条件】

条件1: トリオンが0に到達 → 強制ベイルアウト（デス判定）
  → HPが残っていてもトリオン0でデス
  → 原作の「トリオン切れで戦闘体解除」を再現

条件2: HPが0に到達 → 通常のデス（MCデフォルト）
  → 一撃で大ダメージを受けた場合はHP0で即死もありうる

※ HP回復（自然回復）は無効化する（原作のトリオン体は自然回復しない）
※ 代わりに特定の条件でトリオンの漏出を止める手段を用意する（後述）
```

#### トリオン漏出の対抗手段

```
1. シールドで被弾を防ぐ → HPが減らない → 漏出が発生しない
2. エスクードで遮蔽物を作る → 攻撃を物理的に防ぐ
3. バッグワームで戦闘を避ける → ダメージを受けない（ただしバッグワーム自体がトリオン消費）
4. 早期決着を目指す → 漏出が致命的になる前に敵を倒す

※ HP回復手段は意図的に存在しない（原作準拠）
※ これにより「ダメージを受けた時点で不利」という緊張感が生まれる
```

#### 戦局への影響（ゲームデザイン意図）

```
1. 持久戦の抑制:
   → ダメージを受けると漏出が始まるため、長引くほど不利
   → アグレッシブな立ち回りを促進

2. ヒットアンドアウェイの重要性:
   → 一撃離脱で相手にダメージを与えてトリオン漏出を強制
   → スナイパーの価値が上がる（離れた場所から漏出を強制できる）

3. サブトリガーの使いどころ:
   → トリオンが有限なので、グラスホッパーやシールドを乱用できない
   → 「ここぞ」という場面で使う判断力が求められる

4. ロードアウトの戦略性向上:
   → サブトリガーを多くセットすると選択肢は増えるがトリオン管理がシビアに
   → 軽量ロードアウト（スコーピオン+バッグワーム等）は燃費が良い
```

#### MC実装方法（Paper 1.21.11）

```java
// === 経験値バーでトリオン表示 ===
// Player.setExp(float) で XP バーの割合を設定（0.0〜1.0）
// Player.setLevel(int) で XP レベル表示に数値を設定

player.setLevel(currentTrion);                    // 数値表示: "1000"
player.setExp((float) currentTrion / maxTrion);   // バー表示: 100%

// === 毎Tick処理（BukkitRunnable / 1秒 = 20tick）===
// 1秒ごとにトリオン漏出を計算・適用
new BukkitRunnable() {
    @Override
    public void run() {
        for (Player player : matchPlayers) {
            double maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double currentHp = player.getHealth();
            double missingHp = maxHp - currentHp;

            // HP漏出
            double leakPerSec = missingHp * 2.5;

            // バッグワーム等の持続消費を加算
            double sustainCost = getActiveSustainCost(player);

            double totalDrain = leakPerSec + sustainCost;
            trionMap.merge(player.getUniqueId(), -totalDrain, Double::sum);

            double trion = trionMap.get(player.getUniqueId());

            // XPバー更新
            player.setLevel((int) trion);
            player.setExp((float) Math.max(0, trion / 1000.0));

            // トリオン枯渇チェック
            if (trion <= 0) {
                triggerBailout(player);  // ベイルアウト（デス処理）
            }
        }
    }
}.runTaskTimer(plugin, 0L, 20L);  // 20tick = 1秒ごと

// === 自然回復の無効化 ===
// server.properties: natural-regeneration=false
// または GameRule で: world.setGameRule(GameRule.NATURAL_REGENERATION, false);

// === トリガー使用時のトリオン消費 ===
// PlayerInteractEvent で右クリックを検知し、トリオンを差し引く
// トリオン不足の場合はトリガー使用をキャンセル（アクションバーで警告表示）
```

#### 経験値バー表示の詳細

```
┌──────────────────────────────────────────────┐
│  画面表示イメージ                              │
│                                              │
│  [アクションバー] 弧月 | シールド CD: 3s       │
│                                              │
│  ████████████████████░░░░░  750              │
│  ↑ XPバー（トリオン残量）   ↑ XPレベル（数値） │
│                                              │
│  ♥♥♥♥♥♥♥♥♡♡  （HP: 16/20）                  │
│  → この場合 (20-16)×2.5 = 10 トリオン/秒漏出  │
│                                              │
│  [サイドバー]                                 │
│  === RANK BATTLE ===                         │
│  残り時間: 3:24                               │
│  キル: 2                                      │
│  生存: 3/4人                                  │
│  トリオン漏出: 10.0/s                         │
└──────────────────────────────────────────────┘
```

---

### 3.4 トリガーセット・コストシステム

#### 概念

原作のトリガーセットを再現する。プレイヤーは**トリオンポイント（TP）**という予算の中でメイントリガーとサブトリガーを組み合わせる。

```
┌─────────────────────────────────────────┐
│            トリガーセット                 │
│                                         │
│  【メイン（右手）】    【サブ（左手）】    │
│  ┌──────────┐      ┌──────────┐        │
│  │ Slot 1   │      │ Slot 5   │        │
│  │ 攻撃武器  │      │ 攻撃武器  │        │
│  ├──────────┤      ├──────────┤        │
│  │ Slot 2   │      │ Slot 6   │        │
│  │ サブ武器  │      │ サブ武器  │        │
│  ├──────────┤      ├──────────┤        │
│  │ Slot 3   │      │ Slot 7   │        │
│  │ オプション │      │ オプション │        │
│  ├──────────┤      ├──────────┤        │
│  │ Slot 4   │      │ Slot 8   │        │
│  │ 自由枠   │      │ 自由枠   │        │
│  └──────────┘      └──────────┘        │
│                                         │
│  合計コスト: ██ / 15 TP               │
└─────────────────────────────────────────┘
```

#### トリオンポイント（TP）ルール

```
基本TP上限: 15ポイント（全プレイヤー共通）
  ※ 将来的に階級やレベルでTP上限を変動させることも可能

ルール:
- メイン側に最低1つの攻撃トリガーが必須
- 同じトリガーをメイン・サブ両方にセット可（二刀流・両手持ち再現）
- 空スロットはコスト0（スロットを埋める義務はない）
- セットは試合開始前にのみ変更可能（試合中の変更不可）
```

---

### 3.4 トリガー一覧（コスト付き）

#### アタッカー系（近接武器）

| トリガー名 | MCアイテム | コスト | 特徴 |
|---|---|---|---|
| 弧月（こげつ） | ネザライト剣 | **4 TP** | 高火力・標準リーチ。アタッカーの基本武器 |
| スコーピオン | 金剣（攻撃速度UP） | **2 TP** | 低火力・高速攻撃・背後ダメージ1.5倍。軽量で安い |
| レイガスト | 鉄剣 + 盾モード | **3 TP** | 攻防一体。右クリックでシールドモードに切替 |
| ※ 双月（二刀弧月） | ネザライト剣×2 | **8 TP** | 弧月をメイン・サブ両方にセットした場合の特殊状態。火力2倍・防御不可 |

#### シューター系（射撃武器）

| トリガー名 | MCアイテム | コスト | 特徴 |
|---|---|---|---|
| アステロイド | 弓 | **3 TP** | 直線弾道・高精度。シューターの基本武器 |
| メテオラ | クロスボウ + 花火の星 | **4 TP** | 爆発弾・範囲ダメージ。高コストだが制圧力が高い |
| ハウンド | トライデント（忠誠） | **4 TP** | 追尾弾（ホーミング風）。命中率が高い |
| バイパー | 弓（カスタム弾道） | **3 TP** | プレイヤーの視線方向に弾道変化。テクニカル武器 |

#### スナイパー系（狙撃武器）

| トリガー名 | MCアイテム | コスト | 特徴 |
|---|---|---|---|
| イーグレット | 弓（射力V） | **4 TP** | 超長射程・高火力。チャージ2秒必要 |
| ライトニング | 弓 + 貫通 | **3 TP** | 壁貫通・低ダメージ。位置バレのリスクが少ない |
| アイビス | 弓（射力V + ダメージ強化） | **5 TP** | 最高火力の狙撃。チャージ3秒・発射後に位置が露出 |

#### サブトリガー（補助・機動系）

| トリガー名 | MCアイテム | コスト | 効果 | トリオン消費 | クールダウン |
|---|---|---|---|---|---|
| グラスホッパー | 羽根（右クリック） | **2 TP** | 空中ジャンプ台を設置。自分・味方が踏むと大ジャンプ | **40/回** | 8秒 |
| シールド | 盾（自動発動） | **2 TP** | 前方からの射撃ダメージを50%カット。耐久制（3回で破壊） | **30/被弾吸収** | 破壊後30秒で再生 |
| バッグワーム | 革ヘルメット（特殊） | **1 TP** | 装着中は透明化。攻撃するか被弾すると解除 | **15/秒（持続）** | 解除後15秒で再使用可 |
| テレポーター | エンダーパール（即時） | **3 TP** | 15ブロック前方に瞬間移動。壁抜け不可 | **60/回** | 12秒 |
| エスクード | 金ブロック（設置型） | **2 TP** | 地面から壁を生成（3×2×1ブロック）。10秒で消滅 | **50/回** | 15秒 |
| メテオラ（サブ） | TNT（投擲型） | **2 TP** | 投擲型の小爆発。ブロック破壊なし・プレイヤーにのみダメージ | **35/回** | 10秒 |
| レッドバレット | 光の矢（スロー付き） | **1 TP** | 命中した敵の移動速度を3秒間50%低下 | **20/回** | 6秒 |
| スタートリガー | 鉄インゴット（パッシブ） | **0 TP** | C級隊員のデフォルト装備。攻撃力は低いが無料 | **0** | なし |

> **TP（トリオンポイント）** はロードアウト構築時の予算。**トリオン消費** は試合中のリアルタイムリソース消費。この2つは別システム。

#### MC実装の補足（1.21.11固有の対応）

```
- 弓のカスタム弾道（バイパー等）: ProjectileLaunchEvent + 毎Tickの弾道制御で実装
- グラスホッパーの空中ジャンプ: Player.setVelocity() による打ち上げ
- シールドの自動発動: EntityDamageByEntityEvent でダメージソースを判定しダメージ軽減
- エスクードの壁生成: BlockPlaceイベントではなく、直接ブロックを配置＋スケジューラで時限撤去
- バッグワームの透明化: PotionEffect(PotionEffectType.INVISIBILITY) + 防具非表示
- テレポーター: RayTrace で移動先を判定（壁抜け防止）→ Player.teleport()
- レッドバレット: PotionEffect(PotionEffectType.SLOWNESS) を矢に付与

1.21.1で利用可能なAPI:
- Mace（メイス）: 1.21で追加された新武器。落下ダメージボーナスがあるため、
  グラスホッパーとの組み合わせで「空中から叩きつけ」という原作にない独自戦術が可能。
  オプション武器として将来追加を検討。
```

---

### 3.5 ロードアウト構成例

#### 万能アタッカー型（迅悠一風）

```
メイン: 弧月(4) + シールド(2) + グラスホッパー(2) + 空き(0)
サブ:   スコーピオン(2) + バッグワーム(1) + シールド(2) + 空き(0)
合計: 13 / 15 TP（余裕あり）
戦術: 弧月で正面から戦い、不利ならスコーピオンに切り替えて奇襲
```

#### 狙撃特化型（当真勇風）

```
メイン: イーグレット(4) + バッグワーム(1) + シールド(2) + 空き(0)
サブ:   ライトニング(3) + シールド(2) + エスクード(2) + 空き(0)
合計: 14 / 15 TP
戦術: バッグワームで隠密→イーグレットで一撃。エスクードで狙撃地点を防御
```

#### 二刀流アタッカー型（空閑遊真風）

```
メイン: スコーピオン(2) + グラスホッパー(2) + シールド(2) + 空き(0)
サブ:   スコーピオン(2) + グラスホッパー(2) + バッグワーム(1) + 空き(0)
合計: 11 / 15 TP（軽量・機動力重視）
戦術: 二刀スコーピオンで高速接近。グラスホッパーで縦横無尽に機動
```

#### 重火力シューター型（二宮匡貴風）

```
メイン: アステロイド(3) + ハウンド(4) + シールド(2) + 空き(0)
サブ:   メテオラ(4) + シールド(2) + 空き(0) + 空き(0)
合計: 15 / 15 TP（コスト上限ぴったり）
戦術: 複数の射撃武器で弾幕を張る。全スロット射撃で近接拒否
```

#### バランスオールラウンダー型（三雲修風）

```
メイン: レイガスト(3) + アステロイド(3) + スタートリガー(0) + 空き(0)
サブ:   シールド(2) + レッドバレット(1) + エスクード(2) + バッグワーム(1)
合計: 12 / 15 TP
戦術: 火力は低いが堅実。レッドバレットで味方の攻撃を支援
```

---

### 3.6 試合中のトリガー切替操作

```
試合中のトリガー切替（MCでの操作）:
- ホットバーのSlot 1〜4 = メイントリガー（Slot 1〜4）
- Fキー（オフハンド切替）= メイン⇔サブの切替
  → 切替後はホットバーがサブトリガー（Slot 5〜8）に変わる
- 切替には0.5秒のクールダウン（連打防止）

表示:
- 経験値バー（XPバー）: トリオン残量（バー＝割合、レベル数値＝トリオン値）
- アクションバー: 現在の装備名・残りクールダウン・トリオン漏出警告
- サイドバースコアボード: 残り時間・キル数・生存者数・トリオン漏出量/秒
```

---

### 3.7 試合フロー

#### 個人戦（ソロランク）

```
1. /rank solo でキュー登録
2. 2〜4人マッチング（30秒待機 → 足りなければBot補充 or キャンセル）
3. マップ抽選 → テレポート
4. 10秒カウントダウン
5. 制限時間5分 or 全員1デスで終了
6. キル数で順位決定 → RP精算
7. ロビーに帰還
```

#### チーム戦（B級ランク戦）

```
1. チームリーダーが /rank team でキュー登録（3〜4人パーティ必須）
2. 2〜4チームマッチング
3. マップ抽選 → 各チームスポーン地点にテレポート
4. 30秒準備フェーズ（作戦タイム）
5. 制限時間10分 or 生存チーム1つで終了
6. 生存者数 + キル数で順位決定 → RP精算
7. ロビーに帰還
```

### 3.8 マップシステム

5〜8種類の戦闘マップを用意し、ランダムに抽選する。

| マップ名 | テーマ | サイズ | 特徴 |
|---|---|---|---|
| 市街地A | 都市・ビル | 150×150 | 高低差あり、スナイパー有利 |
| 河川敷 | 平地・橋 | 120×120 | 開けた地形、シューター有利 |
| 工業地帯 | 工場・配管 | 130×130 | 入り組んだ構造、アタッカー有利 |
| 住宅街 | 民家・路地 | 100×100 | 狭い通路、接近戦多発 |
| 商業施設 | ショッピングモール | 140×140 | 複数階層、バランス型 |

マップは WorldEdit / Schematics で管理し、試合ごとにコピーして使用する。

---

## 4. 技術アーキテクチャ

### 4.1 サーバー構成

```
[Velocity Proxy]
    ├── [Lobby Server]      ... ロビー・マッチメイキング
    ├── [Game Server 1]     ... 試合インスタンス
    ├── [Game Server 2]     ... 試合インスタンス（拡張用）
    └── [DB Server]         ... MySQL / MariaDB
```

小規模であれば、最初はProxy + 1サーバーでロビーと試合を同一サーバー内で処理し、
人数が増えたらマルチサーバー構成に拡張する。

### 4.2 技術スタック

| レイヤー | 技術 | 理由 |
|---|---|---|
| サーバーソフト | **Paper 1.21.111** | 安定版・ハードフォーク後・26.x系への移行パス◎ |
| Java | **Java 21** | Paper 1.21.x の必須要件 |
| プロキシ | Velocity | 現代的・高性能 |
| プラグイン言語 | Java 21 / Kotlin | Paper公式サポート |
| データベース | MySQL 8.0 / MariaDB | RP・プレイヤーデータ永続化 |
| キャッシュ | Redis（将来） | セッション管理・リアルタイム集計 |
| ビルドツール | Gradle (Kotlin DSL) | 依存関係管理 |
| バージョン管理 | Git + GitHub | Claude Code Teams連携 |

### 4.3 プラグイン構成

モノリポ構成で、機能ごとにモジュール分割する。

```
border-rank-battle/
├── build.gradle.kts
├── settings.gradle.kts
├── common/                    # 共通ライブラリ
│   └── src/main/java/
│       ├── model/             # Player, Team, Match データクラス
│       ├── database/          # DAO・リポジトリ
│       └── util/              # ユーティリティ
├── core-plugin/               # メインプラグイン
│   └── src/main/java/
│       ├── BRBPlugin.java     # エントリーポイント
│       ├── command/           # コマンドハンドラ
│       ├── listener/          # イベントリスナー
│       ├── manager/
│       │   ├── MatchManager        # 試合管理
│       │   ├── QueueManager        # マッチメイキング
│       │   ├── RankManager         # RP計算・階級管理
│       │   ├── TriggerManager      # トリガーセット・コスト管理
│       │   ├── TrionManager        # トリオン残量管理・漏出計算・XPバー表示
│       │   ├── LoadoutManager      # ロードアウト永続化
│       │   ├── CooldownManager     # クールダウン管理
│       │   ├── MapManager          # マップ抽選・ロード
│       │   └── ScoreboardManager   # スコアボード表示
│       └── arena/
│           ├── ArenaInstance       # 試合インスタンス
│           └── ArenaState          # 状態マシン
├── trigger-plugin/            # トリガー実装（分離可）
│   └── src/main/java/
│       ├── attacker/          # アタッカー系トリガー
│       ├── shooter/           # シューター系トリガー
│       ├── sniper/            # スナイパー系トリガー
│       ├── support/           # サブトリガー（グラスホッパー等）
│       ├── ability/           # 特殊能力（背後ダメージ等）
│       └── loadout/           # ロードアウトUI
└── api/                       # 外部連携API（将来）
    └── src/main/java/
```

### 4.4 データベース設計

```sql
-- プレイヤー基本情報
CREATE TABLE players (
    uuid         VARCHAR(36) PRIMARY KEY,
    name         VARCHAR(16) NOT NULL,
    rank_class   ENUM('C','B','A','S') DEFAULT 'C',
    trion_cap    INT DEFAULT 15,              -- TP上限（ロードアウト構築用、将来の拡張用）
    trion_max    INT DEFAULT 1000,            -- トリオン総量（試合中リソース上限）
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login   TIMESTAMP
);

-- 武器別ランクポイント
CREATE TABLE weapon_rp (
    uuid         VARCHAR(36),
    weapon_type  ENUM('ATTACKER','SHOOTER','SNIPER'),
    rp           INT DEFAULT 1000,
    wins         INT DEFAULT 0,
    losses       INT DEFAULT 0,
    PRIMARY KEY (uuid, weapon_type),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);

-- トリガー定義マスタ（YAML設定からロード）
CREATE TABLE trigger_master (
    trigger_id   VARCHAR(32) PRIMARY KEY,     -- 例: 'kogetsu', 'grasshopper'
    trigger_name VARCHAR(32) NOT NULL,        -- 表示名
    category     ENUM('ATTACKER','SHOOTER','SNIPER','SUPPORT') NOT NULL,
    cost         INT NOT NULL,                -- TPコスト（ロードアウト構築用）
    trion_use    INT DEFAULT 0,              -- 1回使用あたりのトリオン消費
    trion_sustain DECIMAL(5,1) DEFAULT 0,    -- 毎秒の持続トリオン消費（バッグワーム等）
    slot_type    ENUM('MAIN','SUB','BOTH'),   -- 装備可能スロット
    mc_item      VARCHAR(64),                 -- MCアイテムID
    description  TEXT
);

-- プレイヤーのロードアウト（トリガーセット）
CREATE TABLE player_loadouts (
    uuid         VARCHAR(36),
    loadout_name VARCHAR(32) DEFAULT 'default',
    slot_1       VARCHAR(32) NULL,            -- メインSlot1（攻撃武器）
    slot_2       VARCHAR(32) NULL,            -- メインSlot2
    slot_3       VARCHAR(32) NULL,            -- メインSlot3
    slot_4       VARCHAR(32) NULL,            -- メインSlot4
    slot_5       VARCHAR(32) NULL,            -- サブSlot1（攻撃武器）
    slot_6       VARCHAR(32) NULL,            -- サブSlot2
    slot_7       VARCHAR(32) NULL,            -- サブSlot3
    slot_8       VARCHAR(32) NULL,            -- サブSlot4
    total_cost   INT DEFAULT 0,              -- 合計コスト（バリデーション用）
    PRIMARY KEY (uuid, loadout_name),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);

-- チーム情報
CREATE TABLE teams (
    team_id      INT AUTO_INCREMENT PRIMARY KEY,
    team_name    VARCHAR(32) UNIQUE NOT NULL,
    leader_uuid  VARCHAR(36),
    team_rp      INT DEFAULT 0,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (leader_uuid) REFERENCES players(uuid)
);

-- チームメンバー
CREATE TABLE team_members (
    team_id      INT,
    uuid         VARCHAR(36),
    role         ENUM('LEADER','MEMBER') DEFAULT 'MEMBER',
    PRIMARY KEY (team_id, uuid),
    FOREIGN KEY (team_id) REFERENCES teams(team_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);

-- 試合履歴
CREATE TABLE match_history (
    match_id     INT AUTO_INCREMENT PRIMARY KEY,
    match_type   ENUM('SOLO','TEAM'),
    map_name     VARCHAR(32),
    season_id    INT NULL,
    started_at   TIMESTAMP,
    ended_at     TIMESTAMP,
    duration_sec INT,
    FOREIGN KEY (season_id) REFERENCES seasons(season_id)
);

-- 試合参加者の成績
CREATE TABLE match_results (
    match_id     INT,
    uuid         VARCHAR(36),
    team_id      INT NULL,
    weapon_type  ENUM('ATTACKER','SHOOTER','SNIPER'),
    loadout_hash VARCHAR(64),                -- 使用したロードアウトのハッシュ（分析用）
    kills        INT DEFAULT 0,
    deaths       INT DEFAULT 0,
    survived     BOOLEAN DEFAULT FALSE,
    rp_change    INT DEFAULT 0,
    placement    INT,
    PRIMARY KEY (match_id, uuid),
    FOREIGN KEY (match_id) REFERENCES match_history(match_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);

-- シーズン情報
CREATE TABLE seasons (
    season_id    INT AUTO_INCREMENT PRIMARY KEY,
    season_name  VARCHAR(32),
    start_date   DATE,
    end_date     DATE,
    is_active    BOOLEAN DEFAULT FALSE
);

-- シーズン終了時のスナップショット
CREATE TABLE season_snapshots (
    season_id    INT,
    uuid         VARCHAR(36),
    weapon_type  ENUM('ATTACKER','SHOOTER','SNIPER'),
    final_rp     INT,
    placement    INT,
    PRIMARY KEY (season_id, uuid, weapon_type),
    FOREIGN KEY (season_id) REFERENCES seasons(season_id),
    FOREIGN KEY (uuid) REFERENCES players(uuid)
);

-- 推奨インデックス
CREATE INDEX idx_weapon_rp_ranking ON weapon_rp(weapon_type, rp DESC);
CREATE INDEX idx_match_results_uuid ON match_results(uuid);
CREATE INDEX idx_match_history_season ON match_history(season_id);
CREATE INDEX idx_players_rank ON players(rank_class);
```

### 4.5 トリガー定義YAML（設定ファイル例）

```yaml
# triggers.yml - サーバー設定ファイルとして配置
triggers:
  kogetsu:
    name: "弧月"
    category: ATTACKER
    cost: 4
    slot_type: BOTH
    mc_item: NETHERITE_SWORD
    damage: 8.0
    attack_speed: 1.6
    special: null
    description: "高火力の標準近接武器"

  scorpion:
    name: "スコーピオン"
    category: ATTACKER
    cost: 2
    slot_type: BOTH
    mc_item: GOLDEN_SWORD
    damage: 5.0
    attack_speed: 2.4
    special:
      type: BACKSTAB_MULTIPLIER
      value: 1.5
    description: "低火力だが高速・背後攻撃1.5倍"

  grasshopper:
    name: "グラスホッパー"
    category: SUPPORT
    cost: 2
    trion_use: 40              # 1回使用ごとに40トリオン消費
    trion_sustain: 0
    slot_type: BOTH
    mc_item: FEATHER
    cooldown: 8
    special:
      type: LAUNCH_PAD
      launch_power: 1.8
      duration: 3
    description: "空中ジャンプ台を設置"

  shield_trigger:
    name: "シールド"
    category: SUPPORT
    cost: 2
    trion_use: 30              # 被弾吸収ごとに30トリオン消費
    trion_sustain: 0
    slot_type: BOTH
    mc_item: SHIELD
    cooldown: 30
    special:
      type: DAMAGE_REDUCTION
      reduction: 0.5
      durability: 3
    description: "前方射撃ダメージ50%カット（3回で破壊）"

  bagworm:
    name: "バッグワーム"
    category: SUPPORT
    cost: 1
    trion_use: 0
    trion_sustain: 15          # 毎秒15トリオン持続消費
    slot_type: BOTH
    mc_item: LEATHER_HELMET
    cooldown: 15
    special:
      type: INVISIBILITY
      break_on_attack: true
      break_on_damage: true
    description: "透明化。攻撃or被弾で解除"

  teleporter:
    name: "テレポーター"
    category: SUPPORT
    cost: 3
    trion_use: 60              # 1回使用ごとに60トリオン消費
    trion_sustain: 0
    slot_type: BOTH
    mc_item: ENDER_PEARL
    cooldown: 12
    special:
      type: TELEPORT
      range: 15
      wall_pass: false
    description: "15ブロック前方に瞬間移動"

  escudo:
    name: "エスクード"
    category: SUPPORT
    cost: 2
    trion_use: 50              # 1回使用ごとに50トリオン消費
    trion_sustain: 0
    slot_type: BOTH
    mc_item: GOLD_BLOCK
    cooldown: 15
    special:
      type: WALL_SPAWN
      size: [3, 2, 1]
      duration: 10
    description: "地面から壁を生成（3×2×1）。10秒で消滅"

  red_bullet:
    name: "レッドバレット"
    category: SUPPORT
    cost: 1
    trion_use: 20              # 1回使用ごとに20トリオン消費
    trion_sustain: 0
    slot_type: BOTH
    mc_item: SPECTRAL_ARROW
    cooldown: 6
    special:
      type: SLOW_EFFECT
      duration: 3
      amplifier: 1
    description: "命中で3秒間移動速度50%低下"

# トリオンシステム設定
trion:
  max_trion: 1000                   # トリオン最大値
  leak_coefficient: 2.5             # HP欠損1あたりの漏出量/秒
  low_trion_warning: 200            # この値以下でアクションバーに警告表示
  critical_trion_warning: 100       # この値以下で画面赤点滅
  bailout_threshold: 0              # この値でベイルアウト（デス）
  natural_regeneration: false       # HP自然回復を無効化
```

---

## 5. コマンド一覧

### プレイヤー向け

| コマンド | 説明 |
|---|---|
| `/rank solo` | 個人戦キューに参加 |
| `/rank team` | チーム戦キューに参加（リーダーのみ） |
| `/rank cancel` | キューから離脱 |
| `/rank stats [player]` | RP・戦績を確認 |
| `/rank top [weapon]` | 武器別ランキング表示 |
| `/trigger set <slot> <trigger_id>` | 指定スロットにトリガーをセット |
| `/trigger remove <slot>` | 指定スロットのトリガーを外す |
| `/trigger list [category]` | 使用可能なトリガー一覧（コスト表示付き） |
| `/trigger view [player]` | トリガーセット全体を表示（残りTP含む） |
| `/trigger preset save <名前>` | 現在のセットをプリセット保存 |
| `/trigger preset load <名前>` | プリセットを読み込み |
| `/team create <名前>` | チーム作成（B級以上） |
| `/team invite <player>` | チームに招待 |
| `/team leave` | チーム脱退 |
| `/team info [チーム名]` | チーム情報表示 |
| `/spectate <player>` | 観戦モード |

### 管理者向け

| コマンド | 説明 |
|---|---|
| `/bradmin trigger reload` | triggers.yml を再読み込み |
| `/bradmin trigger cost <trigger_id> <値>` | コストを一時的に変更（バランス調整用） |
| `/bradmin map add <名前>` | マップ登録 |
| `/bradmin map remove <名前>` | マップ削除 |
| `/bradmin season start <名前>` | シーズン開始 |
| `/bradmin season end` | シーズン終了・集計 |
| `/bradmin rp set <player> <weapon> <値>` | RP手動設定 |
| `/bradmin forcestart` | 強制試合開始 |

---

## 6. 開発ロードマップ

### フェーズ1: 基盤構築（2〜3週間）

**ゴール:** サーバーに入ってRPが記録される状態

- [ ] Paper 1.21.11サーバー環境構築（**Java 21**）
- [ ] Gradleプロジェクト作成（モノリポ構成）
- [ ] MySQL接続・プレイヤーテーブル作成
- [ ] プレイヤーのログイン/ログアウト処理
- [ ] 基本コマンドフレームワーク（`/rank stats`）
- [ ] RP管理の基本CRUD

**Claude Code Teamsでの分担例:**
- メンバーA: DB設計・DAO実装
- メンバーB: プラグイン骨格・コマンドフレームワーク

### フェーズ2: トリガー・コストシステム（3〜4週間）

**ゴール:** トリガーセットを組んでカスタム武器で戦える状態

- [ ] triggers.yml パーサー・トリガーマスタDB連携
- [ ] TriggerManager実装（コスト計算・バリデーション）
- [ ] アタッカー系トリガーの実装（弧月・スコーピオン・レイガスト）
- [ ] シューター系トリガーの実装（アステロイド・メテオラ・ハウンド・バイパー）
- [ ] スナイパー系トリガーの実装（イーグレット・ライトニング・アイビス）
- [ ] サブトリガーの実装（グラスホッパー・シールド・バッグワーム・テレポーター・エスクード・レッドバレット）
- [ ] ロードアウトUI（チェストメニュー + コスト表示）
- [ ] メイン⇔サブ切替操作の実装
- [ ] クールダウンマネージャー
- [ ] TrionManager実装（トリオン残量管理・XPバー表示・漏出計算）
- [ ] トリガー使用時のトリオン消費処理
- [ ] HP連動トリオン漏出の毎秒Tick処理
- [ ] トリオン枯渇時のベイルアウト（デス）処理
- [ ] トリオン警告表示（低トリオン時のアクションバー・画面演出）
- [ ] 自然回復無効化（GameRule設定）
- [ ] 武器バランス調整用の設定ファイル

### フェーズ3: 試合システム（4〜5週間）★最重要フェーズ

**ゴール:** 個人戦が回る状態

- [ ] マッチメイキングキュー実装
- [ ] アリーナインスタンス管理（状態マシン）
- [ ] マップロード（WorldEdit Schematic）
- [ ] テレポート・カウントダウン処理
- [ ] 試合中のキル/デス検知
- [ ] 試合終了判定・RP精算
- [ ] 結果表示・ロビー帰還

### フェーズ4: チーム戦・ランキング（2〜3週間）

**ゴール:** チーム戦とランキングが動く状態

- [ ] チームCRUD
- [ ] チーム戦マッチメイキング
- [ ] チーム戦RP計算ロジック
- [ ] B級昇格条件チェック
- [ ] ランキングボード（ホログラム or スコアボード）
- [ ] 武器別ランキングコマンド

### フェーズ5: 演出・UX改善（2〜3週間）

**ゴール:** 遊んで楽しい状態

- [ ] ロビーデザイン（ランキング掲示板・NPC）
- [ ] 試合中のスコアボード/ボスバー演出
- [ ] キルメッセージのカスタマイズ
- [ ] 観戦モード
- [ ] シーズンシステム・リセット処理
- [ ] サウンド・パーティクル演出

### フェーズ6: テスト・公開（1〜2週間）

**ゴール:** 安定稼働

- [ ] 負荷テスト（20人同時接続）
- [ ] RP計算の境界値テスト
- [ ] トリガーコスト・バランステスト
- [ ] 不正対策（Anti-Cheat連携）
- [ ] バグ修正・バランス調整
- [ ] 公開・コミュニティ告知

**合計想定期間: 約 22〜30週間（5.5〜7.5ヶ月）**

> **注意（1.21.11固有）:** Paper 1.21.11はハードフォーク後のため、Spigotフォーラムの旧情報がそのまま使えないケースがある。Paper公式Javadocとドキュメントを主な情報源とすること。Claude Codeを活用すればAPI差異の吸収が自動化されるため、学習コストは+1〜2週間程度に抑えられる。フェーズ3（試合システム）は非同期処理・状態管理が複雑なため、4〜5週間を見込むこと。Paper APIのスレッド制約（メインスレッドでDB I/Oを行わない）に特に注意が必要。`CompletableFuture` や `Bukkit.getScheduler().runTaskAsynchronously()` を活用すること。GameRuleはsnake_case（1.21.11で変更済み）を使用すること。

---

## 7. インフラ・運用

### サーバースペック（推奨）

| 項目 | スペック | 月額目安 |
|---|---|---|
| VPS | 4コア / 8GB RAM / 100GB SSD | ¥3,000〜5,000 |
| Java | **Java 21**（OpenJDK 21 / Adoptium Temurin 21） | 無料 |
| ドメイン | .com or .jp | ¥1,000〜2,000/年 |
| MySQL | VPS内に同居（初期） | ¥0（VPS内） |
| バックアップ | 日次自動バックアップ | VPS付属 or ¥500 |

**推奨VPS:** Xserver VPS、ConoHa VPS、Agames（Minecraft特化）

### 月間コスト見積もり

```
VPS:            ¥4,000
ドメイン:        ¥150（年額÷12）
Claude Code Teams: 利用分
----------------------------
合計:           約 ¥5,000〜10,000/月
```

---

## 8. Claude Code Teams 活用方針

### リポジトリ構成

GitHub上にモノリポを作成し、Claude Code Teamsで共同開発する。

```
GitHubリポジトリ: border-rank-battle
├── .claude/
│   ├── settings.json          # プロジェクト共通設定
│   └── CLAUDE.md              # Claude向けプロジェクト説明
├── docs/                      # 設計ドキュメント
│   ├── game-design.md         # このファイルの詳細版
│   ├── api-spec.md            # 内部API仕様
│   └── db-schema.sql          # DB定義
├── server/                    # プラグインソースコード
│   ├── build.gradle.kts
│   ├── common/
│   ├── core-plugin/
│   └── trigger-plugin/
├── maps/                      # マップデータ（Git LFS）
├── config/                    # サーバー設定テンプレート
│   └── triggers.yml           # トリガー定義
└── scripts/                   # デプロイ・運用スクリプト
```

### CLAUDE.md に記載すべき内容

```markdown
# Border Rank Battle - プロジェクトガイド

## 概要
ワールドトリガー風ランク戦Minecraftサーバーのプラグイン開発プロジェクト

## 技術スタック
- Paper 1.21.11 プラグイン（Java 21 必須）
- Gradle Kotlin DSL でビルド
- MySQL 8.0 でデータ永続化

## コーディング規約
- パッケージ: com.borderrank.battle.*
- 命名規則: Java標準（camelCase / PascalCase）
- DB操作: HikariCP接続プール使用、非同期実行（メインスレッドでDB I/Oしない）
- イベント処理: Paper Event API準拠
- トリガー追加: triggers.yml にデータ定義 → trigger-plugin に挙動実装
- トリオン管理: TrionManager が毎秒Tickで漏出計算。XPバーで表示。メインスレッドで処理（軽量計算のため）

## 重要なファイル
- core-plugin/src/main/java/.../BRBPlugin.java（エントリーポイント）
- common/src/main/java/.../database/DatabaseManager.java（DB管理）
- trigger-plugin/src/main/java/.../support/（サブトリガー実装）
- config/triggers.yml（トリガーマスタ定義）
- core-plugin/src/main/resources/plugin.yml（プラグイン定義）

## テスト
- JUnit 5 + MockBukkit でユニットテスト
- テスト実行: ./gradlew test
- トリガーコストのバリデーションテストは必須

## ビルド・デプロイ
- ビルド: ./gradlew shadowJar
- 成果物: core-plugin/build/libs/BorderRankBattle-*.jar
- デプロイ: plugins/ ディレクトリにコピー → サーバー再起動
```

### 開発フローの例

1. GitHub Issueでタスクを管理（フェーズごとにマイルストーン設定）
2. 各メンバーがClaude Codeでブランチ作成→実装→PR作成
3. コードレビュー（Claudeにレビュー依頼も可能）
4. mainにマージ → ビルド → テストサーバーにデプロイ

---

## 9. 法的注意事項

- 「ワールドトリガー」は集英社・葦原大介氏の著作物であるため、公開サーバーで原作名称をそのまま使用するとIP侵害のリスクがある
- **推奨対応:**
  - サーバー名・プラグイン名にはオリジナル名称を使用する（例: 「Border Rank Battle」）
  - 武器名は原作と同一にせず、インスパイア名称にする（例: 弧月 → Crescent Blade）
  - 「ワールドトリガーをモチーフにしたファンサーバー」という位置づけを明記する
  - 収益化する場合は特に注意（二次創作ガイドラインを確認）
- 身内サーバーであれば原作名称をそのまま使ってもリスクは低いが、公開時には変更を推奨

---

## 10. 次のアクション（今すぐやること）

1. **Java 21をインストール** する（Adoptium Temurin 21推奨）
2. **GitHubリポジトリを作成** する（`border-rank-battle`）
3. **Gradleプロジェクトを初期化** する（Paper 1.21.11 plugin template使用）
4. **テスト用Paper 1.21.11サーバーをローカルに立てる**
5. **triggers.yml のドラフトを作成** する（全トリガーのコスト・パラメータ定義）
6. **CLAUDE.md を配置** してClaude Code Teams環境を整える
7. **フェーズ1のGitHub Issueを作成** する（5〜6個のタスクに分解済み）

---

*この企画書は開発の進行に合わせて随時更新すること。*
*武器バランス・RP計算式・トリガーコストはテストプレイを経て調整が必要。*
