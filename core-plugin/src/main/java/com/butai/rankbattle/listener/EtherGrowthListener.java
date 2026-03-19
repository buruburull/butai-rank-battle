package com.butai.rankbattle.listener;

import com.butai.rankbattle.BRBPlugin;
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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Listens for mining and mob kill events in growth zones.
 * Also handles death in mob tower (no drops, respawn at 1F with sword).
 */
public class EtherGrowthListener implements Listener {

    private final EtherGrowthManager growthManager;
    private final MineManager mineManager;

    public EtherGrowthListener(EtherGrowthManager growthManager, MineManager mineManager) {
        this.growthManager = growthManager;
        this.mineManager = mineManager;
    }

    /**
     * Block all non-ore block breaking in mine zone.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreakProtection(BlockBreakEvent event) {
        Location blockLoc = event.getBlock().getLocation();

        if (mineManager.isInMineZone(blockLoc) && !mineManager.isRegisteredOre(blockLoc)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handle ore mining in the mine zone.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location blockLoc = event.getBlock().getLocation();

        if (!mineManager.isRegisteredOre(blockLoc)) return;

        if (mineManager.isRegenerating(blockLoc)) {
            event.setCancelled(true);
            MessageUtil.sendWarning(player, "この鉱石はまだ再生中です...");
            return;
        }

        Material material = event.getBlock().getType();
        int ep = mineManager.getOreEP(material);
        if (ep <= 0) return;

        event.setCancelled(true);
        mineManager.onOreMined(blockLoc);

        UUID uuid = player.getUniqueId();
        growthManager.addOreEP(uuid, ep);

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

        if (!growthManager.isInMobTowerArea(entity.getLocation())) return;

        int ep = growthManager.getMobEP(entity.getType());
        if (ep <= 0) return;

        // No drops from mobs in tower
        event.getDrops().clear();
        event.setDroppedExp(0);

        UUID uuid = killer.getUniqueId();
        growthManager.addMobEP(uuid, ep);

        int currentEP = growthManager.getEPTotal(uuid);
        int requiredEP = growthManager.getEPForNextLevel(uuid);
        if (requiredEP > 0) {
            MessageUtil.sendInfo(killer, "§a+" + ep + " EP §8(§7" + currentEP + "/" + requiredEP + "§8)");
        } else {
            MessageUtil.sendInfo(killer, "§a+" + ep + " EP §8(§6MAX LEVEL§8)");
        }
    }

    /**
     * Handle player death in mob tower: no item drops, respawn at 1F with sword.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!growthManager.isInTower(uuid)) return;

        // No item drops on death in tower
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);

        // Respawn at 1F after 1 tick (need to wait for respawn)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;

                // Force respawn if still dead
                if (player.isDead()) {
                    player.spigot().respawn();
                }

                // Teleport to 1F and re-give sword
                Location spawn = growthManager.getMobTowerSpawn();
                if (spawn != null) {
                    player.teleport(spawn);
                }
                player.getInventory().clear();
                player.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);

                MessageUtil.sendWarning(player, "§c倒されました！§71Fにリスポーンしました。");
            }
        }.runTaskLater(BRBPlugin.getInstance(), 2L);
    }
}
