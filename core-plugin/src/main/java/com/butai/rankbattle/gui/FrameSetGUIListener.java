package com.butai.rankbattle.gui;

import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.model.FrameData;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles click events for the FrameSet GUI.
 * Flow:
 *   1. Click frame in bottom section → select it
 *   2. Click slot in top section → equip selected frame to that slot
 *   3. Right-click slot in top section → remove frame from slot
 */
public class FrameSetGUIListener implements Listener {

    private final FrameSetGUI gui;
    private final FrameSetManager frameSetManager;
    private final FrameRegistry frameRegistry;
    private final FrameCommand frameCommand;

    // Currently selected frame per player
    private final Map<UUID, String> selectedFrame = new HashMap<>();

    public FrameSetGUIListener(FrameSetGUI gui, FrameSetManager frameSetManager,
                                FrameRegistry frameRegistry, FrameCommand frameCommand) {
        this.gui = gui;
        this.frameSetManager = frameSetManager;
        this.frameRegistry = frameRegistry;
        this.frameCommand = frameCommand;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isFrameSetGUI(event.getView().getTitle())) return;

        event.setCancelled(true);

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= 54) return;

        // Row 0 (slots 0-8): Frameset slot area
        int fsSlot = gui.getFrameSetSlot(rawSlot);
        if (fsSlot > 0) {
            if (event.isRightClick()) {
                // Right click: remove frame
                handleSlotRemove(player, fsSlot);
            } else {
                // Left click: equip selected frame
                handleSlotEquip(player, fsSlot);
            }
            return;
        }

        // Row 2+ (slots 18-53): Frame selection area
        if (rawSlot >= 18) {
            handleFrameSelect(player, rawSlot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isFrameSetGUI(event.getView().getTitle())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isFrameSetGUI(event.getView().getTitle())) return;

        selectedFrame.remove(player.getUniqueId());
    }

    // === Handlers ===

    /**
     * Player clicked a frame icon in the selection area.
     */
    private void handleFrameSelect(Player player, int rawSlot) {
        UUID uuid = player.getUniqueId();

        // Check if this slot has a frame (via PDC on the item)
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack item = inv.getItem(rawSlot);
        String frameId = getFrameIdFromItem(item);
        if (frameId == null) return;

        FrameData data = frameRegistry.getFrame(frameId);
        if (data == null) return;

        // Check if already equipped
        String[] frameSet = frameSetManager.getFrameSet(uuid);
        for (String fid : frameSet) {
            if (frameId.equals(fid)) {
                MessageUtil.sendError(player, "§c" + data.getName() + " §7は既に装備中です。");
                return;
            }
        }

        // Select the frame
        selectedFrame.put(uuid, frameId);
        MessageUtil.sendInfo(player, "§e" + data.getName() + " §7を選択しました。上のスロットをクリックして装備してください。");

        // Refresh GUI to show selection highlight
        refreshGUI(player);
    }

    /**
     * Player left-clicked a frameset slot to equip the selected frame.
     */
    private void handleSlotEquip(Player player, int fsSlot) {
        UUID uuid = player.getUniqueId();
        String frameId = selectedFrame.get(uuid);

        if (frameId == null) {
            MessageUtil.sendError(player, "先に下のフレームをクリックして選択してください。");
            return;
        }

        // Use existing validation in FrameSetManager
        String error = frameSetManager.setFrame(uuid, fsSlot, frameId);
        if (error != null) {
            MessageUtil.sendError(player, error);
            return;
        }

        FrameData data = frameRegistry.getFrame(frameId);
        String label = fsSlot <= 4 ? "メイン" + fsSlot : "サブ" + (fsSlot - 4);
        MessageUtil.sendSuccess(player, "§f" + label + " §7に "
                + data.getCategory().getColor() + data.getName() + " §7を装備しました。");

        // Clear selection and refresh
        selectedFrame.remove(uuid);
        refreshGUI(player);
        frameCommand.refreshHotbar(player);
    }

    /**
     * Player right-clicked a frameset slot to remove the frame.
     */
    private void handleSlotRemove(Player player, int fsSlot) {
        UUID uuid = player.getUniqueId();

        String error = frameSetManager.removeFrame(uuid, fsSlot);
        if (error != null) {
            MessageUtil.sendError(player, error);
            return;
        }

        String label = fsSlot <= 4 ? "メイン" + fsSlot : "サブ" + (fsSlot - 4);
        MessageUtil.sendSuccess(player, "§f" + label + " §7のフレームを解除しました。");

        player.getInventory().setItem(fsSlot - 1, null);
        refreshGUI(player);
        frameCommand.refreshHotbar(player);
    }

    // === Utility ===

    private void refreshGUI(Player player) {
        UUID uuid = player.getUniqueId();
        String selected = selectedFrame.get(uuid);
        Inventory newInv = gui.buildInventory(player, selected);

        // Update contents of the open inventory
        Inventory topInv = player.getOpenInventory().getTopInventory();
        topInv.setContents(newInv.getContents());
    }

    private boolean isFrameSetGUI(String title) {
        return FrameSetGUI.GUI_TITLE.equals(title);
    }

    private String getFrameIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(FrameCommand.FRAME_KEY, PersistentDataType.STRING);
    }
}
