package com.butai.rankbattle.listener;

import com.butai.rankbattle.manager.LobbyManager;
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
 * - Prevent damage to NPC villagers
 */
public class LobbyListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager)) return;

        String command = entity.getPersistentDataContainer()
                .get(LobbyManager.NPC_KEY, PersistentDataType.STRING);
        if (command == null || command.isEmpty()) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        player.performCommand(command);
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
