package com.butai.rankbattle.listener;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.manager.EtherGrowthManager;
import com.butai.rankbattle.manager.LobbyManager;
import com.butai.rankbattle.manager.MineManager;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles NPC interactions in the lobby.
 * - Right-click on NPC villager → execute stored command
 * - Right-click on Growth NPC → teleport to mine/mob tower
 * - Prevent damage to NPC villagers
 */
public class LobbyListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager)) return;

        // Check for growth teleport NPC first
        String teleportTo = entity.getPersistentDataContainer()
                .get(LobbyManager.GROWTH_TELEPORT_KEY, PersistentDataType.STRING);
        if (teleportTo != null && !teleportTo.isEmpty()) {
            event.setCancelled(true);
            handleGrowthTeleport(event.getPlayer(), teleportTo);
            return;
        }

        String command = entity.getPersistentDataContainer()
                .get(LobbyManager.NPC_KEY, PersistentDataType.STRING);
        if (command == null || command.isEmpty()) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        player.performCommand(command);
    }

    private void handleGrowthTeleport(Player player, String destination) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        Location target = null;

        if ("mine".equals(destination)) {
            MineManager mineManager = plugin.getMineManager();
            if (mineManager != null) {
                target = mineManager.getMineSpawn();
            }
        } else if ("mob_tower".equals(destination)) {
            EtherGrowthManager growthManager = plugin.getEtherGrowthManager();
            if (growthManager != null) {
                target = growthManager.getMobTowerSpawn();
            }
        }

        if (target != null) {
            player.teleport(target);
            EtherGrowthManager gm = plugin.getEtherGrowthManager();
            if (gm != null) {
                int level = gm.getGrowthLevel(player.getUniqueId());
                int cap = gm.getEtherCap(player.getUniqueId());
                int ep = gm.getEPTotal(player.getUniqueId());
                int nextEP = gm.getEPForNextLevel(player.getUniqueId());
                String progress = nextEP > 0 ? ep + "/" + nextEP : "MAX";
                MessageUtil.sendInfo(player, "§aエーテルLv." + level + " §8| §bエーテル上限: " + cap + " §8| §7EP: " + progress);
            }
        } else {
            MessageUtil.sendError(player, "テレポート先が設定されていません。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) return;

        // Prevent damage to BRB NPCs
        if (entity.getPersistentDataContainer().has(LobbyManager.NPC_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}
