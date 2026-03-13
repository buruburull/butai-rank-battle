package com.butai.rankbattle.listener;

import com.butai.rankbattle.arena.ArenaInstance;
import com.butai.rankbattle.manager.QueueManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.UUID;

/**
 * Tracks block changes during matches for post-match restoration.
 */
public class BlockChangeListener implements Listener {

    private final QueueManager queueManager;

    public BlockChangeListener(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ArenaInstance match = queueManager.getPlayerMatch(player.getUniqueId());
        if (match == null) return;

        Block block = event.getBlock();
        match.trackBlockChange(block.getLocation(), block.getState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ArenaInstance match = queueManager.getPlayerMatch(player.getUniqueId());
        if (match == null) return;

        // Store the replaced block state (what was there before placement)
        match.trackBlockChange(event.getBlock().getLocation(), event.getBlockReplacedState());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.blockList().isEmpty()) return;

        // Find which match this explosion belongs to by checking nearby players
        ArenaInstance match = findMatchForExplosion(event);
        if (match == null) return;

        for (Block block : event.blockList()) {
            match.trackBlockChange(block.getLocation(), block.getState());
        }
    }

    /**
     * Find the match associated with an explosion by checking the explosion location
     * against active match spawns / players.
     */
    private ArenaInstance findMatchForExplosion(EntityExplodeEvent event) {
        // Check all active matches for any player near the explosion
        for (ArenaInstance match : queueManager.getActiveMatches().values()) {
            for (UUID uuid : match.getPlayers()) {
                Player p = org.bukkit.Bukkit.getPlayer(uuid);
                if (p != null && p.getWorld().equals(event.getLocation().getWorld())) {
                    // Same world - assume this match (single-world arena setup)
                    return match;
                }
            }
        }
        return null;
    }
}
