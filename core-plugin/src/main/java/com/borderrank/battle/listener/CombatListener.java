package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.arena.ArenaInstance;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.TriggerRegistry;
import com.borderrank.battle.model.Loadout;
import com.borderrank.battle.model.TriggerData;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CombatListener implements Listener {

    private static final Set<UUID> matchDeaths = new HashSet<>();

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        BRBPlugin plugin = BRBPlugin.getInstance();

        // Handle direct melee damage from player
        if (event.getDamager() instanceof Player attacker) {
            ArenaInstance match = plugin.getMatchManager().getPlayerMatch(attacker.getUniqueId());
            if (match == null) return;

            // Friendly fire prevention
            if (match.isTeammate(attacker.getUniqueId(), victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            double damage = event.getDamage();

            // Backstab check - enhanced for Scorpion
            if (isBehind(attacker, victim)) {
                String heldTriggerId = getHeldTriggerId(attacker);
                if ("scorpion".equals(heldTriggerId)) {
                    // Scorpion: 1.5x base backstab * 1.5x Scorpion bonus = 2.25x
                    damage *= 2.25;
                    MessageUtil.sendSuccessMessage(attacker, ChatColor.GOLD + "スコーピオン・バックスタブ！！");
                } else {
                    // Normal backstab: 1.5x
                    damage *= 1.5;
                    MessageUtil.sendSuccessMessage(attacker, "バックスタブ！");
                }
            }

            event.setDamage(damage);
        }

        // Handle Red Bullet (SpectralArrow) hit - apply Glowing effect
        if (event.getDamager() instanceof SpectralArrow spectralArrow) {
            if (spectralArrow.getShooter() instanceof Player shooter) {
                ArenaInstance match = plugin.getMatchManager().getPlayerMatch(shooter.getUniqueId());
                if (match == null) return;

                // Friendly fire prevention
                if (match.isTeammate(shooter.getUniqueId(), victim.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }

                // Apply Glowing effect for 5 seconds (100 ticks)
                victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false, true));
                MessageUtil.sendSuccessMessage(shooter, ChatColor.RED + "レッドバレット命中！ " + victim.getName() + " をマーキング！");
                MessageUtil.sendErrorMessage(victim, ChatColor.RED + "レッドバレットでマーキングされた！");
            }
        }

        // Handle Arrow damage from players (for sniper damage multiplier etc.)
        if (event.getDamager() instanceof Arrow arrow) {
            if (arrow.getShooter() instanceof Player shooter) {
                ArenaInstance match = plugin.getMatchManager().getPlayerMatch(shooter.getUniqueId());
                if (match == null) return;

                if (match.isTeammate(shooter.getUniqueId(), victim.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        BRBPlugin plugin = BRBPlugin.getInstance();
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(victim.getUniqueId());
        if (match == null) return;
        match.onKill(killer != null ? killer.getUniqueId() : null, victim.getUniqueId());
        event.getDrops().clear();
        event.setDroppedExp(0);
        matchDeaths.add(victim.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!matchDeaths.remove(player.getUniqueId())) return;
        event.setRespawnLocation(player.getWorld().getSpawnLocation());
        BRBPlugin plugin = BRBPlugin.getInstance();
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0);
            MessageUtil.sendInfoMessage(player, "ベイルアウト！ロビーに戻りました。");
        }, 1L);
    }

    @EventHandler
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (plugin.getMatchManager().isInMatch(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Get the trigger ID the player is currently holding.
     */
    private String getHeldTriggerId(Player player) {
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

    private boolean isBehind(Player attacker, Player victim) {
        float victimYaw = victim.getLocation().getYaw();
        double dx = attacker.getLocation().getX() - victim.getLocation().getX();
        double dz = attacker.getLocation().getZ() - victim.getLocation().getZ();
        double attackerAngle = Math.atan2(dz, dx) * 180 / Math.PI;
        victimYaw = (victimYaw % 360 + 360) % 360;
        attackerAngle = (attackerAngle % 360 + 360) % 360;
        double angleDiff = Math.abs(victimYaw - attackerAngle);
        if (angleDiff > 180) angleDiff = 360 - angleDiff;
        return angleDiff > 90;
    }
}
