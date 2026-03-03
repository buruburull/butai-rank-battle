# Border Rank Battle - セットアップ手順書

このガイドでは、あなたが手動で行う必要がある作業を、コマンド付きで具体的に説明します。

---

## ステップ1: GitHubリポジトリの作成

### 1-1. GitHubアカウントの準備

GitHubアカウントがない場合は https://github.com にアクセスしてアカウントを作成してください。既にある場合はそのままでOKです。

### 1-2. リポジトリを作成する

ブラウザで行う場合は、GitHubにログインして右上の「+」→「New repository」をクリックし、以下のように設定してください。

- **Repository name**: `border-rank-battle`
- **Description**: `World Trigger inspired rank battle system for Minecraft`
- **Visibility**: Private（公開したくない場合）
- **Initialize**: チェックを入れない（README等は既にプロジェクトに含まれているため）

「Create repository」をクリックして完了です。

### 1-3. ローカルにクローン＆ファイルをプッシュ

ターミナル（Windows ならPowerShell、Mac ならTerminal）を開いて以下を実行してください。

```bash
# 1. 作業したいフォルダに移動（例: デスクトップ）
cd ~/Desktop

# 2. 空のリポジトリをクローン（YOUR_USERNAME をあなたのGitHub名に置き換え）
git clone https://github.com/YOUR_USERNAME/border-rank-battle.git

# 3. クローンしたフォルダに移動
cd border-rank-battle
```

次に、Claudeが生成した `brb-project` フォルダの中身をこのフォルダにコピーします。Claudeからダウンロードした `brb-project` フォルダがある場所に合わせてパスを調整してください。

```bash
# 4. brb-projectの中身を全てコピー（Mac/Linuxの場合）
cp -r /path/to/brb-project/* .
cp -r /path/to/brb-project/.gitignore .

# Windowsの場合はエクスプローラーでbrb-projectフォルダの中身を
# border-rank-battleフォルダにドラッグ＆ドロップしてもOK
```

### 1-4. dotclaude を .claude にリネーム

プロジェクト内の `dotclaude` フォルダを `.claude` に名前変更します。

```bash
# Mac/Linux
mv dotclaude .claude

# Windows (PowerShell)
Rename-Item -Path "dotclaude" -NewName ".claude"
```

### 1-5. 初回コミット＆プッシュ

```bash
# 5. 全ファイルをステージング
git add -A

# 6. 初回コミット
git commit -m "Initial project setup: Border Rank Battle v0.1.0-SNAPSHOT"

# 7. GitHubにプッシュ
git push origin main
```

> **うまくいかない場合**: `git push` で認証エラーが出たら、GitHubの Personal Access Token が必要です。GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic) → Generate new token で `repo` スコープにチェックを入れてトークンを作成し、パスワードの代わりに使ってください。

---

## ステップ2: VPS（サーバー）の契約

テストプレイ用なので、まずは安いプランで始めましょう。

### 2-1. おすすめVPSプロバイダー

| プロバイダー | プラン | スペック | 月額 | 備考 |
|---|---|---|---|---|
| **ConoHa VPS** | 2GBプラン | 3コア/2GB RAM/100GB SSD | 約1,848円 | 時間課金可能、日本語対応 |
| **さくらVPS** | 2GBプラン | 3コア/2GB RAM/100GB SSD | 約1,738円 | 安定、老舗 |
| **Xserver VPS** | 2GBプラン | 3コア/2GB RAM/50GB SSD | 約1,150円 | コスパ良い |
| **Agames** | マイクラ専用 | 4GB RAM | 約1,650円 | Minecraft特化、設定が楽 |

**推奨**: テスト段階では **2GB RAM** で十分です。本格運用時に4GB以上にスケールアップしてください。

### 2-2. 契約手順（ConoHaの例）

1. https://www.conoha.jp/vps/ にアクセス
2. 「お申し込み」をクリック
3. アカウント登録（メールアドレス、パスワード、電話番号認証）
4. プラン選択: **2GBプラン**
5. OS選択: **Ubuntu 22.04**
6. rootパスワードを設定（**必ずメモしてください**）
7. オプション: SSH鍵を追加（後述の手順で作成可能）
8. 支払い情報を入力して申し込み完了

契約後、コントロールパネルにサーバーのIPアドレスが表示されます（例: `123.456.78.90`）。これをメモしてください。

---

## ステップ3: サーバーの初期設定

### 3-1. SSHでサーバーに接続

```bash
# Mac/Linux のターミナルから
ssh root@あなたのIPアドレス

# Windows の場合は PowerShell から同じコマンドでOK
# または TeraTerm / PuTTY を使用

# 初回接続時「Are you sure you want to continue connecting?」と聞かれたら yes と入力
# パスワードを聞かれたら、契約時に設定したrootパスワードを入力
```

### 3-2. システムの更新

```bash
apt update && apt upgrade -y
```

### 3-3. Java 21のインストール

```bash
# Java 21 をインストール
apt install -y openjdk-21-jre-headless

# 確認
java -version
# → openjdk version "21.x.x" と表示されればOK
```

