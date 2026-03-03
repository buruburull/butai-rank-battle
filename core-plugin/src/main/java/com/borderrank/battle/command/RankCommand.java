package com.borderrank.battle.command;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Command handler for /rank command.
 * Allows players to manage their ranking and view statistics.
 */
public class RankCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrorMessage(sender, "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendInfoMessage(player, "Usage: /rank <solo|cancel|stats|top>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "solo" -> handleSolo(player);
            case "cancel" -> handleCancel(player);
            case "stats" -> handleStats(player, args);
            case "top" -> handleTop(player, args);
            default -> MessageUtil.sendErrorMessage(player, "Unknown subcommand: " + subcommand);
        }

        return true;
    }

    /**
     * Handle /rank solo command - adds player to solo queue.
     */
    private void handleSolo(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (plugin.getQueueManager().addPlayer(player.getUniqueId(), "solo")) {
            MessageUtil.sendSuccessMessage(player, "Added to solo queue! Waiting for opponents...");
        } else {
            MessageUtil.sendErrorMessage(player, "You are already in a queue!");
        }
    }

    /**
     * Handle /rank cancel command - removes player from queue.
     */
    private void handleCancel(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (plugin.getQueueManager().removePlayer(player.getUniqueId())) {
            MessageUtil.sendSuccessMessage(player, "Removed from queue.");
        } else {
            MessageUtil.sendErrorMessage(player, "You are not in a queue!");
        }
    }

    /**
     * Handle /rank stats command - shows player's ranking points by weapon type.
     */
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

        // Display stats
        MessageUtil.sendInfoMessage(player, "=== Ranking Stats for " + targetPlayer.getName() + " ===");
        MessageUtil.sendInfoMessage(player, "Overall Rank: " + brPlayer.getRankClass());
        
        // Display weapon-type stats (placeholder for weapon types)
        MessageUtil.sendInfoMessage(player, "Assault Rifle RP: " + brPlayer.getRPByWeapon("ASSAULT_RIFLE"));
        MessageUtil.sendInfoMessage(player, "Sniper RP: " + brPlayer.getRPByWeapon("SNIPER"));
        MessageUtil.sendInfoMessage(player, "Trigger RP: " + brPlayer.getRPByWeapon("TRIGGER"));
    }

    /**
     * Handle /rank top command - shows top 10 ranking.
     */
    private void handleTop(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        String weaponType = args.length > 1 ? args[1].toUpperCase() : "OVERALL";

        List<BRBPlayer> topPlayers = rankManager.getTopRanked(weaponType, 10);

        if (topPlayers.isEmpty()) {
            MessageUtil.sendInfoMessage(player, "No ranking data available.");
            return;
        }

        MessageUtil.sendInfoMessage(player, "=== Top 10 Rankings (" + weaponType + ") ===");
        int rank = 1;
        for (BRBPlayer brPlayer : topPlayers) {
            int rp = "OVERALL".equals(weaponType) ? brPlayer.getTotalRP() : brPlayer.getRPByWeapon(weaponType);
            MessageUtil.sendInfoMessage(player, "#" + rank + " " + brPlayer.getPlayerName() + " - " + rp + " RP");
            rank++;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("solo");
            completions.add("cancel");
            completions.add("stats");
            completions.add("top");
        } else if (args.length == 2) {
            if ("top".equalsIgnoreCase(args[0])) {
                completions.add("ASSAULT_RIFLE");
                completions.add("SNIPER");
                completions.add("TRIGGER");
                completions.add("OVERALL");
            } else if ("stats".equalsIgnoreCase(args[0])) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
