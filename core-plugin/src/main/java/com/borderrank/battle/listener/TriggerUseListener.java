package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.TrionManager;
import com.borderrank.battle.model.Trigger;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener for trigger usage events.
 * Handles right-click trigger activation and F key loadout swapping.
 */
public class TriggerUseListener implements Listener {

    private final Map<UUID, Long> triggerCooldowns = new HashMap<>();
    private static final long TRIGGER_COOLDOWN_MS = 500; // 500ms cooldown between trigger uses

    /**
     * Called when a player interacts (right-click).
     * Handles support trigger activation.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Only handle right-click with item
        if (!event.getAction().name().contains("RIGHT")) {
            return;
        }

        if (event.getItem() == null) {
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        
        // Check if player is in match
        if (!isInMatch(player)) {
            return;
        }

        // Check cooldown
        if (isOnCooldown(player.getUniqueId())) {
            MessageUtil.sendErrorMessage(player, "Trigger on cooldown!");
            return;
        }

        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        TrionManager trionManager = plugin.getTrionManager();

        // Get current main slot trigger
        java.util.List<Trigger> loadout = loadoutManager.getLoadout(player.getUniqueId());
        int activeSlot = getActiveSlot(player); // 0-3 for main, 4-7 for sub

        if (activeSlot < 0 || activeSlot >= loadout.size() || loadout.get(activeSlot) == null) {
            return;
        }

        Trigger trigger = loadout.get(activeSlot);

        // Check trion sufficiency
        int currentTrion = trionManager.getTrion(player.getUniqueId());
        if (currentTrion < trigger.getTrionConsumption()) {
            MessageUtil.sendErrorMessage(player, "Insufficient trion! Need " + trigger.getTrionConsumption() + 
                    " but you have " + currentTrion);
            return;
        }

        // Handle specific triggers
        boolean used = false;
        switch (trigger.getId().toUpperCase()) {
            case "GRASSHOPPER" -> used = handleGrasshopper(player, trigger, trionManager);
            case "TELEPORTER" -> used = handleTeleporter(player, trigger, trionManager);
            case "ESCUDO" -> used = handleEscudo(player, trigger, trionManager);
        }

        if (used) {
            setCooldown(player.getUniqueId());
        }

        event.setCancelled(true);
    }

    /**
     * Called when player presses F key (swap hand items).
     * Swaps between main and sub trigger loadouts.
     */
    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        BRBPlugin plugin = BRBPlugin.getInstance();
        
        if (!isInMatch(player)) {
            return;
        }

        // Swap between main slots (0-3) and sub slots (4-7)
        // This would typically modify hotbar layout
        event.setCancelled(true);
        MessageUtil.sendInfoMessage(player, "Trigger loadout swapped!");
    }

    /**
     * Handle Grasshopper trigger - launches player upward.
     */
    private boolean handleGrasshopper(Player player, Trigger trigger, TrionManager trionManager) {
        int trionCost = trigger.getTrionConsumption();
        
        if (trionManager.consumeTrion(player.getUniqueId(), trionCost)) {
            // Launch player upward
            Vector velocity = player.getVelocity();
            velocity.setY(2.0); // Launch velocity
            player.setVelocity(velocity);
            
            MessageUtil.sendSuccessMessage(player, "Grasshopper activated!");
            return true;
        }

        return false;
    }

    /**
     * Handle Teleporter trigger - raycasts and teleports.
     */
    private boolean handleTeleporter(Player player, Trigger trigger, TrionManager trionManager) {
        int trionCost = trigger.getTrionConsumption();
        
        if (trionManager.consumeTrion(player.getUniqueId(), trionCost)) {
            // Raycast 15 blocks
            RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), 
                player.getLocation().getDirection(), 
                15.0
            );

            if (result != null && result.getHitBlock() != null) {
                Block hitBlock = result.getHitBlock();
                Location teleportLoc = hitBlock.getLocation().add(0.5, 1.0, 0.5);
                player.teleport(teleportLoc);
                MessageUtil.sendSuccessMessage(player, "Teleported!");
                return true;
            } else {
                // Teleport to max distance
                Location teleportLoc = player.getEyeLocation()
                    .add(player.getLocation().getDirection().multiply(15));
                player.teleport(teleportLoc);
                MessageUtil.sendSuccessMessage(player, "Teleported!");
                return true;
            }
        }

        return false;
    }

    /**
     * Handle Escudo trigger - places protective wall blocks.
     */
    private boolean handleEscudo(Player player, Trigger trigger, TrionManager trionManager) {
        int trionCost = trigger.getTrionConsumption();
        
        if (trionManager.consumeTrion(player.getUniqueId(), trionCost)) {
            // Place wall of glass blocks
            Location centerLoc = player.getLocation().add(player.getLocation().getDirection().multiply(3));
            
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 2; y++) {
                    Block block = centerLoc.add(x, y, 0).getBlock();
                    if (block.getType() == Material.AIR) {
                        block.setType(Material.GLASS);
                    }
                }
            }

            MessageUtil.sendSuccessMessage(player, "Escudo wall created!");
            
            // Schedule removal after 10 seconds
            BRBPlugin plugin = BRBPlugin.getInstance();
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                // Remove the wall (in a real implementation, track created blocks)
                MessageUtil.sendInfoMessage(player, "Escudo wall expired!");
            }, 200L); // 10 seconds * 20 ticks/sec

            return true;
        }

        return false;
    }

    /**
     * Check if player is in a match.
     */
    private boolean isInMatch(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        return plugin.getMatchManager().isInMatch(player.getUniqueId());
    }

    /**
     * Get the active trigger slot (0-7).
     */
    private int getActiveSlot(Player player) {
        // For now, return first main slot
        // In a full implementation, this would track player's selected slot
        return 0;
    }

    /**
     * Check if trigger is on cooldown.
     */
    private boolean isOnCooldown(UUID playerId) {
        Long lastUse = triggerCooldowns.get(playerId);
        if (lastUse == null) {
            return false;
        }
        return System.currentTimeMillis() - lastUse < TRIGGER_COOLDOWN_MS;
    }

    /**
     * Set trigger cooldown for player.
     */
    private void setCooldown(UUID playerId) {
        triggerCooldowns.put(playerId, System.currentTimeMillis());
    }
}
