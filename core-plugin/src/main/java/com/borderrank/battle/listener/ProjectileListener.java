package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.arena.ArenaInstance;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.TriggerRegistry;
import com.borderrank.battle.model.Loadout;
import com.borderrank.battle.model.TriggerData;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Handles projectile-based trigger effects:
 * - Meteora (crossbow): Explosion on bolt impact
 * - Hound (trident): Homing toward nearest enemy
 * - Lightning (bow): Piercing arrows that pass through enemies
 */
public class ProjectileListener implements Listener {

    private static final String META_TRIGGER_ID = "brb_trigger_id";
    private static final String META_SHOOTER_UUID = "brb_shooter_uuid";
    private static final String META_DAMAGE_MULTIPLIER = "brb_damage_multiplier";
    private final Map<UUID, BukkitTask> homingTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> curveTasks = new HashMap<>();
    // Track bow draw start time for Egret/Ibis charge mechanic
    private final Map<UUID, Long> bowDrawStartTime = new HashMap<>();

    /**
     * Track bow draw start time for Egret/Ibis charge mechanic.
     */
    @EventHandler
    public void onBowDraw(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!event.getAction().name().contains("RIGHT")) return;
        Player player = event.getPlayer();
        if (event.getItem() == null || event.getItem().getType() != org.bukkit.Material.BOW) return;

        BRBPlugin plugin = BRBPlugin.getInstance();
        if (!plugin.getMatchManager().isInMatch(player.getUniqueId())) return;

        String triggerId = getShooterTriggerId(player);
        if ("egret".equals(triggerId) || "ibis".equals(triggerId)) {
            bowDrawStartTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) return;

        BRBPlugin plugin = BRBPlugin.getInstance();
        if (!plugin.getMatchManager().isInMatch(shooter.getUniqueId())) return;

        // Determine which trigger the player is using
        String triggerId = getShooterTriggerId(shooter);
        if (triggerId == null) return;

