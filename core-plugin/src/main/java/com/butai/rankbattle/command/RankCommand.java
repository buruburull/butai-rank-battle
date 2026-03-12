package com.butai.rankbattle.command;

import com.butai.rankbattle.manager.QueueManager;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles /rank commands: solo, cancel, stats, top, practice
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private final QueueManager queueManager;

    public RankCommand(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "solo" -> handleSolo(player);
            case "practice" -> handlePractice(player);
            case "cancel" -> handleCancel(player);
            case "status" -> handleStatus(player);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private boolean handleSolo(Player player) {
        String error = queueManager.joinSoloQueue(player.getUniqueId());
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        MessageUtil.sendSuccess(player, "§fソロランク §7キューに参加しました。");
        MessageUtil.sendInfo(player, "キュー内: §f" + queueManager.getSoloQueueSize() + "人 §7| マッチング待機中...");
        MessageUtil.sendInfo(player, "キャンセル: §e/rank cancel");
        return true;
    }

    private boolean handlePractice(Player player) {
        String error = queueManager.joinPracticeQueue(player.getUniqueId());
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        MessageUtil.sendSuccess(player, "§fプラクティス §7キューに参加しました。(RP変動なし)");
        MessageUtil.sendInfo(player, "キャンセル: §e/rank cancel");
        return true;
    }

    private boolean handleCancel(Player player) {
        if (queueManager.isInMatch(player.getUniqueId())) {
            MessageUtil.sendError(player, "試合中はキャンセルできません。");
            return true;
        }

        boolean wasInQueue = queueManager.leaveQueue(player.getUniqueId());
        if (wasInQueue) {
            MessageUtil.sendSuccess(player, "キューから離脱しました。");
        } else {
            MessageUtil.sendInfo(player, "キューに参加していません。");
        }
        return true;
    }

    private boolean handleStatus(Player player) {
        if (queueManager.isInMatch(player.getUniqueId())) {
            MessageUtil.sendInfo(player, "ステータス: §c試合中");
        } else if (queueManager.isInQueue(player.getUniqueId())) {
            int pos = queueManager.getQueuePosition(player.getUniqueId());
            MessageUtil.sendInfo(player, "ステータス: §eキュー待機中 §7(位置: " + pos + ")");
        } else {
            MessageUtil.sendInfo(player, "ステータス: §aロビー");
        }
        MessageUtil.sendInfo(player, "ソロキュー: §f" + queueManager.getSoloQueueSize() + "人"
                + " §8| §7アクティブマッチ: §f" + queueManager.getActiveMatchCount());
        return true;
    }

    private void sendUsage(Player player) {
        MessageUtil.send(player, "§6/rank コマンド一覧:");
        player.sendMessage("  §e/rank solo §7- ソロランクマッチに参加");
        player.sendMessage("  §e/rank practice §7- プラクティス（RP変動なし）");
        player.sendMessage("  §e/rank cancel §7- キューから離脱");
        player.sendMessage("  §e/rank status §7- 現在のステータス");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("solo", "practice", "cancel", "status"));
        }
        String prefix = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }
}
