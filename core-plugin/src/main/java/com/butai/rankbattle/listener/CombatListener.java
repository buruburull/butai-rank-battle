package com.butai.rankbattle.listener;

import com.butai.rankbattle.arena.ArenaInstance;
import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.manager.EtherManager;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.QueueManager;
import com.butai.rankbattle.model.FrameData;
import com.butai.rankbattle.util.MessageUtil;
import com.butai.rankbattle.BRBPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.event.entity.ProjectileHitEvent;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Handles combat events during matches.
 * - Backstab detection (120 degree cone)
 * - Frame-specific damage multipliers
 * - GUNNER/MARKSMAN ether cost on shoot
 * - Projectile effects (Nova, Seeker, Volt, Tracer)
 * - Charge tracking (Falcon, Zenith)
 * - Bastion shield mode damage modification
 * - Friendly fire prevention (team matches)
 */
public class CombatListener implements Listener {

    private static final double BACKSTAB_ANGLE = 120.0; // degrees
    private static final double BACKSTAB_MULTIPLIER = 1.5;

    private final EtherManager etherManager;
    private final FrameRegistry frameRegistry;
    private final QueueManager queueManager;
    private final Logger logger;

    // Reference to FrameEffectListener for Bastion shield state
    private FrameEffectListener frameEffectListener;

    // Bow draw start time tracking for Falcon/Zenith charge
    private final Map<UUID, Long> bowDrawStart = new ConcurrentHashMap<>();

    // Volt arrow hit count tracking: arrowEntityId -> hitCount
    private final Map<Integer, Integer> voltArrowHits = new ConcurrentHashMap<>();

    // Seeker trident tracking: projectileEntityId -> shooterUUID
    private final Map<Integer, UUID> seekerProjectiles = new ConcurrentHashMap<>();

    public CombatListener(EtherManager etherManager, FrameRegistry frameRegistry,
                          QueueManager queueManager, Logger logger) {
        this.etherManager = etherManager;
        this.frameRegistry = frameRegistry;
        this.queueManager = queueManager;
        this.logger = logger;
    }

