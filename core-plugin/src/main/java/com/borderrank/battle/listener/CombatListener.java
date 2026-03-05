package com.borderrank.battle.listener;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.arena.ArenaInstance;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CombatListener implements Listener {

    private static final Set<UUID> matchDeaths = new HashSet<>();

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        BRBPlugin plugin = BRBPlugin.getInstance();
        if (!plugin.getMatchManager().isInMatch(attacker.getUniqueId())) return;
        double damage = event.getDamage();
        if (isBehind(attacker, victim)) {
            damage *= 1.5;
            MessageUtil.sendSuccessMessage(attacker, "バックスタブ！");
        }
        event.setDamage(damage);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        BRBPlugin plugin = BRBPlugin.getInstance();
        ArenaInstance match = plugin.getMatchManager().getPlayerMatch(victim.getUniqueId());
        if (match == null) return;

        // Record kill in match
        match.onKill(killer != null ? killer.getUniqueId() : null, victim.getUniqueId());

        // Clean up drops only
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Mark this player for hub teleport on respawn
        matchDeaths.add(victim.getUniqueId());

        // Do NOT auto-respawn. Let player press the button.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!matchDeaths.remove(player.getUniqueId())) return;

        // Set respawn location to world spawn (hub)
        event.setRespawnLocation(player.getWorld().getSpawnLocation());

        // Clean up player state after 1 tick
        BRBPlugin plugin = BRBPlugin.getInstance();
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0);
            MessageUtil.sendInfoMessage(player, "ベイルアウト！ロビーに戻りました。");
        }, 1L);
    }

    public static boolean isMatchDeath(UUID uuid) {
        return matchDeaths.contains(uuid);
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
