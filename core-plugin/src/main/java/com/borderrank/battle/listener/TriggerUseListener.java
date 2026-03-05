package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.TriggerRegistry;
import com.borderrank.battle.manager.TrionManager;
import com.borderrank.battle.model.Loadout;
import com.borderrank.battle.model.TriggerData;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.ChatColor;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TriggerUseListener implements Listener {

    private final Map<UUID, Long> triggerCooldowns = new HashMap<>();
    private static final long TRIGGER_COOLDOWN_MS = 500;

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!event.getAction().name().contains("RIGHT")) {
            return;
        }
        if (event.getItem() == null) {
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        if (!plugin.getMatchManager().isInMatch(player.getUniqueId())) {
            return;
        }
        if (isOnCooldown(player.getUniqueId())) {
            return;
        }

        TrionManager trionManager = plugin.getTrionManager();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        TriggerRegistry triggerRegistry = plugin.getTriggerRegistry();

        // Get trigger from current hotbar slot
        int heldSlot = player.getInventory().getHeldItemSlot();
        Loadout loadout = loadoutManager.getLoadout(player.getUniqueId(), "default");
        if (loadout == null) return;

        List<String> slots = loadout.getSlots();
        if (heldSlot >= slots.size()) return;

        String triggerId = slots.get(heldSlot);
        if (triggerId == null || triggerId.isEmpty()) return;

        TriggerData triggerData = triggerRegistry.get(triggerId);
        if (triggerData == null) return;

        double trionCost = triggerData.getTrionUse();

        // Check trion
        double currentTrion = trionManager.getTrion(player.getUniqueId());
        if (currentTrion < trionCost) {
            MessageUtil.sendErrorMessage(player, ChatColor.RED + "トリオン不足！ (必要: " + (int)trionCost + " / 現在: " + (int)currentTrion + ")");
            event.setCancelled(true);
            return;
        }

        // Execute trigger by ID
        boolean used = false;
        switch (triggerId) {
            case "grasshopper" -> used = handleGrasshopper(player, trionManager, trionCost);
            case "teleporter" -> used = handleTeleporter(player, trionManager, trionCost);
            case "escudo" -> used = handleEscudo(player, trionManager, trionCost);
            case "shield_trigger" -> used = handleShield(player, trionManager, trionCost);
            case "bagworm" -> used = handleBagworm(player, trionManager, triggerId);
            case "meteora_sub" -> used = handleMeteora(player, trionManager, trionCost);
            case "red_bullet" -> used = handleRedBullet(player, trionManager, trionCost);
            default -> {
                // Weapon triggers (kogetsu, scorpion, etc) - consume trion on use
                if (trionCost > 0 && trionManager.consumeTrion(player.getUniqueId(), trionCost)) {
                    used = true;
                    player.sendActionBar(ChatColor.AQUA + triggerData.getName() + " -" + (int)trionCost + " Trion");
                }
            }
        }

        if (used) {
            setCooldown(player.getUniqueId());
       }
        event.setCancelled(true);
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (!plugin.getMatchManager().isInMatch(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        MessageUtil.sendInfoMessage(player, "トリガーロードアウト切替！");
    }

    private boolean handleGrasshopper(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            Vector velocity = player.getVelocity();
            velocity.setY(2.0);
            player.setVelocity(velocity);
            player.sendActionBar(ChatColor.GREEN + "グラスホッパー！ -" + (int)cost + " Trion");
            return true;
        }
        return false;
    }

    private boolean handleTeleporter(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getLocation().getDirection(),
                15.0
            );
            if (result != null && result.getHitBlock() != null) {
                Block hitBlock = result.getHitBlock();
                Location teleportLoc = hitBlock.getLocation().add(0.5, 1.0, 0.5);
                player.teleport(teleportLoc);
            } else {
                Location teleportLoc = player.getEyeLocation()
                    .add(player.getLocation().getDirection().multiply(15));
                player.teleport(teleportLoc);
            }
            player.sendActionBar(ChatColor.LIGHT_PURPLE + "テレポーター！ -" + (int)cost + " Trion");
            return true;
        }
        return false;
    }

    private boolean handleEscudo(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            Location centerLoc = player.getLocation().add(player.getLocation().getDirection().multiply(3));
            Vector dir = player.getLocation().getDirection();
            boolean placeX = Math.abs(dir.getX()) < Math.abs(dir.getZ());

            for (int i = -1; i <= 1; i++) {
                for (int y = 0; y <= 2; y++) {
                    Location blockLoc;
                    if (placeX) {
                        blockLoc = centerLoc.clone().add(i, y, 0);
                    } else {
                        blockLoc = centerLoc.clone().add(0, y, i);
                    }
                    Block block = blockLoc.getBlock();
                    if (block.getType() == Material.AIR) {
                        block.setType(Material.GLASS);
                    }
                }
            }
            player.sendActionBar(ChatColor.YELLOW + "エスクード！ -" + (int)cost + " Trion");

            BRBPlugin plugin = BRBPlugin.getInstance();
            Location saved = centerLoc.clone();
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                for (int i = -1; i <= 1; i++) {
                    for (int y = 0; y <= 2; y++) {
                        Location blockLoc;
                        if (placeX) {
                            blockLoc = saved.clone().add(i, y, 0);
                        } else {
                            blockLoc = saved.clone().add(0, y, i);
                        }
                        Block block = blockLoc.getBlock();
                        if (block.getType() == Material.GLASS) {
                            block.setType(Material.AIR);
                        }
                    }
                }
            }, 200L);
            return true;
        }
        return false;
    }

    private boolean handleShield(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            player.setAbsorptionAmount(player.getAbsorptionAmount() + 4.0);
            player.sendActionBar(ChatColor.GOLD + "シールド！ -" + (int)cost + " Trion");
            return true;
        }
        return false;
    }

    private boolean handleBagworm(Player player, TrionManager trionManager, String triggerId) {
        // Toggle sustain trigger
        if (trionManager.getActiveSustainCount(player.getUniqueId()) > 0) {
            trionManager.deactivateSustain(player.getUniqueId(), triggerId);
            player.sendActionBar(ChatColor.GRAY + "バグワーム解除");
        } else {
            trionManager.activateSustain(player.getUniqueId(), triggerId);
            player.sendActionBar(ChatColor.DARK_PURPLE + "バグワーム発動中... (持続消費)");
        }
        return true;
    }

    private boolean handleMeteora(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            Location loc = player.getLocation();
            player.getWorld().createExplosion(loc.add(player.getLocation().getDirection().multiply(5)), 2.0F, false, false, player);
            player.sendActionBar(ChatColor.RED + "メテオラ！ -" + (int)cost + " Trion");
            return true;
        }
        return false;
    }

    private boolean handleRedBullet(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            player.sendActionBar(ChatColor.DARK_RED + "レッドバレット！ -" + (int)cost + " Trion (次の攻撃強化)");
            return true;
        }
        return false;
    }

    private boolean isOnCooldown(UUID playerId) {
        Long lastUse = triggerCooldowns.get(playerId);
        if (lastUse == null) return false;
        return System.currentTimeMillis() - lastUse < TRIGGER_COOLDOWN_MS;
    }

    private void setCooldown(UUID playerId) {
        triggerCooldowns.put(playerId, System.currentTimeMillis());
    }
}