    public void setFrameEffectListener(FrameEffectListener listener) {
        this.frameEffectListener = listener;
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

        // Friendly fire prevention (check via match's isTeammate)
        if (queueManager != null) {
            ArenaInstance match = queueManager.getPlayerMatch(attacker.getUniqueId());
            if (match != null && match.isTeammate(attacker.getUniqueId(), victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }

        // Get the frame used by the attacker
        FrameData attackerFrame = getAttackerFrame(event, attacker);
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

                case "bastion":
                    // Bastion: if shield mode is active, halve attack damage
                    if (frameEffectListener != null &&
                            frameEffectListener.isBastionShieldActive(attacker.getUniqueId())) {
                        multiplier *= 0.5; // Attack power halved in shield mode
                    }
                    break;

                case "falcon":
                    // Falcon: charge 2.0s -> 2.5x
                    // Only apply charge multiplier for projectile attacks
                    if (isProjectileAttack(event)) {
                        multiplier = calculateChargeMultiplier(attacker.getUniqueId(), 2.0, 2.5);
                    }
                    break;

                case "zenith":
                    // Zenith: charge 3.0s -> 3.0x
                    if (isProjectileAttack(event)) {
                        multiplier = calculateChargeMultiplier(attacker.getUniqueId(), 3.0, 3.0);
                    }
                    break;

                case "tracer":
                    // Tracer projectile hit: apply Glowing 5s
                    victim.addPotionEffect(new PotionEffect(
                            PotionEffectType.GLOWING, 100, 0, false, false, true));
                    MessageUtil.sendWarning(victim, "§eTracer §7により発光状態に！（5秒）");
                    break;
            }

            // Base backstab multiplier (applies to all melee attacks from behind)
            if (isBackstab && isMeleeAttack(event)) {
                multiplier *= BACKSTAB_MULTIPLIER;
            }

            // Bastion shield mode: victim damage reduction (60% cut)
            if (frameEffectListener != null &&
                    frameEffectListener.isBastionShieldActive(victim.getUniqueId())) {
                multiplier *= 0.4; // 60% damage reduction when in shield mode
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
     * Also track bow draw start for charge-based frames.
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

            // Volt: set piercing level to allow hitting multiple entities
            if ("volt".equals(frame.getId())) {
                arrow.setPierceLevel(3);
                voltArrowHits.put(arrow.getEntityId(), 0);
            }
        }

        // Record bow draw start time for Falcon/Zenith charge calculation
        if ("falcon".equals(frame.getId()) || "zenith".equals(frame.getId())) {
            // Store the shoot time; charge is calculated from vanilla force value
            // event.getForce() is 0.0-1.0, we need longer charge times
            // We'll use force combined with a custom timer
            bowDrawStart.put(shooter.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Handle projectile hit events for Nova explosion and Volt piercing.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) return;
        if (!etherManager.isTracking(shooter.getUniqueId())) return;

        String frameId = getFrameIdFromProjectile(projectile);
        if (frameId == null) return;

        switch (frameId) {
            case "nova" -> handleNovaExplosion(shooter, projectile);
            case "volt" -> handleVoltPierce(projectile);
        }
    }

    /**
     * Nova: Create explosion at projectile impact point.
     */
    private void handleNovaExplosion(Player shooter, Projectile projectile) {
        Location hitLoc = projectile.getLocation();
        UUID shooterUuid = shooter.getUniqueId();

        // Visual explosion (no block damage)
        hitLoc.getWorld().createExplosion(hitLoc, 0f, false, false);

        // Apply damage to nearby players (radius 4.0, damage 5.0)
        double radius = 4.0;
        double maxDamage = 5.0;
        for (Player target : hitLoc.getWorld().getPlayers()) {
            if (target.equals(shooter)) continue;
            if (!etherManager.isTracking(target.getUniqueId())) continue;

            // Check friendly fire
            if (queueManager != null) {
                ArenaInstance match = queueManager.getPlayerMatch(shooterUuid);
                if (match != null && match.isTeammate(shooterUuid, target.getUniqueId())) continue;
            }

            double distance = target.getLocation().distance(hitLoc);
            if (distance <= radius) {
                double damageScale = 1.0 - (distance / radius);
                double damage = maxDamage * damageScale;
                target.damage(damage, shooter);

                if (queueManager != null) {
                    ArenaInstance match = queueManager.getPlayerMatch(shooterUuid);
                    if (match != null) {
                        match.addDamageDealt(shooterUuid, damage);
                    }
                }
            }
        }

        // Effects
        hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        hitLoc.getWorld().spawnParticle(Particle.EXPLOSION, hitLoc, 3, 1.0, 1.0, 1.0, 0);

        projectile.remove();
    }

    /**
     * Volt: Track pierce hits (max 3 entities).
     */
    private void handleVoltPierce(Projectile projectile) {
        Integer hits = voltArrowHits.get(projectile.getEntityId());
        if (hits != null && hits >= 3) {
            projectile.remove();
            voltArrowHits.remove(projectile.getEntityId());
        } else if (hits != null) {
            voltArrowHits.put(projectile.getEntityId(), hits + 1);
        }
    }

    /**
     * Start Seeker homing for a trident projectile.
     */
    public void startSeekerHoming(Player shooter, Projectile projectile) {
        seekerProjectiles.put(projectile.getEntityId(), shooter.getUniqueId());

        new BukkitRunnable() {
            int ticksAlive = 0;

            @Override
            public void run() {
                ticksAlive++;
                if (projectile.isDead() || !projectile.isValid() || ticksAlive > 100) {
                    cancel();
                    seekerProjectiles.remove(projectile.getEntityId());
                    return;
                }

                // Find nearest enemy within 20 blocks
                Player nearestEnemy = null;
                double nearestDist = 20.0;
                Location projLoc = projectile.getLocation();

                for (Player candidate : projLoc.getWorld().getPlayers()) {
                    if (candidate.getUniqueId().equals(shooter.getUniqueId())) continue;
                    if (!etherManager.isTracking(candidate.getUniqueId())) continue;

                    // Check friendly fire
                    if (queueManager != null) {
                        ArenaInstance match = queueManager.getPlayerMatch(shooter.getUniqueId());
                        if (match != null && match.isTeammate(shooter.getUniqueId(), candidate.getUniqueId()))
                            continue;
                    }

                    double dist = candidate.getLocation().distance(projLoc);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestEnemy = candidate;
                    }
                }

                if (nearestEnemy != null) {
                    // Adjust trajectory toward target (gentle curve)
                    Vector toTarget = nearestEnemy.getLocation().add(0, 1, 0).toVector()
                            .subtract(projLoc.toVector()).normalize();
                    Vector currentVel = projectile.getVelocity();
                    double speed = currentVel.length();

                    Vector newVel = currentVel.normalize().multiply(0.7)
                            .add(toTarget.multiply(0.3)).normalize().multiply(speed);
                    projectile.setVelocity(newVel);

                    // Trail particle
                    projLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, projLoc, 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(BRBPlugin.getInstance(), 2L, 1L);
    }

    /**
     * Calculate charge multiplier for Falcon/Zenith based on bow draw time.
     */
    private double calculateChargeMultiplier(UUID shooterUuid, double requiredChargeSeconds, double maxMultiplier) {
        Long drawStart = bowDrawStart.remove(shooterUuid);
        if (drawStart == null) return 1.0;

        double chargeTime = (System.currentTimeMillis() - drawStart) / 1000.0;
        if (chargeTime >= requiredChargeSeconds) {
            return maxMultiplier;
        }
        // Scale linearly from 1.0 to maxMultiplier
        double progress = chargeTime / requiredChargeSeconds;
        return 1.0 + (maxMultiplier - 1.0) * progress;
    }

    /**
     * Handle player death during matches.
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
        event.setDeathMessage(null);

        // Notify match of elimination
        if (queueManager != null) {
            ArenaInstance match = queueManager.getPlayerMatch(victim.getUniqueId());
            if (match != null) {
                Player killer = victim.getKiller();
                String killerName = killer != null ? killer.getName() : "???";
                String victimName = victim.getName();

                match.broadcast("§c§l✖ " + victimName + " §7が §f" + killerName + " §7に倒されました！");
                match.onPlayerEliminated(victim.getUniqueId());

                // Auto-respawn and set spectator mode (skip death screen)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!victim.isOnline()) return;
                        victim.spigot().respawn();

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!victim.isOnline()) return;
                                Location specLoc = match.getSpectatorLocation();
                                if (specLoc != null) {
                                    victim.teleport(specLoc);
                                }
                                victim.setGameMode(GameMode.SPECTATOR);
                                MessageUtil.send(victim, "§7観戦モードに切り替わりました。試合終了まで観戦できます。");
                            }
                        }.runTaskLater(BRBPlugin.getInstance(), 1L);
                    }
                }.runTaskLater(BRBPlugin.getInstance(), 1L);
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
        Vector victimDirection = victim.getLocation().getDirection().normalize();
        Vector toAttacker = attacker.getLocation().toVector()
                .subtract(victim.getLocation().toVector()).normalize();

        double halfAngle = BACKSTAB_ANGLE / 2.0;
        double cosThreshold = Math.cos(Math.toRadians(180.0 - halfAngle));

        double dot = victimDirection.dot(toAttacker);
        return dot < cosThreshold;
    }

    private boolean isMeleeAttack(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Player;
    }

    private boolean isProjectileAttack(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Projectile;
    }

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
     * Get the FrameData for the attacker, considering both melee and projectile attacks.
     */
    private FrameData getAttackerFrame(EntityDamageByEntityEvent event, Player attacker) {
        if (event.getDamager() instanceof Projectile projectile) {
            String frameId = getFrameIdFromProjectile(projectile);
            if (frameId != null) {
                return frameRegistry.getFrame(frameId);
            }
        }
        return getHeldFrame(attacker);
    }

    /**
     * Get frame ID from a projectile's custom name tag.
     */
    private String getFrameIdFromProjectile(Projectile projectile) {
        if (projectile instanceof Arrow arrow) {
            String name = arrow.getCustomName();
            if (name != null && name.startsWith("brb_")) {
                return name.substring(4);
            }
        }
        if (projectile instanceof Trident) {
            if (projectile.getShooter() instanceof Player shooter) {
                FrameData held = getHeldFrame(shooter);
                if (held != null && "seeker".equals(held.getId())) {
                    return "seeker";
                }
            }
        }
        return null;
    }

    private FrameData getHeldFrame(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return getFrameFromItem(item);
    }

    private FrameData getFrameFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String frameId = meta.getPersistentDataContainer()
                .get(FrameCommand.FRAME_KEY, PersistentDataType.STRING);
        if (frameId == null) return null;
        return frameRegistry.getFrame(frameId);
    }

    /**
     * Clean up tracking data for a player (call on match end/disconnect).
     */
    public void clearPlayerState(UUID uuid) {
        bowDrawStart.remove(uuid);
    }
}
