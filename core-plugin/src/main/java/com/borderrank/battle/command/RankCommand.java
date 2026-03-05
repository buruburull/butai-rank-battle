package com.borderrank.battle.command;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.model.Team;
import com.borderrank.battle.model.WeaponRP;
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
        if (plugin.getMatchManager().isInMatch(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "既にマッチ中です！");
            return;
        }
        if (!plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            plugin.getQueueManager().addToSoloQueue(player.getUniqueId());
            MessageUtil.sendSuccessMessage(player, "ソロキューに追加しました！対戦相手を待っています...");
        } else {
            MessageUtil.sendErrorMessage(player, "既にキューに入っています！");
        }
    }

    private void handleTeam(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        if (plugin.getMatchManager().isInMatch(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "既にマッチ中です！");
            return;
        }
        if (plugin.getQueueManager().isInQueue(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "既にキューに入っています！");
            return;
        }

        Team team = rankManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            MessageUtil.sendErrorMessage(player, "チームに所属していません！/team create <名前> でチームを作成してください。");
            return;
        }
        if (!team.isLeader(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "チームリーダーのみキューに参加できます！");
            return;
        }

        Set<UUID> members = team.getMembers();
        if (members.size() < 2) {
            MessageUtil.sendErrorMessage(player, "チームに最低2人必要です！/team invite <プレイヤー> でメンバーを招待してください。");
            return;
        }

        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null || !member.isOnline()) {
                MessageUtil.sendErrorMessage(player, "チームメンバー全員がオンラインである必要があります！");
                return;
            }
            if (plugin.getMatchManager().isInMatch(memberId)) {
                MessageUtil.sendErrorMessage(player, member.getName() + " は既にマッチ中です！");
                return;
            }
        }

        int teamId = plugin.getQueueManager().addToTeamQueue(members);
        for (UUID memberId : members) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                MessageUtil.sendSuccessMessage(member, "チーム「" + team.getName() + "」でチームキューに参加しました！対戦チームを待っています...");
            }
        }
    }

    private void handleCancel(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (plugin.getQueueManager().removePlayer(player.getUniqueId())) {
            MessageUtil.sendSuccessMessage(player, "キューから離脱しました。");
        } else {
            MessageUtil.sendErrorMessage(player, "キューに入っていません！");
        }
    }

    private void handleStats(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        Player targetPlayer = player;
        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                MessageUtil.sendErrorMessage(player, "Player not found: " + args[1]);
                return;
            }
        }
        RankManager rankManager = plugin.getRankManager();
        BRBPlayer brPlayer = rankManager.getPlayer(targetPlayer.getUniqueId());
        if (brPlayer == null) {
            MessageUtil.sendErrorMessage(player, "No data found for player: " + targetPlayer.getName());
            return;
        }
        MessageUtil.sendInfoMessage(player, "=== " + targetPlayer.getName() + " のランク情報 ===");
        MessageUtil.sendInfoMessage(player, "ランク: " + rankManager.getHighestRankTier(brPlayer));
        var weaponRPs = brPlayer.getWeaponRPs();
        for (var entry : weaponRPs.entrySet()) {
            var wrp = entry.getValue();
            MessageUtil.sendInfoMessage(player, entry.getKey().name() + " RP: " + wrp.getRp() + " (" + wrp.getWins() + "勝 " + wrp.getLosses() + "敗)");
        }
    }

    private void handleTop(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        List<BRBPlayer> topPlayers = rankManager.getGlobalTopPlayers(10);
        if (topPlayers.isEmpty()) {
            MessageUtil.sendInfoMessage(player, "ランキングデータがありません。");
            return;
        }
        MessageUtil.sendInfoMessage(player, "=== トップ10ランキング ===");
        int rank = 1;
        for (BRBPlayer brPlayer : topPlayers) {
            int rp = brPlayer.getTotalRP();
            MessageUtil.sendInfoMessage(player, "#" + rank + " " + brPlayer.getPlayerName() + " - " + rp + " RP");
            rank++;
        }
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
        } else if (args.length == 2 && "stats".equalsIgnoreCase(args[0])) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        return completions;
    }
}
