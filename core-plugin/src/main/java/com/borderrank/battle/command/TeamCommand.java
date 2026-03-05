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

/**
 * Command handler for /team command.
 * Manages team creation and membership.
 */
public class TeamCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrorMessage(sender, "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendInfoMessage(player, "Usage: /team <create|invite|leave|info>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "create" -> handleCreate(player, args);
            case "invite" -> handleInvite(player, args);
            case "leave" -> handleLeave(player);
            case "info" -> handleInfo(player, args);
            default -> MessageUtil.sendErrorMessage(player, "Unknown subcommand: " + subcommand);
        }

        return true;
    }

    /**
     * Handle /team create command - creates a new team (requires B rank or higher).
     */
    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(player, "Usage: /team create <name>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        BRBPlayer brPlayer = rankManager.getPlayer(player.getUniqueId());

        if (brPlayer == null) {
            MessageUtil.sendErrorMessage(player, "Your player data was not found.");
            return;
        }

        // Check rank requirement (B rank or higher)
        String rankTier = rankManager.getHighestRankTier(brPlayer);
        if (!isRankBOrHigher(rankTier)) {
            MessageUtil.sendErrorMessage(player, "You need B rank or higher to create a team!");
            return;
        }

        String teamName = args[1];
        Team team = new Team(teamName, player.getUniqueId());

        // Store team (this would typically be saved to database)
        if (rankManager.createTeam(team)) {
            MessageUtil.sendSuccessMessage(player, "Team '" + teamName + "' created!");
        } else {
            MessageUtil.sendErrorMessage(player, "Team name already exists!");
        }
    }

    /**
     * Handle /team invite command - invites a player to the team.
     */
    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(player, "Usage: /team invite <player>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            MessageUtil.sendErrorMessage(player, "Player not found: " + args[1]);
            return;
        }

        Team team = rankManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            MessageUtil.sendErrorMessage(player, "You are not in a team!");
            return;
        }

        if (!team.getLeaderId().equals(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "Only the team leader can invite players!");
            return;
        }

        if (team.addMember(targetPlayer.getUniqueId())) {
            plugin.getRankManager().registerPlayerTeam(targetPlayer.getUniqueId(), team.getName());
            MessageUtil.sendSuccessMessage(player, "Invited " + targetPlayer.getName() + " to the team!");
            MessageUtil.sendSuccessMessage(targetPlayer, "You were invited to join team: " + team.getName());
        } else {
            MessageUtil.sendErrorMessage(player, "Failed to invite player.");
        }
    }

    /**
     * Handle /team leave command - removes player from their team.
     */
    private void handleLeave(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        Team team = rankManager.getPlayerTeam(player.getUniqueId());
        if (team == null) {
            MessageUtil.sendErrorMessage(player, "You are not in a team!");
            return;
        }

        if (team.removeMember(player.getUniqueId())) {
            plugin.getRankManager().unregisterPlayerTeam(player.getUniqueId());
            MessageUtil.sendSuccessMessage(player, "You left the team.");

            // If player was leader and team is now empty, delete team
            if (team.getMembers().isEmpty()) {
                rankManager.deleteTeam(team.getName());
            }
        } else {
            MessageUtil.sendErrorMessage(player, "Failed to leave team.");
        }
    }

    /**
     * Handle /team info command - shows team information.
     */
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
            MessageUtil.sendErrorMessage(player, "Team not found.");
            return;
        }

        MessageUtil.sendInfoMessage(player, "=== Team: " + team.getName() + " ===");
        MessageUtil.sendInfoMessage(player, "Leader: " + Bukkit.getOfflinePlayer(team.getLeaderId()).getName());
        MessageUtil.sendInfoMessage(player, "Members: " + team.getMembers().size());

        int totalRP = 0;
        for (UUID memberId : team.getMembers()) {
            BRBPlayer member = rankManager.getPlayer(memberId);
            if (member != null) {
                totalRP += member.getTotalRP();
            }
        }

        MessageUtil.sendInfoMessage(player, "Team RP: " + totalRP);
    }

    /**
     * Check if a rank is B or higher.
     */
    private boolean isRankBOrHigher(String rankTier) {
        return rankTier.equals("S") || rankTier.equals("A") || rankTier.equals("B");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("create");
            completions.add("invite");
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
