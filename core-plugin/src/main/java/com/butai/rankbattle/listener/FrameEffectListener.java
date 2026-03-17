package com.butai.rankbattle.listener;

import com.butai.rankbattle.arena.ArenaInstance;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.manager.EtherManager;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.QueueManager;
import com.butai.rankbattle.model.FrameData;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles right-click activated frame effects:
 * - Leap: directional dash
 * - Warp: teleport 32m forward
 * - Vant: barrier blocks (4s duration)
 * - Blast: explosion (radius 5.0, power 6.0)
 * - Bastion: shield mode toggle
 * - Cloak: stealth toggle
 * - Tracer: throw spectral arrow with glowing effect
 */
public class FrameEffectListener implements Listener {

    private final EtherManager etherManager;
    private final FrameRegistry frameRegistry;
    private final QueueManager queueManager;
    private final Logger logger;

    // Cooldown tracking: playerUUID -> frameId -> expiry time (millis)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // Bastion shield mode tracking
    private final Set<UUID> bastionShieldActive = ConcurrentHashMap.newKeySet();

    public FrameEffectListener(EtherManager etherManager, FrameRegistry frameRegistry,
                               QueueManager queueManager, Logger logger) {
        this.etherManager = etherManager;
        this.frameRegistry = frameRegistry;
        this.queueManager = queueManager;
        this.logger = logger;
    }

    /**
     * Check if Bastion shield mode is active for a player.
     */
    public boolean isBastionShieldActive(UUID uuid) {
        return bastionShieldActive.contains(uuid);
    }

    /**
     * Clear all state for a player (call on match end/disconnect).
     */
    public void clearPlayerState(UUID uuid) {
        cooldowns.remove(uuid);
        bastionShieldActive.remove(uuid);
        // Remove invisibility if Cloak was active
        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click actions
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only process main hand
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Only process if player is in a match
        if (!etherManager.isTracking(uuid)) return;

        // Get frame from held item
        FrameData frame = getHeldFrame(player);
        if (frame == null) return;

        switch (frame.getId()) {
            case "fang" -> handleFangSpeed(player, frame, event);
            case "leap" -> handleLeap(player, frame, event);
            case "warp" -> handleWarp(player, frame, event);
            case "vant" -> handleVant(player, frame, event);
            case "blast" -> handleBlast(player, frame, event);
            case "cloak" -> handleCloakToggle(player, frame, event);
            case "tracer" -> handleTracer(player, frame, event);
        }
    }

    // ========== FANG (speed boost) ==========

    private void handleFangSpeed(Player player, FrameData frame, PlayerInteractEvent event) {
        event.setCancelled(true);
        UUID uuid = player.getUniqueId();

        if (isOnCooldown(uuid, frame.getId())) {
            long remaining = getCooldownRemaining(uuid, frame.getId());
            MessageUtil.sendError(player, "クールタイム中！ (残り" + remaining + "秒)");
            return;
        }

        if (!etherManager.consumeUse(uuid, frame)) {
            MessageUtil.sendError(player, "エーテル不足！ (必要: " + frame.getEtherUse() + ")");
            return;
        }

        // Speed II for 3 seconds (60 ticks)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED, 60, 1, false, true, true));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 5, 0.5, 0.3, 0.5, 0);

        setCooldown(uuid, frame.getId(), frame.getCooldown());
        MessageUtil.sendInfo(player, "§6Fang §7加速発動！（3秒）");
    }

    // ========== LEAP ==========

    private void handleLeap(Player player, FrameData frame, PlayerInteractEvent event) {
        event.setCancelled(true);
        UUID uuid = player.getUniqueId();

        if (isOnCooldown(uuid, frame.getId())) {
            long remaining = getCooldownRemaining(uuid, frame.getId());
            MessageUtil.sendError(player, "クールタイム中！ (残り" + remaining + "秒)");
            return;
        }

        if (!etherManager.consumeUse(uuid, frame)) {
            MessageUtil.sendError(player, "エーテル不足！ (必要: " + frame.getEtherUse() + ")");
            return;
        }

        // Directional dash: launch player in the direction they're facing
        Vector direction = player.getLocation().getDirection().normalize();
        // Apply strong velocity in facing direction (including vertical component)
        player.setVelocity(direction.multiply(2.5));

        // Visual effect
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.5f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.1, 0.3, 0.08);

