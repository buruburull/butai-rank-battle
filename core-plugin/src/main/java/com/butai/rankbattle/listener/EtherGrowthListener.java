package com.butai.rankbattle.listener;

import com.butai.rankbattle.manager.EtherGrowthManager;
import com.butai.rankbattle.manager.MineManager;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

/**
 * Listens for mining and mob kill events in growth zones.
 * Awards EP (Ether Points) to players for ether cap growth.
 */
public class EtherGrowthListener implements Listener {

    private final EtherGrowthManager growthManager;
    private final MineManager mineManager;

    public EtherGrowthListener(EtherGrowthManager growthManager, MineManager mineManager) {
        this.growthManager = growthManager;
        this.mineManager = mineManager;
    }

    /**
     * Handle ore mining in the mine zone.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location blockLoc = event.getBlock().getLocation();

        // Check if it's a registered ore in the mine zone
        if (!mineManager.isRegisteredOre(blockLoc)) return;

        // Check if it's currently regenerating (cobblestone)
        if (mineManager.isRegenerating(blockLoc)) {
            event.setCancelled(true);
            MessageUtil.sendWarning(player, "この鉱石はまだ再生中です...");
            return;
        }

        Material material = event.getBlock().getType();
        int ep = mineManager.getOreEP(material);
        if (ep <= 0) return;

        // Cancel default drop (we don't want actual ore items)
        event.setDropItems(false);

        // Handle ore regeneration
        mineManager.onOreMined(blockLoc);

        // Award EP
        UUID uuid = player.getUniqueId();
        growthManager.addOreEP(uuid, ep);

        // Show EP gain
        int currentEP = growthManager.getEPTotal(uuid);
        int requiredEP = growthManager.getEPForNextLevel(uuid);
        if (requiredEP > 0) {
            MessageUtil.sendInfo(player, "§a+" + ep + " EP §8(§7" + currentEP + "/" + requiredEP + "§8)");
        } else {
            MessageUtil.sendInfo(player, "§a+" + ep + " EP §8(§6MAX LEVEL§8)");
        }
    }

    /**
     * Handle mob kills in the mob tower zone.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        // Check if the mob is in the mob tower area
        if (!growthManager.isInMobTowerArea(entity.getLocation())) return;

        // Get EP for this mob type
        int ep = growthManager.getMobEP(entity.getType());
        if (ep <= 0) return;

        // Clear drops in growth zone (no loot)
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Award EP
        UUID uuid = killer.getUniqueId();
        growthManager.addMobEP(uuid, ep);

        // Show EP gain
        int currentEP = growthManager.getEPTotal(uuid);
        int requiredEP = growthManager.getEPForNextLevel(uuid);
        if (requiredEP > 0) {
            MessageUtil.sendInfo(killer, "§a+" + ep + " EP §8(§7" + currentEP + "/" + requiredEP + "§8)");
        } else {
            MessageUtil.sendInfo(killer, "§a+" + ep + " EP §8(§6MAX LEVEL§8)");
        }
    }
}
