package com.butai.rankbattle;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class BRBPlugin extends JavaPlugin {

    private static BRBPlugin instance;
    private Logger log;

    public static BRBPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        log = getLogger();

        log.info("=== BUTAI Rank Battle v" + getDescription().getVersion() + " ===");
        log.info("BRB プラグインを起動しています...");

        // TODO: DatabaseManager初期化
        // TODO: Manager初期化 (RankManager, EtherManager, QueueManager, etc.)
        // TODO: コマンド登録
        // TODO: リスナー登録
        // TODO: config読み込み

        log.info("BRB プラグインが正常に起動しました！");
    }

    @Override
    public void onDisable() {
        log.info("BRB プラグインを停止しています...");

        // TODO: DBコネクションプール閉じる
        // TODO: 進行中マッチの終了処理

        log.info("BRB プラグインが停止しました。");
    }
}
