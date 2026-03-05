package com.borderrank.battle.command;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.model.BRBPlayer;
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
import java.util.UUID;

public class TeamCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrorMessage(sender, "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendInfoMessage(player, "Usage: /team <create|invite|accept|deny|leave|info>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "leave" -> handleLeave(player);
            case "info" -> handleInfo(player, args);
            default -> MessageUtil.sendErrorMessage(player, "Unknown subcommand: " + subcommand);
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(player, "Usage: /team create <name>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        BRBPlayer brPlayer = rankManager.getPlayer(player.getUniqueId());

        if (brPlayer == null) {
            MessageUtil.sendErrorMessage(player, "Player data not found.");
            return;
        }

        if (rankManager.getPlayerTeam(player.getUniqueId()) != null) {
            MessageUtil.sendErrorMessage(player, "既にチームに所属しています！");
            return;
        }

        String rankTier = rankManager.getHighestRankTier(brPlayer);
        if (!isRankBOrHigher(rankTier)) {
            MessageUtil.sendErrorMessage(player, "チーム作成にはBランク以上が必要です！");
            return;
        }

        String teamName = args[1];
        Team team = new Team(teamName, player.getUniqueId());

        if (rankManager.createTeam(team)) {
            MessageUtil.sendSuccessMessage(player, "チーム '" + teamName + "' を作成しました！");
        } else {
            MessageUtil.sendErrorMessage(player, "そのチーム名は既に使われています！");
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(player, "Usage: /team invite <player>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            MessageUtil.sendErrorMessage(player, "プレイヤーが見つかりません: " + args[1]);
            return;
        }

        Team team = rankManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            MessageUtil.sendErrorMessage(player, "チームに所属していません！");
            return;
        }

        if (!team.getLeaderId().equals(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "リーダーのみ招待できます！");
            return;
        }

        if (rankManager.getPlayerTeam(targetPlayer.getUniqueId()) != null) {
            MessageUtil.sendErrorMessage(player, targetPlayer.getName() + " は既に別のチームに所属しています！");
            return;
        }

        rankManager.addPendingInvite(targetPlayer.getUniqueId(), team.getName());
        MessageUtil.sendSuccessMessage(player, targetPlayer.getName() + " に招待を送りました！");
        MessageUtil.sendInfoMessage(targetPlayer, player.getName() + " からチーム " + team.getName() + " への招待が届きました！");
        MessageUtil.sendInfoMessage(targetPlayer, "/team accept で承諾、/team deny で拒否");
    }

    private void handleAccept(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        if (rankManager.getPlayerTeam(player.getUniqueId()) != null) {
            MessageUtil.sendErrorMessage(player, "既にチームに所属しています！");
            return;
        }

        String teamName = rankManager.consumePendingInvite(player.getUniqueId());
        if (teamName == null) {
            MessageUtil.sendErrorMessage(player, "招待がありません！");
            return;
        }

        Team team = rankManager.getTeamByName(teamName);
        if (team == null) {
            MessageUtil.sendErrorMessage(player, "チームが存在しません！");
            return;
        }

        team.addMember(player.getUniqueId());
        rankManager.registerPlayerTeam(player.getUniqueId(), team.getName());
        MessageUtil.sendSuccessMessage(player, "チーム " + team.getName() + " に参加しました！");

        Player leader = Bukkit.getPlayer(team.getLeaderId());
        if (leader != null) {
            MessageUtil.sendSuccessMessage(leader, player.getName() + " がチームに参加しました！");
        }
    }

    private void handleDeny(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        String teamName = rankManager.consumePendingInvite(player.getUniqueId());
        if (teamName == null) {
            MessageUtil.sendErrorMessage(player, "招待がありません！");
            return;
        }

        MessageUtil.sendInfoMessage(player, "チーム " + teamName + " への招待を拒否しました。");

        Team team = rankManager.getTeamByName(teamName);
        if (team != null) {
            Player leader = Bukkit.getPlayer(team.getLeaderId());
            if (leader != null) {
                MessageUtil.sendErrorMessage(leader, player.getName() + " が招待を拒否しました。");
            }
        }
    }

    private void handleLeave(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        Team team = rankManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            MessageUtil.sendErrorMessage(player, "チームに所属していません！");
            return;
        }

        if (team.removeMember(player.getUniqueId())) {
            rankManager.unregisterPlayerTeam(player.getUniqueId());
            MessageUtil.sendSuccessMessage(player, "チームを脱退しました。");

            if (team.getMembers().isEmpty()) {
                rankManager.deleteTeam(team.getName());
            } else {
                Player leader = Bukkit.getPlayer(team.getLeaderId());
                if (leader != null) {
                    MessageUtil.sendInfoMessage(leader, player.getName() + " がチームを脱退しました。");
                }
            }
        } else {
            MessageUtil.sendErrorMessage(player, "リーダーは脱退できません。");
        }
    }

    private void handleInfo(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        Team team;
        if (args.length > 1) {
            team = rankManager.getTeamByName(args[1]);
       } else {
            team = rankManager.getPlayerTeam(player.getUniqueId());
        }

        if (team == null) {
            MessageUtil.sendErrorMessage(player, "チームが見つかりません。");
            return;
        }

        MessageUtil.sendInfoMessage(player, "=== チーム: " + team.getName() + " ===");
        MessageUtil.sendInfoMessage(player, "リーダー: " + Bukkit.getOfflinePlayer(team.getLeaderId()).getName());
        MessageUtil.sendInfoMessage(player, "メンバー数: " + team.getMembers().size());

        StringBuilder memberList = new StringBuilder();
        for (UUID memberId : team.getMembers()) {
            if (memberList.length() > 0) {
                memberList.append(", ");
            }
            String name = Bukkit.getOfflinePlayer(memberId).getName();
            if (memberId.equals(team.getLeaderId())) {
                memberList.append(name).append("[L]");
            } else {
                memberList.append(name);
            }
        }
        MessageUtil.sendInfoMessage(player, "メンバー: " + memberList);
    }

    private boolean isRankBOrHigher(String rankTier) {
        return rankTier.equals("S") || rankTier.equals("A") || rankTier.equals("B");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("create");
            completions.add("invite");
            completions.add("accept");
            completions.add("deny");
            completions.add("leave");
            completions.add("info");
        } else if (args.length == 2) {
            if ("invite".equalsIgnoreCase(args[0])) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    completions.add(onlinePlayer.getName());
                }
            } else if ("info".equalsIgnoreCase(args[0])) {
                BRBPlugin plugin = BRBPlugin.getInstance();
                RankManager rankManager = plugin.getRankManager();
                completions.addAll(rankManager.getAllTeamNames());
            }
        }

        return completions;
    }
}
