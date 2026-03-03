package com.borderrank.battle.command;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.manager.TriggerRegistry;
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
 * Command handler for /bradmin command.
 * Admin-only commands for server management.
 * Permission: brb.admin
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("brb.admin")) {
            MessageUtil.sendErrorMessage(sender, "You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendInfoMessage(sender, "Usage: /bradmin <trigger|forcestart|rp|season>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "trigger" -> handleTrigger(sender, args);
            case "forcestart" -> handleForceStart(sender);
            case "rp" -> handleRP(sender, args);
            case "season" -> handleSeason(sender, args);
            default -> MessageUtil.sendErrorMessage(sender, "Unknown subcommand: " + subcommand);
        }

        return true;
    }

    /**
     * Handle /bradmin trigger command - reloads triggers.yml
     */
    private void handleTrigger(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(sender, "Usage: /bradmin trigger <reload>");
            return;
        }

        if ("reload".equalsIgnoreCase(args[1])) {
            BRBPlugin plugin = BRBPlugin.getInstance();
            TriggerRegistry registry = plugin.getTriggerRegistry();
            
            if (registry.reloadTriggers()) {
                MessageUtil.sendSuccessMessage(sender, "Triggers reloaded!");
            } else {
                MessageUtil.sendErrorMessage(sender, "Failed to reload triggers.");
            }
        } else {
            MessageUtil.sendErrorMessage(sender, "Unknown trigger action: " + args[1]);
        }
    }

    /**
     * Handle /bradmin forcestart command - force starts a match with current queue.
     */
    private void handleForceStart(CommandSender sender) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        
        if (plugin.getQueueManager().startMatch()) {
            MessageUtil.sendSuccessMessage(sender, "Match started!");
        } else {
            MessageUtil.sendErrorMessage(sender, "Not enough players in queue to start a match.");
        }
    }

    /**
     * Handle /bradmin rp command - manually set player RP.
     */
    private void handleRP(CommandSender sender, String[] args) {
        if (args.length < 4) {
            MessageUtil.sendErrorMessage(sender, "Usage: /bradmin rp set <player> <weapon> <value>");
            return;
        }

        if (!"set".equalsIgnoreCase(args[1])) {
            MessageUtil.sendErrorMessage(sender, "Unknown RP action: " + args[1]);
            return;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            MessageUtil.sendErrorMessage(sender, "Player not found: " + args[2]);
            return;
        }

        String weaponType = args[3].toUpperCase();

        int value;
        try {
            value = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            MessageUtil.sendErrorMessage(sender, "Invalid RP value: " + args[4]);
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        
        rankManager.setPlayerRP(targetPlayer.getUniqueId(), weaponType, value);
        MessageUtil.sendSuccessMessage(sender, 
            "Set " + targetPlayer.getName() + "'s " + weaponType + " RP to " + value);
    }

    /**
     * Handle /bradmin season command - manages seasons.
     */
    private void handleSeason(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(sender, "Usage: /bradmin season <start|end> [name]");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        String action = args[1].toLowerCase();

        if ("start".equalsIgnoreCase(action)) {
            if (args.length < 3) {
                MessageUtil.sendErrorMessage(sender, "Usage: /bradmin season start <name>");
                return;
            }

            String seasonName = args[2];
            if (rankManager.startSeason(seasonName)) {
                MessageUtil.sendSuccessMessage(sender, "Season '" + seasonName + "' started!");
            } else {
                MessageUtil.sendErrorMessage(sender, "Failed to start season.");
            }
        } else if ("end".equalsIgnoreCase(action)) {
            if (rankManager.endSeason()) {
                MessageUtil.sendSuccessMessage(sender, "Current season ended!");
            } else {
                MessageUtil.sendErrorMessage(sender, "No active season to end.");
            }
        } else {
            MessageUtil.sendErrorMessage(sender, "Unknown season action: " + action);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("brb.admin")) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("trigger");
            completions.add("forcestart");
            completions.add("rp");
            completions.add("season");
        } else if (args.length == 2) {
            if ("trigger".equalsIgnoreCase(args[0])) {
                completions.add("reload");
            } else if ("rp".equalsIgnoreCase(args[0])) {
                completions.add("set");
            } else if ("season".equalsIgnoreCase(args[0])) {
                completions.add("start");
                completions.add("end");
            }
        } else if (args.length == 3) {
            if ("rp".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 4) {
            if ("rp".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
                completions.add("ASSAULT_RIFLE");
                completions.add("SNIPER");
                completions.add("TRIGGER");
            }
        }

        return completions;
    }
}
