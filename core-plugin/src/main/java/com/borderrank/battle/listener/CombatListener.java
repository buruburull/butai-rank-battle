package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.arena.ArenaInstance;
import com.borderrank.battle.arena.MatchManager;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.model.Trigger;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.UUID;

/**
 * Listener for combat-related events.
 * Handles damage modification, deaths, and health regeneration.
 */
public class CombatListener implements Listener {

    /**
     * Called when an entity takes damage from another entity.
     * Modifies damage based on equipped triggers.
     */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        MatchManager matchManager = plugin.getMatchManager();

        // Only modify damage in matches
        if (!matchManager.isInMatch(attacker.getUniqueId())) {
            return;
        }

        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        
        // Get attacker's main trigger (slot 0)
        java.util.List<Trigger> loadout = loadoutManager.getLoadout(attacker.getUniqueId());
        if (loadout.isEmpty() || loadout.get(0) == null) {
            return;
        }

        Trigger mainTrigger = loadout.get(0);
        double damage = event.getDamage();

        // Apply trigger-specific damage modifications
        // Example: Scorpion backstab damage (1.5x if behind)
        if ("SCORPION".equalsIgnoreCase(mainTrigger.getId())) {
            if (isBehind(attacker, victim)) {
                damage *= 1.5; // 1.5x backstab damage
                MessageUtil.sendSuccessMessage(attacker, "Backstab!");
            }
        }

        event.setDamage(damage);
    }

    /**
     * Called when a player dies.
     * Records kills and checks match end conditions.
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null) {
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        MatchManager matchManager = plugin.getMatchManager();

        // Check if in match
        ArenaInstance match = matchManager.getPlayerMatch(victim.getUniqueId());
        if (match == null) {
            return;
        }

        // Record the kill
        match.onKill(killer.getUniqueId(), victim.getUniqueId());

        // Clear drops for match deaths
        event.getDrops().clear();
    }

    /**
     * Called when an entity regains health.
     * Cancels natural health regeneration during matches.
     */
    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Only cancel natural regen, allow potion healing
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) {
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        MatchManager matchManager = plugin.getMatchManager();

        // Cancel natural regen during matches
        if (matchManager.isInMatch(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Check if the attacker is behind the victim (for backstab mechanics).
     */
    private boolean isBehind(Player attacker, Player victim) {
        // Get victim's facing direction
        float victimYaw = victim.getLocation().getYaw();
        
        // Get direction from victim to attacker
        double dx = attacker.getLocation().getX() - victim.getLocation().getX();
        double dz = attacker.getLocation().getZ() - victim.getLocation().getZ();
        double attackerAngle = Math.atan2(dz, dx) * 180 / Math.PI;
        
        // Normalize angles
        victimYaw = (victimYaw % 360 + 360) % 360;
        attackerAngle = (attackerAngle % 360 + 360) % 360;
        
        // Check if attacker is within 90 degrees behind victim
        double angleDiff = Math.abs(victimYaw - attackerAngle);
        if (angleDiff > 180) {
            angleDiff = 360 - angleDiff;
        }
        
        return angleDiff > 90; // Behind if angle diff is > 90 degrees
    }
}
