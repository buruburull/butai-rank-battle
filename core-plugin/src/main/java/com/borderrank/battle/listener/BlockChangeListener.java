package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.arena.ArenaInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Listens for block changes during matches and records them in BlockTracker
 * so they can be restored when the match ends.
 */
public class BlockChangeListener implements Listener {

    /**
     * Track blocks destroyed by explosions (Meteora Sub, TNT, etc).
     * EntityExplodeEvent fires BEFORE blocks are actually destroyed,
     * so we can record their original state.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        BRBPlugin plugin = BRBPlugin.getInstance();

        ArenaInstance match = null;

        // Case 1: Entity is a Player (Meteora Sub uses createExplosion with shooter as source)
        if (entity instanceof Player player) {
            match = plugin.getMatchManager().getPlayerMatch(player.getUniqueId());
        }

        // Case 2: Entity is a Projectile (crossbow bolt, etc)
        if (match == null && entity instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                match = plugin.getMatchManager().getPlayerMatch(shooter.getUniqueId());
            }
        }

        // Case 3: Fallback - find match by explosion location within map boundaries
        if (match == null) {
            org.bukkit.Location explosionLoc = event.getLocation();
            for (ArenaInstance m : plugin.getMatchManager().getAllActiveMatches()) {
                if (m.getMapData().isWithinBoundaries(explosionLoc)) {
                    match = m;
                    break;
                }
            }
        }

        if (match == null) return;

        // Record all blocks that will be destroyed
        for (Block block : event.blockList()) {
            match.getBlockTracker().recordBlockChange(block);
        }
    }

    /**
     * Track blocks broken by players during matches.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BRBPlugin plugin = BRBPlugin.getInstance();
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(player.getUniqueId());
        if (match == null) return;

        match.getBlockTracker().recordBlockChange(event.getBlock());
    }

    /**
     * Track blocks placed by players during matches (e.g. Escudo glass walls).
     * Records the original state (usually AIR) so it can be restored.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        BRBPlugin plugin = BRBPlugin.getInstance();
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(player.getUniqueId());
        if (match == null) return;

        // Record the block that was replaced (original state before placement)
        match.getBlockTracker().recordBlockChange(event.getBlockReplacedState().getBlock());
    }
}