        setCooldown(uuid, frame.getId(), frame.getCooldown());
        MessageUtil.sendInfo(player, "§aLeap §7発動！ ダッシュ！");
    }

    // ========== WARP ==========

    private void handleWarp(Player player, FrameData frame, PlayerInteractEvent event) {
        event.setCancelled(true);
        UUID uuid = player.getUniqueId();

        if (isOnCooldown(uuid, frame.getId())) {
            long remaining = getCooldownRemaining(uuid, frame.getId());
            MessageUtil.sendError(player, "クールタイム中！ (残り" + remaining + "秒)");
            return;
        }

        if (!etherManager.consumeUse(uuid, frame)) {
            MessageUtil.sendError(player, "エーテル不足！ (必要: " + frame.getEtherUse() + ")");
            return;
        }

        // Teleport 32m in facing direction (ray trace for safety)
        Location origin = player.getLocation();
        Vector direction = origin.getDirection().normalize();
        double maxDistance = 32.0;

        // Ray trace to check for blocks in the way
        RayTraceResult rayResult = player.getWorld().rayTraceBlocks(
                origin.clone().add(0, 0.5, 0), direction, maxDistance,
                FluidCollisionMode.NEVER, true);

        Location target;
        if (rayResult != null && rayResult.getHitBlock() != null) {
            // Hit a block - teleport to just before it
            target = rayResult.getHitPosition().toLocation(player.getWorld());
            target.subtract(direction.clone().multiply(0.5)); // Step back slightly
        } else {
            // No obstruction - teleport full distance
            target = origin.clone().add(direction.multiply(maxDistance));
        }

        // Preserve yaw and pitch
        target.setYaw(origin.getYaw());
        target.setPitch(origin.getPitch());

        // Ensure safe landing (find highest block)
        Block landBlock = target.getWorld().getHighestBlockAt(target);
        if (target.getY() < landBlock.getY() + 1) {
            target.setY(landBlock.getY() + 1);
        }

        // Visual effects at origin
        player.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.PORTAL, origin.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.5);

        player.teleport(target);

        // Visual effects at destination
        player.getWorld().playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.5);

        setCooldown(uuid, frame.getId(), frame.getCooldown());
        MessageUtil.sendInfo(player, "§dWarp §7発動！");
    }

    // ========== VANT ==========

    private void handleVant(Player player, FrameData frame, PlayerInteractEvent event) {
        event.setCancelled(true);
        UUID uuid = player.getUniqueId();

        if (!etherManager.consumeUse(uuid, frame)) {
            MessageUtil.sendError(player, "エーテル不足！ (必要: " + frame.getEtherUse() + ")");
            return;
        }

        // Place barrier blocks 2 blocks in front of player (3 wide, 3 tall)
        Location loc = player.getLocation();
        Vector direction = loc.getDirection().normalize();
        direction.setY(0); // Only horizontal
        direction.normalize();

        // Calculate front position (2 blocks ahead)
        Location front = loc.clone().add(direction.multiply(2));
        front.setY(Math.floor(front.getY()));

        // Calculate perpendicular direction for width
        Vector perp = new Vector(-direction.getZ(), 0, direction.getX());

        // Place 3 wide x 3 tall glass wall
        for (int w = -1; w <= 1; w++) {
            for (int h = 0; h < 3; h++) {
                Location blockLoc = front.clone()
                        .add(perp.clone().multiply(w))
                        .add(0, h, 0);
                Block block = blockLoc.getBlock();
                if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
                    block.setType(Material.GLASS);
                }
            }
        }

        // Visual effect
        player.getWorld().playSound(front, Sound.BLOCK_GLASS_PLACE, 1.0f, 1.0f);

        MessageUtil.sendInfo(player, "§6Vant §7発動！ ガラス壁生成。");
    }

    // ========== BLAST ==========

    private void handleBlast(Player player, FrameData frame, PlayerInteractEvent event) {
        event.setCancelled(true);
        UUID uuid = player.getUniqueId();

        if (!etherManager.consumeUse(uuid, frame)) {
            MessageUtil.sendError(player, "エーテル不足！ (必要: " + frame.getEtherUse() + ")");
            return;
        }

        // Throw a snowball projectile (explosion handled in CombatListener.onProjectileHit)
        org.bukkit.entity.Snowball snowball = player.launchProjectile(org.bukkit.entity.Snowball.class);
        snowball.setCustomName("brb_blast");
        snowball.setVelocity(player.getLocation().getDirection().multiply(1.5));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1.0f, 0.8f);
        MessageUtil.sendInfo(player, "§cBlast §7投擲！");
    }

    // ========== BASTION (F-key swap: sword ⇔ shield) ==========

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!etherManager.isTracking(uuid)) return;

        // Check if the item being swapped TO offhand is Bastion
        ItemStack mainHandItem = event.getMainHandItem(); // item going to main hand
        ItemStack offHandItem = event.getOffHandItem();   // item going to offhand

        // Case 1: Bastion sword going to offhand → convert to shield (activate)
        String offHandFrameId = getFrameIdFromItem(offHandItem);
        if ("bastion".equals(offHandFrameId) && !bastionShieldActive.contains(uuid)) {
            event.setCancelled(true);

            // Convert main hand sword to shield in offhand
            FrameData bastionFrame = frameRegistry.getFrame("bastion");
            ItemStack shield = createBastionShield(bastionFrame);
            player.getInventory().setItemInOffHand(shield);
            player.getInventory().setItemInMainHand(null);

            // Find the original hotbar slot and clear it
            bastionShieldActive.add(uuid);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.0f);
            MessageUtil.sendInfo(player, "§7Bastion シールドモード §a発動 §8(攻撃力半減・被ダメ60%カット)");
            return;
        }

        // Case 2: Bastion shield in offhand going back to main hand → convert to sword (deactivate)
        String mainHandFrameId = getFrameIdFromItem(mainHandItem);
        if ("bastion".equals(mainHandFrameId) && bastionShieldActive.contains(uuid)) {
            event.setCancelled(true);

            // Convert offhand shield back to sword in main hand
            FrameData bastionFrame = frameRegistry.getFrame("bastion");
            ItemStack sword = createBastionSword(bastionFrame);
            player.getInventory().setItemInMainHand(sword);
            player.getInventory().setItemInOffHand(null);

            bastionShieldActive.remove(uuid);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.8f, 1.0f);
            MessageUtil.sendInfo(player, "§7Bastion シールドモード §c解除");
        }
    }

    private ItemStack createBastionShield(FrameData frame) {
        ItemStack shield = new ItemStack(Material.SHIELD);
        ItemMeta meta = shield.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7§lBastion §8[シールド]");
            List<String> lore = new ArrayList<>();
            lore.add("§7攻撃力半減・被ダメ60%カット");
            lore.add("§7Fキーで剣に戻す");
            lore.add("§8§oBRB Frame");
            meta.setLore(lore);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(FrameCommand.FRAME_KEY, PersistentDataType.STRING, "bastion");
            shield.setItemMeta(meta);
        }
        return shield;
    }

    private ItemStack createBastionSword(FrameData frame) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7§lBastion §8[剣]");
            List<String> lore = new ArrayList<>();
            lore.add("§7" + frame.getDescription());
            lore.add("§7Fキーでシールドに切替");
            lore.add("§8§oBRB Frame");
            meta.setLore(lore);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(FrameCommand.FRAME_KEY, PersistentDataType.STRING, "bastion");
            sword.setItemMeta(meta);
        }
        return sword;
    }

    private String getFrameIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(FrameCommand.FRAME_KEY, PersistentDataType.STRING);
    }

    // ========== CLOAK (toggle) ==========

    private void handleCloakToggle(Player player, FrameData frame, PlayerInteractEvent event) {
        event.setCancelled(true);
        UUID uuid = player.getUniqueId();

        if (etherManager.isSustainActive(uuid, "cloak")) {
            // Deactivate
            etherManager.deactivateSustain(uuid, "cloak");
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 0.8f);
            MessageUtil.sendInfo(player, "§7Cloak §c解除");
        } else {
            // Activate
            etherManager.activateSustain(uuid, frame);
            // Apply invisibility (infinite duration, removed on deactivate)
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f);
            MessageUtil.sendInfo(player, "§7Cloak §a発動 §8(透明化・被ダメ60%カット, 15エーテル/秒)");
        }
    }

    // ========== TRACER ==========

    private void handleTracer(Player player, FrameData frame, PlayerInteractEvent event) {
        event.setCancelled(true);
        UUID uuid = player.getUniqueId();

        if (!etherManager.consumeUse(uuid, frame)) {
            MessageUtil.sendError(player, "エーテル不足！ (必要: " + frame.getEtherUse() + ")");
            return;
        }

        // Ray trace to find target player (64 blocks)
        Location origin = player.getEyeLocation();
        Vector direction = origin.getDirection().normalize();

        // Find nearest player in line of sight (within 64 blocks)
        Player target = null;
        double closestDist = 64.0;
        for (Player candidate : player.getWorld().getPlayers()) {
            if (candidate.equals(player)) continue;
            if (!etherManager.isTracking(candidate.getUniqueId())) continue;

            // Check if candidate is in the direction player is looking
            Vector toCandidate = candidate.getLocation().toVector().subtract(origin.toVector());
            double distance = toCandidate.length();
            if (distance > closestDist) continue;

            // Check angle (within ~10 degree cone)
            double dot = direction.dot(toCandidate.normalize());
            if (dot > 0.985) { // ~10 degree cone
                // Check friendly fire
                if (queueManager != null) {
                    ArenaInstance match = queueManager.getPlayerMatch(uuid);
                    if (match != null && match.isTeammate(uuid, candidate.getUniqueId())) continue;
                }
                target = candidate;
                closestDist = distance;
            }
        }

        if (target != null) {
            // Apply Glowing effect for 5 seconds
            target.addPotionEffect(new PotionEffect(
                    PotionEffectType.GLOWING, 100, 0, false, false, true)); // 5 seconds

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            MessageUtil.sendInfo(player, "§eTracer §7発動！ §f" + target.getName() + " §7に発光効果付与。");
            MessageUtil.sendWarning(target, "§eTracer §7により発光状態に！（5秒）");
        } else {
            MessageUtil.sendError(player, "対象が見つかりません。（エーテル消費済み）");
        }
    }

    // ========== COOLDOWN MANAGEMENT ==========

    private boolean isOnCooldown(UUID uuid, String frameId) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return false;
        Long expiry = playerCooldowns.get(frameId);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            playerCooldowns.remove(frameId);
            return false;
        }
        return true;
    }

    private long getCooldownRemaining(UUID uuid, String frameId) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) return 0;
        Long expiry = playerCooldowns.get(frameId);
        if (expiry == null) return 0;
        long remaining = (expiry - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    private void setCooldown(UUID uuid, String frameId, int seconds) {
        if (seconds <= 0) return;
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(frameId, System.currentTimeMillis() + (seconds * 1000L));
    }

    // ========== UTILITY ==========

    private FrameData getHeldFrame(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String frameId = meta.getPersistentDataContainer()
                .get(FrameCommand.FRAME_KEY, PersistentDataType.STRING);
        if (frameId == null) return null;
        return frameRegistry.getFrame(frameId);
    }
}
