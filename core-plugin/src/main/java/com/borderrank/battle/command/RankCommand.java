package com.borderrank.battle.command;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.model.WeaponRP;
import com.borderrank.battle.model.WeaponType;
import com.borderrank.battle.model.Team;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RankCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrorMessage(sender, "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendInfoMessage(player, "Usage: /rank <solo|team|cancel|stats|top>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "solo" -> handleSolo(player);
            case "team" -> handleTeam(player);
            case "cancel" -> handleCancel(player);
            case "stats" -> handleStats(player, args);
            case "top" -> handleTop(player, args);
            default -> MessageUtil.sendErrorMessage(player, "Unknown subcommand: " + subcommand);
        }

        return true;
    }

    private void handleSolo(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (!plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            plugin.getQueueManager().addToSoloQueue(player.getUniqueId());
            MessageUtil.sendSuccessMessage(player, "\u00a7eソロキュー\u00a7aに参加しました！対戦相手を待っています...");
        } else {
            MessageUtil.sendErrorMessage(player, "既にキューに参加しています！");
        }
    }

    private void handleTeam(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        Team team = rankManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            MessageUtil.sendErrorMessage(player, "チームに所属していません！");
            return;
        }

        if (!team.getLeaderId().equals(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "チームキューはリーダーのみ登録できます！");
            return;
        }

        if (team.size() < 2) {
            MessageUtil.sendErrorMessage(player, "チームには最低2人必要です！");
            return;
        }

        for (UUID memberId : team.getMembers()) {
            if (Bukkit.getPlayer(memberId) == null) {
                MessageUtil.sendErrorMessage(player, "チームメンバー全員がオンラインである必要があります！");
                return;
            }
        }

        plugin.getQueueManager().addToTeamQueue(team);
        MessageUtil.sendSuccessMessage(player, "\u00a7bチームキュー\u00a7aに参加しました！対戦チームを待っています...");

        for (UUID memberId : team.getMembers()) {
            if (!memberId.equals(player.getUniqueId())) {
                Player member = Bukkit.getPlayer(memberId);
                if (member != null) {
                    MessageUtil.sendInfoMessage(member, "\u00a7eチームがランクマッチキューに参加しました！");
                }
            }
        }
    }

    private void handleCancel(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (plugin.getQueueManager().removePlayer(player.getUniqueId())) {
            MessageUtil.sendSuccessMessage(player, "キューから離脱しました。");
        } else {
            MessageUtil.sendErrorMessage(player, "キューに参加していません！");
        }
    }

    private void handleStats(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        Player targetPlayer = player;

        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                MessageUtil.sendErrorMessage(player, "プレイヤーが見つかりません: " + args[1]);
                return;
            }
        }

        RankManager rankManager = plugin.getRankManager();
        BRBPlayer brPlayer = rankManager.getPlayer(targetPlayer.getUniqueId());

        if (brPlayer == null) {
            MessageUtil.sendErrorMessage(player, "データが見つかりません: " + targetPlayer.getName());
            return;
        }

        String rankTier = rankManager.getHighestRankTier(brPlayer);
        String rankColor = getRankColor(rankTier);

        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------\u00a7r \u00a7b" + targetPlayer.getName() + " \u00a7fのステータス \u00a78\u00a7m----------");
        MessageUtil.sendInfoMessage(player, "\u00a7e\u25c6 総合ランク: " + rankColor + rankTier + "\u00a7eランク");
        MessageUtil.sendInfoMessage(player, "\u00a7e\u25c6 総合RP: \u00a7f" + brPlayer.getTotalRP() + " RP");

        int totalWins = brPlayer.getTotalWins();
        int totalLosses = brPlayer.getTotalLosses();
        int totalGames = totalWins + totalLosses;
        String winRate = totalGames > 0 ? String.format("%.1f%%", (double) totalWins / totalGames * 100) : "-";
        MessageUtil.sendInfoMessage(player, "\u00a7e\u25c6 総合戦績: \u00a7a" + totalWins + "\u00a7f勝 \u00a7c" + totalLosses + "\u00a7f敗 \u00a77(" + winRate + ")");

        MessageUtil.sendInfoMessage(player, "");
        MessageUtil.sendInfoMessage(player, "\u00a7e\u25b6 武器別ステータス:");

        for (WeaponType wt : WeaponType.values()) {
            WeaponRP wrp = brPlayer.getWeaponRP(wt);
            if (wrp != null) {
                int rp = wrp.getRp();
                int wins = wrp.getWins();
                int losses = wrp.getLosses();
                int games = wins + losses;
                String weaponWinRate = games > 0 ? String.format("%.1f%%", (double) wins / games * 100) : "-";
                String weaponRankColor = getRankColorByRP(rp);

                MessageUtil.sendInfoMessage(player, "  \u00a7f" + getWeaponDisplayName(wt) + ": "
                    + weaponRankColor + rp + " RP \u00a77| \u00a7a" + wins + "\u00a77勝 \u00a7c" + losses + "\u00a77敗 \u00a78(" + weaponWinRate + ")");
            }
        }

        Team team = rankManager.getPlayerTeam(targetPlayer.getUniqueId());
        if (team != null) {
            MessageUtil.sendInfoMessage(player, "");
            MessageUtil.sendInfoMessage(player, "\u00a7e\u25b6 チーム: \u00a7b" + team.getName() + " \u00a77(メンバー" + team.size() + "人)");
        }

        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m--------------------------------------");
    }

    private void handleTop(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        if (args.length > 1) {
            String weaponArg = args[1].toUpperCase();
            try {
                WeaponType weaponType = WeaponType.valueOf(weaponArg);
                showWeaponRanking(player, rankManager, weaponType);
                return;
            } catch (IllegalArgumentException e) {
                // Not a weapon type, show global
            }
        }

        List<BRBPlayer> topPlayers = rankManager.getGlobalTopPlayers(10);

        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------\u00a7r \u00a7e\u00a7l\u2606 総合ランキング TOP10 \u2606 \u00a78\u00a7m----------");

        if (topPlayers.isEmpty()) {
            MessageUtil.sendInfoMessage(player, "\u00a77ランキングデータがありません。");
        } else {
            int rank = 1;
            for (BRBPlayer brPlayer : topPlayers) {
                String rankTier = rankManager.getHighestRankTier(brPlayer);
                String rankColor = getRankColor(rankTier);
                String posColor = getPositionColor(rank);
                int totalRP = brPlayer.getTotalRP();
                int wins = brPlayer.getTotalWins();
                int losses = brPlayer.getTotalLosses();

                MessageUtil.sendInfoMessage(player, posColor + "#" + rank + " \u00a7f" + brPlayer.getPlayerName()
                    + " " + rankColor + rankTier + " \u00a77| \u00a7f" + totalRP + " RP \u00a77| \u00a7a" + wins + "\u00a77W \u00a7c" + losses + "\u00a77L");
                rank++;
            }
        }

        MessageUtil.sendInfoMessage(player, "");
        MessageUtil.sendInfoMessage(player, "\u00a77武器別: \u00a7a/rank top attacker \u00a77| \u00a7a/rank top shooter \u00a77| \u00a7a/rank top sniper");
        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------------------------------------------");
    }

    private void showWeaponRanking(Player player, RankManager rankManager, WeaponType weaponType) {
        List<BRBPlayer> topPlayers = rankManager.getTopPlayers(weaponType, 10);

        String weaponName = getWeaponDisplayName(weaponType);
        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------\u00a7r \u00a7e\u00a7l\u2606 " + weaponName + " \u00a7e\u00a7lランキング TOP10 \u2606 \u00a78\u00a7m----------");

        if (topPlayers.isEmpty()) {
            MessageUtil.sendInfoMessage(player, "\u00a77ランキングデータがありません。");
        } else {
            int rank = 1;
            for (BRBPlayer brPlayer : topPlayers) {
                WeaponRP wrp = brPlayer.getWeaponRP(weaponType);
                int rp = wrp != null ? wrp.getRp() : 0;
                int wins = wrp != null ? wrp.getWins() : 0;
                int losses = wrp != null ? wrp.getLosses() : 0;
                String posColor = getPositionColor(rank);
                String rpColor = getRankColorByRP(rp);

                MessageUtil.sendInfoMessage(player, posColor + "#" + rank + " \u00a7f" + brPlayer.getPlayerName()
                    + " " + rpColor + rp + " RP \u00a77| \u00a7a" + wins + "\u00a77W \u00a7c" + losses + "\u00a77L");
                rank++;
            }
        }

        MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------------------------------------------");
    }

    private String getWeaponDisplayName(WeaponType type) {
        return switch (type) {
            case ATTACKER -> "\u00a7cAttacker";
            case SHOOTER -> "\u00a7eShooter";
            case SNIPER -> "\u00a7bSniper";
        };
    }

    private String getRankColor(String rankTier) {
        return switch (rankTier) {
            case "S" -> "\u00a76\u00a7l";
            case "A" -> "\u00a7c";
            case "B" -> "\u00a7b";
            case "C" -> "\u00a7a";
            default -> "\u00a77";
        };
    }

    private String getRankColorByRP(int rp) {
        if (rp >= 5000) return "\u00a76\u00a7l";
        if (rp >= 3000) return "\u00a7c";
        if (rp >= 1500) return "\u00a7b";
        if (rp >= 500) return "\u00a7a";
        return "\u00a77";
    }

    private String getPositionColor(int position) {
        return switch (position) {
            case 1 -> "\u00a76\u00a7l";
            case 2 -> "\u00a7f\u00a7l";
            case 3 -> "\u00a76";
            default -> "\u00a77";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("solo");
            completions.add("team");
            completions.add("cancel");
            completions.add("stats");
            completions.add("top");
        } else if (args.length == 2) {
            if ("stats".equalsIgnoreCase(args[0])) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    completions.add(onlinePlayer.getName());
                }
            } else if ("top".equalsIgnoreCase(args[0])) {
                completions.add("attacker");
                completions.add("shooter");
                completions.add("sniper");
            }
        }

        return completions;
    }
}
