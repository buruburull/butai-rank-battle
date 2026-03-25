package com.butai.rankbattle.gui;

import com.butai.rankbattle.manager.ShopManager;
import com.butai.rankbattle.manager.ShopManager.ShopItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Handles click events for the Shop GUI.
 */
public class ShopGUIListener implements Listener {

    private final ShopGUI gui;
    private final ShopManager shopManager;

    public ShopGUIListener(ShopGUI gui, ShopManager shopManager) {
        this.gui = gui;
        this.shopManager = shopManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isShopGUI(event.getView().getTitle())) return;

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 54) return;

        String itemId = gui.getItemIdAtSlot(rawSlot);
        if (itemId == null) return;

        boolean purchased = false;

        // Handle permanent upgrades
        if (itemId.equals("upgrade_ether_cap")) {
            purchased = shopManager.purchaseUpgrade(player, ShopManager.UPGRADE_ETHER_CAP);
        } else if (itemId.equals("upgrade_tower_hp")) {
            purchased = shopManager.purchaseUpgrade(player, ShopManager.UPGRADE_TOWER_HP);
        } else {
            // Handle consumable/skill items
            ShopItem shopItem = findShopItem(itemId);
            if (shopItem != null) {
                if (shopItem.type() == ShopManager.ShopItemType.SKILL) {
                    purchased = shopManager.purchaseSkillItem(player, shopItem);
                } else {
                    purchased = shopManager.purchaseConsumable(player, shopItem);
                }
            }
        }

        if (purchased) {
            refreshGUI(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isShopGUI(event.getView().getTitle())) return;
        event.setCancelled(true);
    }

    private ShopItem findShopItem(String id) {
        for (ShopItem item : ShopManager.CONSUMABLE_ITEMS) {
            if (item.id().equals(id)) return item;
        }
        for (ShopItem item : ShopManager.SKILL_ITEMS) {
            if (item.id().equals(id)) return item;
        }
        return null;
    }

    private void refreshGUI(Player player) {
        org.bukkit.inventory.Inventory newInv = gui.buildInventory(player);
        org.bukkit.inventory.Inventory topInv = player.getOpenInventory().getTopInventory();
        topInv.setContents(newInv.getContents());
    }

    private boolean isShopGUI(String title) {
        return ShopGUI.GUI_TITLE.equals(title);
    }
}
