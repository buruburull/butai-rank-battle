package com.butai.rankbattle.listener;

import com.butai.rankbattle.arena.ArenaInstance;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.manager.EtherManager;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.QueueManager;
import com.butai.rankbattle.model.FrameData;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles combat events during matches.
 * - Backstab detection (120 degree cone)
 * - Frame-specific damage multipliers (Crescent 1.3x, Fang backstab 1.5x)
 * - GUNNER/MARKSMAN ether cost on shoot
 * - Frost: Slowness II 3s on hit
 * - Natural health regen disabled during matches
 * - Friendly fire prevention (team matches)
 */
public class CombatListener implements Listener {

    private static final double BACKSTAB_ANGLE = 120.0; // degrees
    private static final double BACKSTAB_MULTIPLIER = 1.5;

    private final EtherManager etherManager;
    private final FrameRegistry frameRegistry;
    private final QueueManager queueManager;
    private final Logger logger;

    // Teammate check callback (set by match system)
    private TeammateChecker teammateChecker;

    @FunctionalInterface
    public interface TeammateChecker {
        boolean isTeammate(UUID player1, UUID player2);
    }

    public CombatListener(EtherManager etherManager, FrameRegistry frameRegistry,
                          QueueManager queueManager, Logger logger) {
        this.etherManager = etherManager;
        this.frameRegistry = frameRegistry;
        this.queueManager = queueManager;
        this.logger = logger;
    }

    public void setTeammateChecker(TeammateChecker checker) {
        this.teammateChecker = checker;
    }

    /**
     * Handle melee and projectile damage between players in matches.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        // Only process if both players are in a match (tracked by ether system)
        if (!etherManager.isTracking(attacker.getUniqueId()) ||
                !etherManager.isTracking(victim.getUniqueId())) {
            return;
        }

        // Friendly fire prevention
        if (teammateChecker != null &&
                teammateChecker.isTeammate(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Get the frame used by the attacker
        FrameData attackerFrame = getHeldFrame(attacker);
        double damage = event.getDamage();
        double multiplier = 1.0;

        if (attackerFrame != null) {
            // Apply frame damage multiplier
            multiplier *= attackerFrame.getDamageMultiplier();

            // Backstab check
            boolean isBackstab = isBackstab(attacker, victim);

            // Frame-specific effects
            switch (attackerFrame.getId()) {
                case "crescent":
                    // Crescent: 1.3x already in damageMultiplier from frames.yml
                    break;

                case "fang":
                    // Fang: additional 1.5x on backstab (total with base backstab = 2.25x)
                    if (isBackstab) {
                        multiplier *= BACKSTAB_MULTIPLIER; // Fang's own backstab bonus
                    }
                    break;

                case "frost":
                    // Frost: Slowness II for 3 seconds on hit
                    victim.addPotionEffect(new PotionEffect(
                            PotionEffectType.SLOWNESS, 60, 1, false, true, true));
                    break;
            }

            // Base backstab multiplier (applies to all melee attacks from behind)
            if (isBackstab && isMeleeAttack(event)) {
                multiplier *= BACKSTAB_MULTIPLIER;
            }

            // Cloak damage reduction (victim)
            if (etherManager.isSustainActive(victim.getUniqueId(), "cloak")) {
                multiplier *= 0.4; // 60% damage reduction
            }
        }

        // Apply final damage
        double finalDamage = damage * multiplier;
        event.setDamage(finalDamage);

        // Track damage dealt for judge scoring
        if (queueManager != null) {
            ArenaInstance match = queueManager.getPlayerMatch(attacker.getUniqueId());
            if (match != null) {
                match.addDamageDealt(attacker.getUniqueId(), finalDamage);
            }
        }
    }

    /**
     * Handle bow/crossbow shooting - consume ether for GUNNER/MARKSMAN frames.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;
        if (!etherManager.isTracking(shooter.getUniqueId())) return;

        // Get the frame from the bow item
        FrameData frame = getFrameFromItem(event.getBow());
        if (frame == null) return;

        // Consume ether for the shot
        if (frame.getEtherUse() > 0) {
            if (!etherManager.consumeUse(shooter.getUniqueId(), frame)) {
                // Not enough ether - cancel the shot
                event.setCancelled(true);
                MessageUtil.sendError(shooter, "エーテル不足！ (必要: " + frame.getEtherUse() + ")");
                return;
            }
        }

        // Tag the projectile with frame ID for hit processing
        if (event.getProjectile() instanceof Arrow arrow) {
            arrow.setCustomName("brb_" + frame.getId());
        }
    }

    /**
     * Handle player death during matches.
     * - Prevent item drops
     * - Notify match of elimination
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!etherManager.isTracking(victim.getUniqueId())) return;

        // Prevent item drops and XP drops (keep inventory)
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepLevel(true);

        // Notify match of elimination
        if (queueManager != null) {
            ArenaInstance match = queueManager.getPlayerMatch(victim.getUniqueId());
            if (match != null) {
                // Get killer name for broadcast
                Player killer = victim.getKiller();
                String killerName = killer != null ? killer.getName() : "???";
                String victimName = victim.getName();
                event.setDeathMessage(null); // Suppress default death message

                // Broadcast kill message to match players
                match.broadcast("§c§l✖ " + victimName + " §7が §f" + killerName + " §7に倒されました！");

                // Mark as eliminated (triggers win condition check)
                match.onPlayerEliminated(victim.getUniqueId());
            }
        }
    }

    /**
     * Disable natural health regeneration during matches.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!etherManager.isTracking(player.getUniqueId())) return;

        // Cancel natural/peaceful/satiated regeneration during matches
        EntityRegainHealthEvent.RegainReason reason = event.getRegainReason();
        if (reason == EntityRegainHealthEvent.RegainReason.SATIATED ||
                reason == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if an attack is from behind the victim (within BACKSTAB_ANGLE degrees).
     */
    private boolean isBackstab(Player attacker, Player victim) {
        // Get victim's facing direction (normalized)
        Vector victimDirection = victim.getLocation().getDirection().normalize();
        // Get vector from victim to attacker
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).normalize();

        // If attacker is behind the victim, the dot product of victimDirection and toAttacker
        // will be negative (they point in roughly opposite directions when attacked from behind)
        // Actually: if attacker is behind, toAttacker points backward relative to victim's facing
        // So we check if the angle between victimDirection and toAttacker is > (180 - BACKSTAB_ANGLE/2)
        // Simplified: attacker is behind if dot(victimFacing, victimToAttacker) < cos(180 - halfAngle)
        double halfAngle = BACKSTAB_ANGLE / 2.0;
        double cosThreshold = Math.cos(Math.toRadians(180.0 - halfAngle));

        double dot = victimDirection.dot(toAttacker);
        return dot < cosThreshold;
    }

    /**
     * Check if the damage event is a melee (non-projectile) attack.
     */
    private boolean isMeleeAttack(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Player;
    }

    /**
     * Resolve the attacking player from a damage event (handles projectiles).
     */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }
        return null;
    }

    /**
     * Get the FrameData from the player's currently held item (main hand).
     */
    private FrameData getHeldFrame(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return getFrameFromItem(item);
    }

    /**
     * Get the FrameData from an ItemStack by reading its PDC tag.
     */
    private FrameData getFrameFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String frameId = meta.getPersistentDataContainer()
                .get(FrameCommand.FRAME_KEY, PersistentDataType.STRING);
        if (frameId == null) return null;
        return frameRegistry.getFrame(frameId);
    }
}
