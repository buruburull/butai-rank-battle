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
    private final Map<UUID, BukkitTask> homingTasks = new HashMap<>();

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

        // Handle Hound homing effect
        if ("hound".equals(triggerId) && projectile instanceof Trident trident) {
            startHomingTask(trident, shooter);
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
            case "hound" -> stopHomingTask(projectile.getUniqueId());
            case "lightning" -> handleLightningPiercing(projectile, shooter, event, plugin);
        }
    }

    /**
     * Meteora: Create explosion on crossbow bolt impact.
     */
    private void handleMeteoraImpact(Projectile projectile, Player shooter, BRBPlugin plugin) {
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(shooter.getUniqueId());
        if (match == null) return;

        Location impactLoc = projectile.getLocation();
        // Create explosion (power 2.0, no fire, no block damage)
        projectile.getWorld().createExplosion(impactLoc, 2.0F, false, false, shooter);
        shooter.sendActionBar(ChatColor.RED + "メテオラ着弾！");
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
    }
}
