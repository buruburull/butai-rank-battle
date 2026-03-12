package com.butai.rankbattle.command;

import com.butai.rankbattle.manager.QueueManager;
import com.butai.rankbattle.manager.RankManager;
import com.butai.rankbattle.model.BRBPlayer;
import com.butai.rankbattle.model.RankClass;
import com.butai.rankbattle.model.Team;
import com.butai.rankbattle.model.WeaponRP;
import com.butai.rankbattle.model.WeaponType;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles /rank commands: solo, cancel, stats, top, practice, status
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    private final QueueManager queueManager;
    private final RankManager rankManager;

    public RankCommand(QueueManager queueManager, RankManager rankManager) {
        this.queueManager = queueManager;
        this.rankManager = rankManager;
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
            case "team" -> handleTeam(player);
            case "practice" -> handlePractice(player);
            case "cancel" -> handleCancel(player);
            case "status" -> handleStatus(player);
            case "stats" -> handleStats(player, args);
            case "top" -> handleTop(player, args);
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

    private boolean handleTeam(Player player) {
        String error = queueManager.joinTeamQueue(player.getUniqueId());
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        Team team = rankManager.getTeam(player.getUniqueId());
        String teamName = team != null ? team.getName() : "???";

        MessageUtil.sendSuccess(player, "§fチームランク §7キューに参加しました。 §8[§f" + teamName + "§8]");
        MessageUtil.sendInfo(player, "チームキュー内: §f" + queueManager.getTeamQueueSize() + "チーム §7| マッチング待機中...");
        MessageUtil.sendInfo(player, "キャンセル: §e/rank cancel");

        // Notify team members
        if (team != null) {
            for (UUID memberUuid : team.getMembers()) {
                if (memberUuid.equals(player.getUniqueId())) continue;
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null) {
                    MessageUtil.sendInfo(member, "チームリーダー §f" + player.getName() + " §7がチームランクキューに参加しました。");
                }
            }
        }

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

    /**
     * /rank stats [player] - Show player stats
     */
    private boolean handleStats(Player player, String[] args) {
        BRBPlayer data;
        String targetName;

        if (args.length >= 2) {
            // Look up another player
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target != null) {
                data = rankManager.getPlayer(target.getUniqueId());
                targetName = target.getName();
            } else {
                MessageUtil.sendError(player, "プレイヤー '" + args[1] + "' がオンラインではありません。");
                return true;
            }
        } else {
            data = rankManager.getPlayer(player.getUniqueId());
            targetName = player.getName();
        }

        if (data == null) {
            MessageUtil.sendError(player, "プレイヤーデータが見つかりません。");
            return true;
        }

        RankClass rank = data.getRankClass();
        int totalRP = data.getTotalRP();
        int totalWins = data.getTotalWins();
        int totalLosses = data.getTotalLosses();
        int totalMatches = totalWins + totalLosses;
        double winRate = totalMatches > 0 ? (double) totalWins / totalMatches * 100.0 : 0.0;

        MessageUtil.send(player, "§6§l============ 戦績 ============");
        MessageUtil.send(player, "§fプレイヤー: " + rank.getColor() + "[" + rank.getDisplayName() + "] §f" + targetName);
        MessageUtil.send(player, "§f総合RP: §e" + totalRP + " §8| §f戦績: §a" + totalWins + "勝 §c" + totalLosses + "敗"
                + " §8(§7勝率: " + String.format("%.1f", winRate) + "%§8)");
        MessageUtil.send(player, "§6--- 武器タイプ別 ---");

        for (WeaponType type : WeaponType.values()) {
            WeaponRP wrp = data.getWeaponRP(type);
            int matches = wrp.getTotalMatches();
            String wr = matches > 0 ? String.format("%.1f", wrp.getWinRate()) + "%" : "-";
            MessageUtil.send(player, "  " + type.getColor() + type.getDisplayName()
                    + " §fRP: §e" + wrp.getRp()
                    + " §8| §a" + wrp.getWins() + "W §c" + wrp.getLosses() + "L"
                    + " §8(§7" + wr + "§8)");
        }

        // Next rank info
        RankClass nextRank = getNextRank(rank);
        if (nextRank != null) {
            int rpNeeded = nextRank.getRequiredRP() - totalRP;
            MessageUtil.send(player, "§7次のランク: " + nextRank.getColoredName() + " §7まであと §e" + rpNeeded + " RP");
        }

        MessageUtil.send(player, "§6§l==============================");
        return true;
    }

    /**
     * /rank top [weapon] - Show top 10 leaderboard
     */
    private boolean handleTop(Player player, String[] args) {
        if (args.length >= 2) {
            // Weapon-specific top
            WeaponType weapon = WeaponType.fromString(args[1]);
            if (weapon == null) {
                MessageUtil.sendError(player, "無効な武器タイプです。(STRIKER / GUNNER / MARKSMAN)");
                return true;
            }
            showWeaponTop(player, weapon);
        } else {
            // Overall top
            showOverallTop(player);
        }
        return true;
    }

    private void showOverallTop(Player player) {
        List<Map<String, Object>> top = rankManager.getTopPlayers(10);
        MessageUtil.send(player, "§6§l===== 総合ランキング TOP10 =====");

        if (top.isEmpty()) {
            MessageUtil.send(player, "§7データがありません。");
        } else {
            for (int i = 0; i < top.size(); i++) {
                Map<String, Object> row = top.get(i);
                String name = (String) row.get("name");
                String rankStr = (String) row.get("rank_class");
                int totalRp = (int) row.get("total_rp");
                RankClass rc = RankClass.fromString(rankStr);

                String posColor = i == 0 ? "§6" : i <= 2 ? "§f" : "§7";
                MessageUtil.send(player, posColor + " #" + (i + 1) + " "
                        + rc.getColor() + "[" + rc.getDisplayName() + "] §f" + name
                        + " §8- §e" + totalRp + " RP");
            }
        }

        MessageUtil.send(player, "§6§l================================");
    }

    private void showWeaponTop(Player player, WeaponType weapon) {
        List<Map<String, Object>> top = rankManager.getTopByWeapon(weapon.name(), 10);
        MessageUtil.send(player, "§6§l===== " + weapon.getColor() + weapon.getDisplayName()
                + " §6§lランキング TOP10 =====");

        if (top.isEmpty()) {
            MessageUtil.send(player, "§7データがありません。");
        } else {
            for (int i = 0; i < top.size(); i++) {
                Map<String, Object> row = top.get(i);
                String name = (String) row.get("name");
                int rp = (int) row.get("rp");
                int wins = (int) row.get("wins");
                int losses = (int) row.get("losses");

                String posColor = i == 0 ? "§6" : i <= 2 ? "§f" : "§7";
                MessageUtil.send(player, posColor + " #" + (i + 1) + " §f" + name
                        + " §8- §e" + rp + " RP §8(§a" + wins + "W §c" + losses + "L§8)");
            }
        }

        MessageUtil.send(player, "§6§l================================");
    }

    /**
     * Get the next rank above the current one, or null if already S.
     */
    private RankClass getNextRank(RankClass current) {
        return switch (current) {
            case C -> RankClass.B;
            case B -> RankClass.A;
            case A -> RankClass.S;
            case UNRANKED -> RankClass.C;
            default -> null;
        };
    }

    private void sendUsage(Player player) {
        MessageUtil.send(player, "§6/rank コマンド一覧:");
        player.sendMessage("  §e/rank solo §7- ソロランクマッチに参加");
        player.sendMessage("  §e/rank team §7- チームランクマッチに参加（リーダーのみ）");
        player.sendMessage("  §e/rank practice §7- プラクティス（RP変動なし）");
        player.sendMessage("  §e/rank cancel §7- キューから離脱");
        player.sendMessage("  §e/rank status §7- 現在のステータス");
        player.sendMessage("  §e/rank stats [player] §7- 戦績を表示");
        player.sendMessage("  §e/rank top [weapon] §7- ランキングTOP10");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("solo", "team", "practice", "cancel", "status", "stats", "top"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("top".equals(sub)) {
                completions.addAll(List.of("striker", "gunner", "marksman"));
            } else if ("stats".equals(sub)) {
                // Online player names
                for (Player p : Bukkit.getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }
}
