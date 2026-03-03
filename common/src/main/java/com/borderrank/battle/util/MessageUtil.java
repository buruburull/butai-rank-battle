package com.borderrank.battle.util;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utility class for handling chat messages and formatting in the Border Rank Battle plugin.
 */
public final class MessageUtil {
    private static final String PREFIX = ChatColor.GOLD + "[BRB] " + ChatColor.RESET;

    /**
     * Gets the plugin prefix for messages.
     *
     * @return the formatted prefix
     */
    public static String prefix() {
        return PREFIX;
    }

    /**
     * Sends a prefixed message to a player.
     *
     * @param player the player to send the message to
     * @param message the message content
     */
    public static void send(Player player, String message) {
        if (player == null) {
            return;
        }
        player.sendMessage(PREFIX + message);
    }

    /**
     * Sends an error message to a player in red.
     *
     * @param player the player to send the message to
     * @param message the error message content
     */
    public static void sendError(Player player, String message) {
        if (player == null) {
            return;
        }
        player.sendMessage(PREFIX + ChatColor.RED + message + ChatColor.RESET);
    }

    /**
     * Broadcasts a message to all players on the server.
     *
     * @param message the message to broadcast
     */
    public static void broadcast(String message) {
        Bukkit.broadcastMessage(PREFIX + message);
    }

    /**
     * Broadcasts an error message to all players on the server in red.
     *
     * @param message the error message to broadcast
     */
    public static void broadcastError(String message) {
        Bukkit.broadcastMessage(PREFIX + ChatColor.RED + message + ChatColor.RESET);
    }

    /**
     * Formats an RP value with color coding based on the value.
     * Colors:
     * - Green for RP > 1500
     * - Yellow for RP > 1000
     * - Red for RP <= 1000
     *
     * @param rp the RP value
     * @return the formatted RP string with color
     */
    public static String formatRP(int rp) {
        ChatColor color;
        if (rp > 1500) {
            color = ChatColor.GREEN;
        } else if (rp > 1000) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.RED;
        }
        return color + String.format("%,d", rp) + ChatColor.RESET;
    }

    /**
     * Formats a value with thousands separators.
     *
     * @param value the value to format
     * @return the formatted string
     */
    public static String formatNumber(int value) {
        return String.format("%,d", value);
    }

    /**
     * Formats a win rate percentage with color coding.
     *
     * @param winRate the win rate as a percentage (0-100)
     * @return the formatted win rate string with color
     */
    public static String formatWinRate(double winRate) {
        ChatColor color;
        if (winRate >= 55) {
            color = ChatColor.GREEN;
        } else if (winRate >= 45) {
            color = ChatColor.YELLOW;
        } else {
            color = ChatColor.RED;
        }
        return color + String.format("%.1f%%", winRate) + ChatColor.RESET;
    }

    /**
     * Sends a success message to a player in green.
     *
     * @param player the player to send the message to
     * @param message the success message content
     */
    public static void sendSuccess(Player player, String message) {
        if (player == null) {
            return;
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + message + ChatColor.RESET);
    }

    /**
     * Sends an info message to a player in blue.
     *
     * @param player the player to send the message to
     * @param message the info message content
     */
    public static void sendInfo(Player player, String message) {
        if (player == null) {
            return;
        }
        player.sendMessage(PREFIX + ChatColor.AQUA + message + ChatColor.RESET);
    }

    /**
     * Sends a warning message to a player in yellow.
     *
     * @param player the player to send the message to
     * @param message the warning message content
     */
    public static void sendWarning(Player player, String message) {
        if (player == null) {
            return;
        }
        player.sendMessage(PREFIX + ChatColor.YELLOW + message + ChatColor.RESET);
    }

    /**
     * Creates a centered message for display.
     *
     * @param message the message to center
     * @return the centered message string
     */
    public static String centerMessage(String message) {
        int center = (int) ((49.6 - ChatColor.stripColor(message).length()) / 2);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < center; i++) {
            sb.append(" ");
        }
        return sb.append(message).toString();
    }

    private MessageUtil() {
        throw new AssertionError("Utility class cannot be instantiated");
    }
}