        // Tag the projectile with trigger info
        projectile.setMetadata(META_TRIGGER_ID, new FixedMetadataValue(plugin, triggerId));
        projectile.setMetadata(META_SHOOTER_UUID, new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));

        // Handle trigger-specific launch effects
        switch (triggerId) {
            case "hound" -> {
                if (projectile instanceof Trident trident) {
                    startHomingTask(trident, shooter);
                }
            }
            case "viper" -> {
                if (projectile instanceof Arrow) {
                    startCurveTask(projectile, shooter, plugin);
                }
            }
            case "egret" -> {
                // Check charge time: 2+ seconds for 2.5x damage
                Long drawStart = bowDrawStartTime.remove(shooter.getUniqueId());
                if (drawStart != null) {
                    long chargeMs = System.currentTimeMillis() - drawStart;
                    if (chargeMs >= 2000) {
                        projectile.setMetadata(META_DAMAGE_MULTIPLIER, new FixedMetadataValue(plugin, 2.5));
                        shooter.sendActionBar(ChatColor.AQUA + "エグレット: フルチャージ！ (2.5x)");
                    } else {
                        shooter.sendActionBar(ChatColor.GRAY + "エグレット: チャージ不足 (" + String.format("%.1f", chargeMs / 1000.0) + "/2.0秒)");
                    }
                }
            }
            case "ibis" -> {
                // Check charge time: 3+ seconds for 3.0x damage
                Long drawStart = bowDrawStartTime.remove(shooter.getUniqueId());
                if (drawStart != null) {
                    long chargeMs = System.currentTimeMillis() - drawStart;
                    if (chargeMs >= 3000) {
                        projectile.setMetadata(META_DAMAGE_MULTIPLIER, new FixedMetadataValue(plugin, 3.0));
                        shooter.sendActionBar(ChatColor.RED + "" + ChatColor.BOLD + "アイビス: フルチャージ！！ (3.0x)");
                    } else {
                        shooter.sendActionBar(ChatColor.GRAY + "アイビス: チャージ不足 (" + String.format("%.1f", chargeMs / 1000.0) + "/3.0秒)");
                    }
                }
            }
            case "asteroid" -> {
                // Asteroid: 1.2x damage multiplier
                projectile.setMetadata(META_DAMAGE_MULTIPLIER, new FixedMetadataValue(plugin, 1.2));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) return;

        if (!projectile.hasMetadata(META_TRIGGER_ID)) return;
        String triggerId = projectile.getMetadata(META_TRIGGER_ID).get(0).asString();

        BRBPlugin plugin = BRBPlugin.getInstance();

        switch (triggerId) {
            case "meteora" -> handleMeteoraImpact(projectile, shooter, plugin);
            case "meteora_sub" -> handleMeteoraSubImpact(projectile, shooter, plugin);
            case "hound" -> stopHomingTask(projectile.getUniqueId());
            case "viper" -> stopCurveTask(projectile.getUniqueId());
            case "lightning" -> handleLightningPiercing(projectile, shooter, event, plugin);
        }
    }

    /**
     * Meteora (shooter crossbow): Create explosion on bolt impact (no block damage).
     */
    private void handleMeteoraImpact(Projectile projectile, Player shooter, BRBPlugin plugin) {
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(shooter.getUniqueId());
        if (match == null) return;

        Location impactLoc = projectile.getLocation();
        // Explosion without block damage (shooter weapon)
        projectile.getWorld().createExplosion(impactLoc, 2.0F, false, false, shooter);
        shooter.sendActionBar(ChatColor.RED + "メテオラ着弾！");
        projectile.remove();
    }

    /**
     * Meteora Sub (support): Thrown explosive with terrain destruction.
     */
    private void handleMeteoraSubImpact(Projectile projectile, Player shooter, BRBPlugin plugin) {
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(shooter.getUniqueId());
        if (match == null) return;

        Location impactLoc = projectile.getLocation();
        // Explosion WITH block damage (setBlockDamage = true)
        projectile.getWorld().createExplosion(impactLoc, 3.0F, false, true, shooter);
        shooter.sendActionBar(ChatColor.RED + "メテオラ・サブ着弾！ 地形破壊！");
        projectile.remove();
    }

    /**
     * Hound: Start tracking task that adjusts trident trajectory toward nearest enemy.
     */
    private void startHomingTask(Trident trident, Player shooter) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(shooter.getUniqueId());
        if (match == null) return;

        double homingRange = 20.0;
        double homingStrength = 0.15;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (trident.isDead() || trident.isOnGround() || !trident.isValid()) {
                stopHomingTask(trident.getUniqueId());
                return;
            }

            // Find nearest enemy player within range
            Player nearestTarget = null;
            double nearestDist = homingRange;

            for (UUID aliveUuid : match.getAlivePlayers()) {
                if (aliveUuid.equals(shooter.getUniqueId())) continue;
                if (match.isTeammate(shooter.getUniqueId(), aliveUuid)) continue;

                Player target = Bukkit.getPlayer(aliveUuid);
                if (target == null || !target.isOnline() || target.isDead()) continue;

                double dist = trident.getLocation().distance(target.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestTarget = target;
                }
            }

            if (nearestTarget != null) {
                // Adjust velocity toward target
                Vector toTarget = nearestTarget.getLocation().add(0, 1.0, 0)
                        .toVector().subtract(trident.getLocation().toVector()).normalize();
                Vector currentVel = trident.getVelocity();
                double speed = currentVel.length();

                // Blend current velocity with target direction
                Vector newVel = currentVel.normalize().multiply(1.0 - homingStrength)
                        .add(toTarget.multiply(homingStrength))
                        .normalize()
                        .multiply(speed);

                trident.setVelocity(newVel);
            }
        }, 1L, 1L); // Every tick

        homingTasks.put(trident.getUniqueId(), task);
    }

    private void stopHomingTask(UUID projectileUuid) {
        BukkitTask task = homingTasks.remove(projectileUuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Lightning: Arrow pierces through entities (passes through and continues).
     */
    private void handleLightningPiercing(Projectile projectile, Player shooter, ProjectileHitEvent event, BRBPlugin plugin) {
        if (event.getHitEntity() == null) return;
        if (!(event.getHitEntity() instanceof LivingEntity)) return;

        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(shooter.getUniqueId());
        if (match == null) return;

        // Check how many entities this arrow has already pierced
        int pierceCount = 0;
        if (projectile.hasMetadata("brb_pierce_count")) {
            pierceCount = projectile.getMetadata("brb_pierce_count").get(0).asInt();
        }

        if (pierceCount >= 3) return; // Max 3 pierces

        // Cancel the hit event so the arrow continues
        event.setCancelled(true);

        // Apply damage manually to the hit entity
        if (event.getHitEntity() instanceof Player victim) {
            if (!match.isTeammate(shooter.getUniqueId(), victim.getUniqueId())) {
                victim.damage(6.0, shooter); // Lightning base damage
                shooter.sendActionBar(ChatColor.YELLOW + "ライトニング貫通！ (" + (pierceCount + 1) + "/3)");
            }
        }

        // Increment pierce count
        projectile.setMetadata("brb_pierce_count", new FixedMetadataValue(plugin, pierceCount + 1));
    }

    /**
     * Viper: Start a curve task that applies lateral velocity to the arrow.
     * Arrow curves in a horizontal arc relative to the shooter's facing direction.
     */
    private void startCurveTask(Projectile projectile, Player shooter, BRBPlugin plugin) {
        // Calculate curve direction (perpendicular to shooter's facing, horizontal)
        Vector facing = shooter.getLocation().getDirection().setY(0).normalize();
        // Curve right by default (cross product with up vector)
        Vector curveDir = new Vector(-facing.getZ(), 0, facing.getX()).normalize();
        double curveStrength = 0.08;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (projectile.isDead() || projectile.isOnGround() || !projectile.isValid()) {
                stopCurveTask(projectile.getUniqueId());
                return;
            }
            // Apply lateral force
            Vector vel = projectile.getVelocity();
            vel.add(curveDir.clone().multiply(curveStrength));
            projectile.setVelocity(vel);
        }, 2L, 1L); // Start after 2 ticks, run every tick

        curveTasks.put(projectile.getUniqueId(), task);
    }

    private void stopCurveTask(UUID projectileUuid) {
        BukkitTask task = curveTasks.remove(projectileUuid);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Determine which trigger ID the player is currently using (based on held slot).
     */
    private String getShooterTriggerId(Player player) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        int heldSlot = player.getInventory().getHeldItemSlot();
        Loadout loadout = loadoutManager.getLoadout(player.getUniqueId(), "default");
        if (loadout == null) return null;
        List<String> slots = loadout.getSlots();
        if (heldSlot >= slots.size()) return null;
        String triggerId = slots.get(heldSlot);
        return (triggerId != null && !triggerId.isEmpty()) ? triggerId : null;
    }

    /**
     * Clean up all homing tasks (call on plugin disable).
     */
    public void cleanup() {
        for (BukkitTask task : homingTasks.values()) {
            task.cancel();
        }
        homingTasks.clear();
        for (BukkitTask task : curveTasks.values()) {
            task.cancel();
        }
        curveTasks.clear();
        bowDrawStartTime.clear();
    }
}
