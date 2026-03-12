package com.butai.rankbattle.command;

import com.butai.rankbattle.manager.RankManager;
import com.butai.rankbattle.model.BRBPlayer;
import com.butai.rankbattle.model.Team;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles /team commands: create, invite, accept, deny, leave, info
 */
public class TeamCommand implements CommandExecutor, TabCompleter {

    private final RankManager rankManager;

    public TeamCommand(RankManager rankManager) {
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
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "accept" -> handleAccept(player);
            case "deny" -> handleDeny(player);
            case "leave" -> handleLeave(player);
            case "info" -> handleInfo(player, args);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendError(player, "使用法: /team create <チーム名>");
            return true;
        }

        String teamName = args[1];
        String error = rankManager.createTeam(player.getUniqueId(), teamName);
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        MessageUtil.sendSuccess(player, "チーム §f" + teamName + " §aを作成しました！");
        MessageUtil.sendInfo(player, "メンバー招待: §e/team invite <プレイヤー名>");
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendError(player, "使用法: /team invite <プレイヤー名>");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            MessageUtil.sendError(player, "プレイヤー '" + args[1] + "' がオンラインではありません。");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            MessageUtil.sendError(player, "自分自身を招待することはできません。");
            return true;
        }

        String error = rankManager.invitePlayer(player.getUniqueId(), target.getUniqueId());
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        Team team = rankManager.getTeam(player.getUniqueId());
        String teamName = team != null ? team.getName() : "???";

        MessageUtil.sendSuccess(player, target.getName() + " §aに招待を送りました。");
        MessageUtil.send(target, "§6§l[チーム招待] §f" + player.getName() + " §7からチーム §f"
                + teamName + " §7への招待が届きました。");
        target.sendMessage("  §a/team accept §7- 承認  §c/team deny §7- 拒否");
        return true;
    }

    private boolean handleAccept(Player player) {
        if (!rankManager.hasPendingInvite(player.getUniqueId())) {
            MessageUtil.sendError(player, "保留中の招待がありません。");
            return true;
        }

        String teamName = rankManager.getPendingInviteTeamName(player.getUniqueId());
        String error = rankManager.acceptInvite(player.getUniqueId());
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        MessageUtil.sendSuccess(player, "チーム §f" + teamName + " §aに参加しました！");

        // Notify team members
        Team team = rankManager.getTeam(player.getUniqueId());
        if (team != null) {
            for (UUID memberUuid : team.getMembers()) {
                if (memberUuid.equals(player.getUniqueId())) continue;
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null) {
                    MessageUtil.sendInfo(member, player.getName() + " §7がチームに参加しました！");
                }
            }
        }
        return true;
    }

    private boolean handleDeny(Player player) {
        String teamName = rankManager.getPendingInviteTeamName(player.getUniqueId());
        String error = rankManager.denyInvite(player.getUniqueId());
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        MessageUtil.sendInfo(player, "チーム §f" + teamName + " §7からの招待を拒否しました。");
        return true;
    }

    private boolean handleLeave(Player player) {
        Team team = rankManager.getTeam(player.getUniqueId());
        if (team == null) {
            MessageUtil.sendError(player, "チームに所属していません。");
            return true;
        }

        String teamName = team.getName();
        boolean wasLeader = team.isLeader(player.getUniqueId());

        // Notify members before leaving
        for (UUID memberUuid : team.getMembers()) {
            if (memberUuid.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null) {
                MessageUtil.sendInfo(member, player.getName() + " §7がチームから離脱しました。");
                if (wasLeader && team.getMemberCount() > 1) {
                    MessageUtil.sendInfo(member, "§eリーダーが自動的に移譲されます。");
                }
            }
        }

        String error = rankManager.leaveTeam(player.getUniqueId());
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        MessageUtil.sendSuccess(player, "チーム §f" + teamName + " §aから離脱しました。");
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        Team team;
        if (args.length >= 2) {
            team = rankManager.getTeamByName(args[1]);
            if (team == null) {
                MessageUtil.sendError(player, "チーム '" + args[1] + "' が見つかりません。");
                return true;
            }
        } else {
            team = rankManager.getTeam(player.getUniqueId());
            if (team == null) {
                MessageUtil.sendError(player, "チームに所属していません。チーム名を指定してください: /team info <名前>");
                return true;
            }
        }

        MessageUtil.send(player, "§6§l===== チーム情報 =====");
        MessageUtil.send(player, "§fチーム名: §e" + team.getName());

        Player leader = Bukkit.getPlayer(team.getLeaderId());
        String leaderName = leader != null ? leader.getName() : team.getLeaderId().toString().substring(0, 8);
        MessageUtil.send(player, "§fリーダー: §a" + leaderName);
        MessageUtil.send(player, "§fメンバー: §7(" + team.getMemberCount() + "/4)");

        for (UUID memberUuid : team.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            String memberName = member != null ? member.getName() : memberUuid.toString().substring(0, 8);
            boolean online = member != null && member.isOnline();
            String role = team.isLeader(memberUuid) ? "§6[リーダー] " : "  ";
            String status = online ? "§a●" : "§c○";

            BRBPlayer data = rankManager.getPlayer(memberUuid);
            String rankInfo = data != null ? " " + data.getRankClass().getColoredName() : "";

            MessageUtil.send(player, "  " + status + " " + role + "§f" + memberName + rankInfo);
        }

        MessageUtil.send(player, "§6§l======================");
        return true;
    }

    private void sendUsage(Player player) {
        MessageUtil.send(player, "§6/team コマンド一覧:");
        player.sendMessage("  §e/team create <名前> §7- チームを作成（B級以上）");
        player.sendMessage("  §e/team invite <プレイヤー> §7- メンバーを招待");
        player.sendMessage("  §e/team accept §7- 招待を承認");
        player.sendMessage("  §e/team deny §7- 招待を拒否");
        player.sendMessage("  §e/team leave §7- チームから離脱");
        player.sendMessage("  §e/team info [名前] §7- チーム情報を表示");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("create", "invite", "accept", "deny", "leave", "info"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("invite".equals(sub)) {
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
