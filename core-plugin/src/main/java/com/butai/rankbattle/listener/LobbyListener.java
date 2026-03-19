package com.butai.rankbattle.listener;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.manager.EtherGrowthManager;
import com.butai.rankbattle.manager.LobbyManager;
import com.butai.rankbattle.manager.MineManager;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles NPC interactions in the lobby and growth zones.
 * - Right-click on NPC villager → execute stored command
 * - Right-click on Growth NPC → teleport to mine/mob tower
 * - Right-click on Floor NPC → move to next floor (level check)
 * - Prevent damage to NPC villagers
 */
public class LobbyListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Villager)) return;

        // Check for growth teleport NPC
        String teleportTo = entity.getPersistentDataContainer()
                .get(LobbyManager.GROWTH_TELEPORT_KEY, PersistentDataType.STRING);
        if (teleportTo != null && !teleportTo.isEmpty()) {
            event.setCancelled(true);
            handleGrowthTeleport(event.getPlayer(), teleportTo);
            return;
        }

        // Check for floor NPC (next floor / previous floor)
        String floorAction = entity.getPersistentDataContainer()
                .get(LobbyManager.FLOOR_ACTION_KEY, PersistentDataType.STRING);
        if (floorAction != null && !floorAction.isEmpty()) {
            event.setCancelled(true);
            handleFloorAction(event.getPlayer(), floorAction);
            return;
        }

        String command = entity.getPersistentDataContainer()
                .get(LobbyManager.NPC_KEY, PersistentDataType.STRING);
        if (command == null || command.isEmpty()) return;

        event.setCancelled(true);
        event.getPlayer().performCommand(command);
    }

    private void handleGrowthTeleport(Player player, String destination) {
        BRBPlugin plugin = BRBPlugin.getInstance();

        if ("lobby".equals(destination)) {
            // Return to lobby - restore inventory
            EtherGrowthManager gm = plugin.getEtherGrowthManager();
            if (gm != null && gm.isInTower(player.getUniqueId())) {
                gm.exitTower(player);
            }
            // Remove mine pickaxe if present
            player.getInventory().remove(Material.IRON_PICKAXE);

            LobbyManager lobbyManager = plugin.getLobbyManager();
            if (lobbyManager != null) {
                lobbyManager.sendToLobby(player);
            }
            MessageUtil.sendInfo(player, "§7ロビーに戻りました。");
            return;
        }

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
            player.setGameMode(GameMode.SURVIVAL);

            if ("mine".equals(destination)) {
                if (!player.getInventory().contains(Material.IRON_PICKAXE)) {
                    player.getInventory().addItem(new ItemStack(Material.IRON_PICKAXE));
                }
            } else if ("mob_tower".equals(destination)) {
                // Save inventory, clear, give iron sword
                EtherGrowthManager gm = plugin.getEtherGrowthManager();
                if (gm != null) {
                    gm.enterTower(player);
                }
                // Show floor info
                if (gm != null && gm.getFloorCount() > 0) {
                    EtherGrowthManager.FloorData floor = gm.getFloor(0);
                    if (floor != null) {
                        MessageUtil.sendInfo(player, floor.name + " §7に入場しました。");
                    }
                }
            }

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

    /**
     * Handle floor NPC actions: "next_X" to go to floor X, "prev_X" to go back.
     */
    private void handleFloorAction(Player player, String action) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        EtherGrowthManager gm = plugin.getEtherGrowthManager();
        if (gm == null) return;

        int targetFloorIndex;
        if (action.startsWith("next_")) {
            targetFloorIndex = Integer.parseInt(action.substring(5));
        } else if (action.startsWith("prev_")) {
            targetFloorIndex = Integer.parseInt(action.substring(5));
        } else {
            return;
        }

        EtherGrowthManager.FloorData targetFloor = gm.getFloor(targetFloorIndex);
        if (targetFloor == null) {
            MessageUtil.sendError(player, "この階は存在しません。");
            return;
        }

        // Level check
        int playerLevel = gm.getGrowthLevel(player.getUniqueId());
        if (playerLevel < targetFloor.requiredLevel) {
            MessageUtil.sendError(player, "§c" + targetFloor.name + " §7にはエーテルLv." + targetFloor.requiredLevel + "が必要です。（現在: Lv." + playerLevel + "）");
            return;
        }

        // Teleport to target floor
        if (targetFloor.playerSpawn != null) {
            player.teleport(targetFloor.playerSpawn);
            MessageUtil.sendInfo(player, targetFloor.name + " §7に移動しました。");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Villager)) return;

        if (entity.getPersistentDataContainer().has(LobbyManager.NPC_KEY, PersistentDataType.STRING) ||
            entity.getPersistentDataContainer().has(LobbyManager.FLOOR_ACTION_KEY, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }
}
