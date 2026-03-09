package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.RankManager;
import com.borderrank.battle.manager.TrionManager;
import com.borderrank.battle.model.BRBPlayer;
import com.borderrank.battle.model.RankClass;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Listener for player connection events.
 * Handles loading and saving player data.
 */
public class PlayerConnectionListener implements Listener {

    /**
     * Called when a player joins the server.
     * Loads player data from database asynchronously.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();

        // Load player data asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BRBPlayer brPlayer = rankManager.getPlayer(uuid);

            if (brPlayer == null) {
                // Create new player
                brPlayer = new BRBPlayer(uuid, player.getName(), RankClass.UNRANKED);
                rankManager.createPlayer(brPlayer);
            } else {
                // Update player name in case of name change
                brPlayer.setPlayerName(player.getName());
            }

            // Cache the player data
            rankManager.cachePlayer(brPlayer);

            // Recalculate rank from current RP
            rankManager.recalculateRank(brPlayer);

            // Load trion data
            TrionManager trionManager = plugin.getTrionManager();
            trionManager.initPlayer(uuid, 1000); // Default trion value

            // Notify player and set tab list name (must run on main thread)
            final BRBPlayer finalBrPlayer = brPlayer;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                rankManager.updateTabListName(player, finalBrPlayer);
                MessageUtil.sendMessage(player, "Welcome to Border Rank Battle!");
                MessageUtil.sendMessage(player, "Use /rank for ranking info, /trigger for loadouts");
            });
        });
    }

    /**
     * Called when a player leaves the server.
     * Saves player data to database asynchronously.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        BRBPlugin plugin = BRBPlugin.getInstance();
        RankManager rankManager = plugin.getRankManager();
        TrionManager trionManager = plugin.getTrionManager();

        // Remove from queue if in queue
        plugin.getQueueManager().removePlayer(uuid);

        // Remove from spectating if spectating
        com.borderrank.battle.arena.ArenaInstance specMatch = plugin.getMatchManager().getSpectatingMatch(uuid);
        if (specMatch != null) {
            specMatch.removeSpectator(uuid);
        }

        // Save player data asynchronously
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            BRBPlayer brPlayer = rankManager.getPlayer(uuid);
            if (brPlayer != null) {
                rankManager.savePlayer(brPlayer);
            }

            // Clean up trion state
            trionManager.removePlayer(uuid);

            // Remove from cache
            rankManager.uncachePlayer(uuid);
        });
    }
}
