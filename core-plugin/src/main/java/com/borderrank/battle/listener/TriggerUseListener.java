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
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class TriggerUseListener implements Listener {

    private final Map<UUID, Long> triggerCooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Long>> perTriggerCooldowns = new HashMap<>();
    private final Set<UUID> raygustShieldMode = new HashSet<>();
    private final Set<UUID> bagwormActive = new HashSet<>();
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
            MessageUtil.sendErrorMessage(player, ChatColor.RED + "トリオン不足！ (必要: " + (int) trionCost + " / 現在: " + (int) currentTrion + ")");
            event.setCancelled(true);
            return;
        }

        // Check per-trigger cooldown
        if (isOnTriggerCooldown(player.getUniqueId(), triggerId, triggerData)) {
            long remaining = getTriggerCooldownRemaining(player.getUniqueId(), triggerId, triggerData);
            MessageUtil.sendErrorMessage(player, ChatColor.GRAY + "クールダウン中... (" + (remaining / 1000) + "秒)");
            event.setCancelled(true);
            return;
        }

        // Weapon triggers that use Minecraft's native right-click mechanics (bow draw, crossbow load, trident throw)
        // should NOT be cancelled - let the player use them normally
        // Note: Swords are NOT included - they have no native right-click mechanic,
        // and Raygust (IRON_SWORD) needs right-click for shield toggle
        Material heldMat = triggerData.getMcItem();
        boolean isNativeWeapon = (heldMat == Material.BOW || heldMat == Material.CROSSBOW
                || heldMat == Material.TRIDENT);

        if (isNativeWeapon) {
            // Native weapons: consume trion on use but don't cancel the event
            if (trionCost > 0 && trionManager.consumeTrion(player.getUniqueId(), trionCost)) {
                player.sendActionBar(ChatColor.AQUA + triggerData.getName() + " -" + (int) trionCost + " Trion");
                setCooldown(player.getUniqueId());
            }
            // Don't cancel - let bow draw, crossbow load, trident throw, sword swing work
            return;
        }

        // Execute support/special trigger by ID
        boolean used = false;
        switch (triggerId) {
            case "grasshopper" -> used = handleGrasshopper(player, trionManager, trionCost);
            case "teleporter" -> used = handleTeleporter(player, trionManager, trionCost);
            case "escudo" -> used = handleEscudo(player, trionManager, trionCost);
            case "shield_trigger" -> used = handleShield(player, trionManager, trionCost);
            case "bagworm" -> used = handleBagworm(player, trionManager, triggerId);
            case "meteora_sub" -> used = handleMeteoraSub(player, trionManager, trionCost);
            case "red_bullet" -> used = handleRedBullet(player, trionManager, trionCost);
            case "raygust" -> used = handleRaygust(player, trionManager, trionCost);
            // Melee weapons - no right-click ability, ignore
            case "kogetsu", "scorpion" -> { /* no right-click action for melee triggers */ }
            default -> {
                // Unknown support trigger
                if (trionCost > 0 && trionManager.consumeTrion(player.getUniqueId(), trionCost)) {
                    used = true;
                    player.sendActionBar(ChatColor.AQUA + triggerData.getName() + " -" + (int) trionCost + " Trion");
                }
            }
        }

        if (used) {
            setCooldown(player.getUniqueId());
            setTriggerCooldown(player.getUniqueId(), triggerId, triggerData);
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

    // ==========================================
    // SUPPORT TRIGGERS
    // ==========================================

    private boolean handleGrasshopper(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            Vector velocity = player.getLocation().getDirection().multiply(0.5);
            velocity.setY(2.0);
            player.setVelocity(velocity);
            // Prevent fall damage for 3 seconds
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, true));
            player.sendActionBar(ChatColor.GREEN + "グラスホッパー！ -" + (int) cost + " Trion");
            return true;
        }
        return false;
    }

    private boolean handleTeleporter(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            RayTraceResult result = player.getWorld().rayTraceBlocks(
                    player.getEyeLocation(),
                    player.getLocation().getDirection(),
                    32.0
            );
            if (result != null && result.getHitBlock() != null) {
                Block hitBlock = result.getHitBlock();
                Location teleportLoc = hitBlock.getLocation().add(0.5, 1.0, 0.5);
                player.teleport(teleportLoc);
            } else {
                Location teleportLoc = player.getEyeLocation()
                        .add(player.getLocation().getDirection().multiply(32));
                player.teleport(teleportLoc);
            }
            player.sendActionBar(ChatColor.LIGHT_PURPLE + "テレポーター！ -" + (int) cost + " Trion");
            return true;
        }
        return false;
    }

    private boolean handleEscudo(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            Location centerLoc = player.getLocation().add(player.getLocation().getDirection().multiply(3));
            Vector dir = player.getLocation().getDirection();
            boolean placeX = Math.abs(dir.getX()) < Math.abs(dir.getZ());

            BRBPlugin plugin = BRBPlugin.getInstance();
            // Get BlockTracker to record changes for match-end restoration
            com.borderrank.battle.arena.ArenaInstance match =
                    plugin.getMatchManager().getPlayerMatch(player.getUniqueId());

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
                        // Record original state (AIR) before placing glass
                        if (match != null) {
                            match.getBlockTracker().recordBlockChange(block);
                        }
                        block.setType(Material.GLASS);
                    }
                }
            }
            player.sendActionBar(ChatColor.YELLOW + "エスクード！ -" + (int) cost + " Trion");

            Location saved = centerLoc.clone();
            boolean savedPlaceX = placeX;
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                for (int i = -1; i <= 1; i++) {
                    for (int y = 0; y <= 2; y++) {
                        Location blockLoc;
                        if (savedPlaceX) {
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
            }, 200L); // 10 seconds
            return true;
        }
        return false;
    }

    private boolean handleShield(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            // Add absorption hearts for 5 seconds (ABSORPTION level 1 = 4 extra hearts)
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, false, true));
            player.sendActionBar(ChatColor.GOLD + "シールド！ -" + (int) cost + " Trion");
            return true;
        }
        return false;
    }

    private boolean handleBagworm(Player player, TrionManager trionManager, String triggerId) {
        UUID uuid = player.getUniqueId();
        // Activate stealth - will be auto-deactivated when player switches away
        if (trionManager.getActiveSustainCount(uuid) > 0) {
            // Already active, do nothing (holding bagworm = stay invisible)
            player.sendActionBar(ChatColor.DARK_PURPLE + "バグワーム発動中...");
            return false;
        }
        trionManager.activateSustain(uuid, triggerId);
        bagwormActive.add(uuid);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 999999, 0, false, false, false));
        player.sendActionBar(ChatColor.DARK_PURPLE + "バグワーム発動！ 透明化中... (持ち替えで解除)");
        return true;
    }

    /**
     * When player switches held item, deactivate Bagworm if it was active.
     */
    @EventHandler
    public void onItemSwitch(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!bagwormActive.contains(uuid)) return;

        BRBPlugin plugin = BRBPlugin.getInstance();
        if (!plugin.getMatchManager().isInMatch(uuid)) return;

        // Check if the new slot is still bagworm
        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        Loadout loadout = loadoutManager.getLoadout(uuid, "default");
        if (loadout != null) {
            List<String> slots = loadout.getSlots();
            int newSlot = event.getNewSlot();
            if (newSlot < slots.size()) {
                String newTriggerId = slots.get(newSlot);
                if ("bagworm".equals(newTriggerId)) {
                    return; // Still holding bagworm, keep invisible
                }
            }
        }

        // Switched away from bagworm - deactivate
        deactivateBagworm(player);
    }

    private boolean handleMeteoraSub(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            // Launch a TNT-like projectile (snowball as carrier, explodes on impact)
            org.bukkit.entity.Snowball projectile = player.launchProjectile(org.bukkit.entity.Snowball.class);
            projectile.setVelocity(player.getLocation().getDirection().multiply(1.5));
            projectile.setShooter(player);
            // Tag it as meteora_sub for ProjectileListener to handle
            BRBPlugin plugin = BRBPlugin.getInstance();
            projectile.setMetadata("brb_trigger_id",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, "meteora_sub"));
            projectile.setMetadata("brb_shooter_uuid",
                    new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId().toString()));
            player.sendActionBar(ChatColor.RED + "メテオラ投擲！ -" + (int) cost + " Trion");
            return true;
        }
        return false;
    }

    private boolean handleRedBullet(Player player, TrionManager trionManager, double cost) {
        if (trionManager.consumeTrion(player.getUniqueId(), cost)) {
            // Launch a spectral arrow (applies Glowing on hit via CombatListener)
            SpectralArrow arrow = player.launchProjectile(SpectralArrow.class);
            arrow.setVelocity(player.getLocation().getDirection().multiply(2.5));
            arrow.setShooter(player);
            arrow.setGlowingTicks(100); // 5 seconds glowing on hit
            player.sendActionBar(ChatColor.DARK_RED + "レッドバレット発射！ -" + (int) cost + " Trion");
            return true;
        }
        return false;
    }

    // ==========================================
    // ATTACKER TRIGGERS
    // ==========================================

    private boolean handleRaygust(Player player, TrionManager trionManager, double cost) {
        UUID uuid = player.getUniqueId();

        if (raygustShieldMode.contains(uuid)) {
            // Switch back to attack mode
            raygustShieldMode.remove(uuid);
            player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            // Remove resistance effect
            player.removePotionEffect(PotionEffectType.RESISTANCE);
            player.sendActionBar(ChatColor.WHITE + "レイガスト: アタックモード");
        } else {
            // Switch to shield mode - consume trion
            if (!trionManager.consumeTrion(uuid, cost)) return false;
            raygustShieldMode.add(uuid);
            // Give shield in offhand
            ItemStack shield = new ItemStack(Material.SHIELD);
            ItemMeta meta = shield.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.BLUE + "レイガスト・シールド");
                shield.setItemMeta(meta);
            }
            player.getInventory().setItemInOffHand(shield);
            // Add damage resistance (Resistance I = 20% damage reduction)
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, 0, false, false, true));
            player.sendActionBar(ChatColor.BLUE + "レイガスト: シールドモード -" + (int) cost + " Trion");
        }
        return true;
    }

    /**
     * Check if Raygust shield mode is active for a player.
     */
    public boolean isRaygustShieldMode(UUID uuid) {
        return raygustShieldMode.contains(uuid);
    }

    /**
     * Clear Raygust shield mode when match ends.
     */
    public void clearRaygustShieldMode(UUID uuid) {
        raygustShieldMode.remove(uuid);
    }

    /**
     * Deactivate Bagworm stealth for a player.
     */
    public void deactivateBagworm(Player player) {
        UUID uuid = player.getUniqueId();
        if (!bagwormActive.remove(uuid)) return;
        BRBPlugin plugin = BRBPlugin.getInstance();
        plugin.getTrionManager().deactivateSustain(uuid, "bagworm");
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        player.sendActionBar(ChatColor.GRAY + "バグワーム解除（持ち替え）");
    }

    /**
     * Clear Bagworm state when match ends.
     */
    public void clearBagworm(UUID uuid) {
        bagwormActive.remove(uuid);
    }

    // ==========================================
    // COOLDOWN MANAGEMENT
    // ==========================================

    private boolean isOnCooldown(UUID playerId) {
        Long lastUse = triggerCooldowns.get(playerId);
        if (lastUse == null) return false;
        return System.currentTimeMillis() - lastUse < TRIGGER_COOLDOWN_MS;
    }

    private void setCooldown(UUID playerId) {
        triggerCooldowns.put(playerId, System.currentTimeMillis());
    }

    private boolean isOnTriggerCooldown(UUID playerId, String triggerId, TriggerData triggerData) {
        if (triggerData.getCooldown() <= 0) return false;
        Map<String, Long> playerCooldowns = perTriggerCooldowns.get(playerId);
        if (playerCooldowns == null) return false;
        Long lastUse = playerCooldowns.get(triggerId);
        if (lastUse == null) return false;
        return System.currentTimeMillis() - lastUse < (triggerData.getCooldown() * 1000L);
    }

    private long getTriggerCooldownRemaining(UUID playerId, String triggerId, TriggerData triggerData) {
        Map<String, Long> playerCooldowns = perTriggerCooldowns.get(playerId);
        if (playerCooldowns == null) return 0;
        Long lastUse = playerCooldowns.get(triggerId);
        if (lastUse == null) return 0;
        long elapsed = System.currentTimeMillis() - lastUse;
        long cooldownMs = triggerData.getCooldown() * 1000L;
        return Math.max(0, cooldownMs - elapsed);
    }

    private void setTriggerCooldown(UUID playerId, String triggerId, TriggerData triggerData) {
        if (triggerData.getCooldown() <= 0) return;
        perTriggerCooldowns.computeIfAbsent(playerId, k -> new HashMap<>()).put(triggerId, System.currentTimeMillis());
    }
}