### 3-4. 作業用ユーザーの作成（セキュリティ上推奨）

rootのままでも動きますが、セキュリティ上は専用ユーザーを作ることを推奨します。

```bash
# minecraft ユーザーを作成
adduser minecraft
# パスワードを設定（聞かれた質問はEnterでスキップ可能）

# sudo権限を付与
usermod -aG sudo minecraft

# 以降は minecraft ユーザーで作業
su - minecraft
```

---

## ステップ4: MySQL 8.0のインストール

### 4-1. MySQLをインストール

```bash
sudo apt install -y mysql-server

# MySQLが起動しているか確認
sudo systemctl status mysql
# → active (running) と表示されればOK
```

### 4-2. MySQLの初期設定

```bash
# セキュリティ設定ウィザードを実行
sudo mysql_secure_installation

# 質問への回答:
# - VALIDATE PASSWORD component: y（パスワード強度チェックを有効化）
# - パスワード強度: 1 (MEDIUM) を推奨
# - Remove anonymous users: y
# - Disallow root login remotely: y
# - Remove test database: y
# - Reload privilege tables: y
```

### 4-3. ゲーム用データベースとユーザーを作成

```bash
# MySQLにログイン
sudo mysql

# 以下のSQLを実行（MySQL内で）:
```

```sql
-- データベース作成
CREATE DATABASE brb_game CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 専用ユーザー作成（パスワードは必ず変更してください！）
CREATE USER 'brb_user'@'localhost' IDENTIFIED BY 'ここに安全なパスワードを設定';

-- 権限付与
GRANT ALL PRIVILEGES ON brb_game.* TO 'brb_user'@'localhost';
FLUSH PRIVILEGES;

-- 確認
SHOW DATABASES;
-- brb_game が表示されればOK

EXIT;
```

### 4-4. スキーマを適用

プロジェクトの `docs/schema.sql` をサーバーにアップロードして実行します。

```bash
# ローカルPCから schema.sql をサーバーに転送（ローカルのターミナルで実行）
scp /path/to/border-rank-battle/docs/schema.sql minecraft@あなたのIPアドレス:~/

# サーバー側で schema.sql を実行
mysql -u brb_user -p brb_game < ~/schema.sql
# パスワードを聞かれたら、上で設定したパスワードを入力

# 確認（テーブル一覧を表示）
mysql -u brb_user -p brb_game -e "SHOW TABLES;"
# players, weapon_rp, trigger_master, player_loadouts 等が表示されればOK
```

---

## ステップ5: Paper 1.21.11サーバーの構築

### 5-1. サーバーディレクトリの作成

```bash
# サーバーに ssh した状態で
mkdir -p ~/minecraft-server
cd ~/minecraft-server
```

### 5-2. Paper JARのダウンロード

Paper 1.21.11 のビルドをダウンロードします。URLはPaperMCのサイトで最新を確認してください。

```bash
# Paper のダウンロードページ: https://papermc.io/downloads/paper
# 1.21.11 を選択して、表示されるダウンロードURLを使用

# 例（実際のURLはサイトで確認してください）:
wget https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/最新ビルド番号/downloads/paper-1.21.11-最新ビルド番号.jar -O paper.jar
```

> **ヒント**: ブラウザで https://papermc.io/downloads/paper にアクセスし、1.21.11を選んで右クリック→「リンクのアドレスをコピー」でURLを取得できます。

### 5-3. 初回起動（EULA同意）

```bash
# 初回起動（すぐ停止します）
java -Xms1G -Xmx1536M -jar paper.jar --nogui

# EULA に同意
nano eula.txt
# eula=false を eula=true に変更して保存（Ctrl+O → Enter → Ctrl+X）
```

### 5-4. server.properties の設定

```bash
nano server.properties
```

以下の項目を変更してください：

```properties
# サーバー名
motd=Border Rank Battle Server

# オンラインモード（正規版のみ接続可能にする場合はtrue）
online-mode=true

# ゲームモード
gamemode=adventure

# 難易度
difficulty=normal

# PvP有効
pvp=true

# 最大プレイヤー数（テスト用）
max-players=20

# 自然回復を無効化（ランク戦ではトリオンシステムが管理するため）
natural-regeneration=false

# スポーン保護を無効化
spawn-protection=0

# ホワイトリスト（テスト段階では有効推奨）
white-list=true
```

保存して閉じます（`Ctrl+O` → `Enter` → `Ctrl+X`）。

### 5-5. 起動スクリプトの作成

```bash
nano start.sh
```

以下を貼り付け：

```bash
#!/bin/bash
java -Xms1G -Xmx1536M \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 \
  -jar paper.jar --nogui
```

保存して実行権限を付与：

```bash
chmod +x start.sh
```

### 5-6. screen を使ってバックグラウンド起動

SSH接続を切ってもサーバーが止まらないように `screen` を使います。

```bash
# screen をインストール（なければ）
sudo apt install -y screen

# screenセッションを作成してサーバー起動
screen -S minecraft
cd ~/minecraft-server
./start.sh

# サーバーが起動したら、Ctrl+A → D でscreenをデタッチ（バックグラウンドに移行）
# 再度接続したい場合: screen -r minecraft
```

---

## ステップ6: プラグインのビルドとデプロイ

### 6-1. ローカルPCでビルド

ローカルPCにJava 21とGradleが必要です。

```bash
# Java 21のインストール確認
java -version

# なければインストール:
# Mac: brew install openjdk@21
# Windows: https://adoptium.net/ からインストーラーをダウンロード
# Linux: sudo apt install openjdk-21-jdk
```

プロジェクトのルートディレクトリでビルド：

```bash
cd /path/to/border-rank-battle

# Gradleラッパーに実行権限を付与（Mac/Linux）
chmod +x gradlew

# ビルド実行
./gradlew shadowJar

# Windows の場合:
# gradlew.bat shadowJar
```

ビルドが成功すると、以下にJARファイルが生成されます：
```
core-plugin/build/libs/BorderRankBattle-0.1.0-SNAPSHOT.jar
```

### 6-2. JARをサーバーに転送

```bash
# ローカルPCのターミナルから
scp core-plugin/build/libs/BorderRankBattle-0.1.0-SNAPSHOT.jar \
  minecraft@あなたのIPアドレス:~/minecraft-server/plugins/
```

### 6-3. config.yml をサーバーに配置

```bash
# triggers.yml をサーバーのプラグインフォルダに配置
scp config/triggers.yml \
  minecraft@あなたのIPアドレス:~/minecraft-server/plugins/BorderRankBattle/triggers.yml
```

> **注意**: プラグインフォルダ `BorderRankBattle/` は初回起動時に自動作成されます。もしまだ存在しない場合は、一度サーバーを再起動してから配置してください。

### 6-4. プラグインの設定ファイルを編集

サーバーにSSH接続し、DBの接続情報を設定します。

```bash
ssh minecraft@あなたのIPアドレス

# config.yml を編集（プラグイン初回起動後に生成される）
nano ~/minecraft-server/plugins/BorderRankBattle/config.yml
```

以下のDB接続情報を設定：

```yaml
database:
  host: localhost
  port: 3306
  name: brb_game
  user: brb_user
  password: ステップ4で設定したパスワード
```

### 6-5. サーバーを再起動

```bash
# screenに接続
screen -r minecraft

# サーバーコンソールで再起動
reload confirm
# または stop してから ./start.sh で再起動

# 正常にロードされたか確認（サーバーコンソールで）
plugins
# → [BorderRankBattle] が緑色で表示されればOK
```

---

## ステップ7: テストプレイの準備

### 7-1. ホワイトリストにプレイヤーを追加

サーバーコンソールで：

```
whitelist add あなたのMinecraft名
whitelist add テスト相手のMinecraft名
```

### 7-2. Minecraftクライアントから接続

1. Minecraft Java Edition を起動
2. マルチプレイ → サーバーを追加
3. サーバーアドレス: `あなたのIPアドレス:25565`
4. 接続

### 7-3. 動作確認コマンド

ゲーム内で以下を試してください：

```
/trigger list          ← トリガー一覧が表示される
/trigger preset        ← プリセットロードアウトが表示される
/rank stats            ← 自分の戦績が表示される
/rank solo             ← ソロマッチキューに参加
```

---

## トラブルシューティング

| 症状 | 原因と対処 |
|---|---|
| `java -version` でエラー | Java 21がインストールされていない → ステップ3-3を実行 |
| ビルドでエラー | Java 21のJDK（JREではなく）がローカルに必要 → `openjdk-21-jdk` をインストール |
| DB接続エラー | config.ymlのパスワードが間違っている / MySQLが起動していない |
| プラグインが赤色 | サーバーコンソールのエラーログを確認。依存関係不足の可能性 |
| 接続できない | VPSのファイアウォールで25565ポートを開放する必要あり（下記参照） |

### ファイアウォール設定（重要！）

VPSのポート25565を開放しないと外部から接続できません。

```bash
# Ubuntu の UFW を使う場合
sudo ufw allow 25565/tcp
sudo ufw allow 22/tcp    # SSH用（既に開いているはず）
sudo ufw enable
sudo ufw status
```

ConoHa等のクラウドの場合、Webコントロールパネルでもセキュリティグループの設定が必要な場合があります。コントロールパネル → セキュリティグループ → ルール追加 → TCP 25565 を許可してください。

---

## 作業の流れまとめ

```
[あなたの作業]                    [Claude Code で行う作業]

1. GitHub リポジトリ作成
2. コードをプッシュ
3. VPS 契約・初期設定
4. Java 21 + MySQL インストール
5. Paper サーバー構築               ← 完了したら声をかけてください
                                   6. コード修正・機能追加
                                   7. ビルド & テスト
8. JAR をサーバーにデプロイ
9. テストプレイ                     ← バグ報告してください
                                   10. バグ修正・調整
```

> 不明な点があれば、該当ステップの番号を教えてください。詳しく説明します。
