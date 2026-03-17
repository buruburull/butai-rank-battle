package com.butai.rankbattle.gui;

import com.butai.rankbattle.command.FrameCommand;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.model.FrameCategory;
import com.butai.rankbattle.model.FrameData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Frameset configuration GUI (54-slot chest).
 *
 * Layout:
 *   Row 0: [deco] [Slot1*] [Slot2] [Slot3] [Slot4] [deco] [Slot5] [Slot6] [Slot7] [Slot8 -> col8 not exist in 9-wide]
 *   Actually 9 columns: use slots 1-4 for main, 5-8 for sub
 *
 *   Row 0 (0-8):  [deco] [S1] [S2] [S3] [S4] [deco] [S5] [S6] [S7] [S8]  -- S8 is col 8 but row is 0-8 only 9 slots
 *   Correction: 54 slots = 6 rows x 9 cols
 *   Row 0: col0=deco, col1=Slot1, col2=Slot2, col3=Slot3, col4=Slot4, col5=deco, col6=Slot5, col7=Slot6, col8=Slot7
 *   Row 1: col0=Slot8, col1-8=separator
 *   Better layout:
 *   Row 0: [S1] [S2] [S3] [S4] [deco] [S5] [S6] [S7] [S8]
 *   Row 1: separator x9
 *   Row 2-4: frames by category
 *   Row 5: separator x9
 */
public class FrameSetGUI {

    public static final String GUI_TITLE = "§0§lフレームセット設定";

    // Row 0: frameset slots mapping (inventory slot -> frameset slot 1-8)
    public static final int[] FRAMESET_INV_SLOTS = {0, 1, 2, 3, -1, 5, 6, 7, 8};
    // frameset slot index (1-8) -> inventory slot
    public static final Map<Integer, Integer> SLOT_TO_INV = Map.of(
            1, 0, 2, 1, 3, 2, 4, 3,
            5, 5, 6, 6, 7, 7, 8, 8
    );

    private final FrameRegistry frameRegistry;
    private final FrameSetManager frameSetManager;

    // Maps inventory slot -> frameId for the frame selection area
    private final Map<Integer, String> frameSlotMap = new LinkedHashMap<>();

    public FrameSetGUI(FrameRegistry frameRegistry, FrameSetManager frameSetManager) {
        this.frameRegistry = frameRegistry;
        this.frameSetManager = frameSetManager;
        buildFrameSlotMap();
    }

    /**
     * Build mapping of inventory slots to frame IDs for the selection area (rows 2-4).
     */
    private void buildFrameSlotMap() {
        frameSlotMap.clear();
        int slot = 18; // Row 2, col 0

        for (FrameCategory category : FrameCategory.values()) {
            List<FrameData> frames = frameRegistry.getFramesByCategory(category);

            // Category label at current position
            slot++; // skip label slot (we'll handle it separately)

            for (FrameData frame : frames) {
                if (slot % 9 == 0 && slot > 18) {
                    // Skip to next row if needed
                }
                frameSlotMap.put(slot, frame.getId());
                slot++;
            }

            // Pad to next section with gap
            while (slot % 9 != 0) {
                slot++;
            }
        }
    }

    public void openGUI(Player player) {
        Inventory inv = buildInventory(player, null);
        player.openInventory(inv);
    }

    public Inventory buildInventory(Player player, String selectedFrameId) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        UUID uuid = player.getUniqueId();
        String[] frameSet = frameSetManager.getFrameSet(uuid);

        // Collect equipped frame IDs for marking
        Set<String> equippedIds = new HashSet<>();
        for (String fid : frameSet) {
            if (fid != null) equippedIds.add(fid);
        }

        // === Row 0: Frameset slots ===
        for (int fsSlot = 1; fsSlot <= 8; fsSlot++) {
            int invSlot = SLOT_TO_INV.get(fsSlot);
            String frameId = frameSet[fsSlot - 1];

            if (frameId != null) {
                FrameData data = frameRegistry.getFrame(frameId);
                if (data != null) {
                    inv.setItem(invSlot, createEquippedSlotItem(data, fsSlot));
                    continue;
                }
            }
            inv.setItem(invSlot, createEmptySlotItem(fsSlot));
        }

        // Separator in row 0 (col 4)
        inv.setItem(4, createSeparator());

        // === Row 1: Separator ===
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, createSeparator());
        }

        // === Rows 2-4: Frame selection by category ===
        int invSlot = 18;
        for (FrameCategory category : FrameCategory.values()) {
            List<FrameData> frames = frameRegistry.getFramesByCategory(category);

            // Category label
            inv.setItem(invSlot, createCategoryLabel(category));
            invSlot++;

            for (FrameData frame : frames) {
                boolean equipped = equippedIds.contains(frame.getId());
                boolean selected = frame.getId().equals(selectedFrameId);
                inv.setItem(invSlot, createFrameIcon(frame, equipped, selected));
                invSlot++;
            }

            // Pad remaining columns with separator
            while (invSlot % 9 != 0) {
                inv.setItem(invSlot, createSeparator());
                invSlot++;
            }
        }

        // === Row 5: Separator (if space remains) ===
        for (int i = invSlot; i < 54; i++) {
            inv.setItem(i, createSeparator());
        }

        return inv;
    }

    /**
     * Get the frameset slot number (1-8) from an inventory slot, or -1 if not a slot.
     */
    public int getFrameSetSlot(int invSlot) {
        for (Map.Entry<Integer, Integer> entry : SLOT_TO_INV.entrySet()) {
            if (entry.getValue() == invSlot) return entry.getKey();
        }
        return -1;
    }

    /**
     * Get the frame ID from an inventory slot in the selection area, or null.
     */
    public String getFrameIdAtSlot(int invSlot) {
        return frameSlotMap.get(invSlot);
    }

    /**
     * Rebuild the frameSlotMap (call after frame registry reload).
     */
    public void rebuildFrameSlotMap() {
        buildFrameSlotMap();
    }

    // === Item Creators ===

    private ItemStack createEquippedSlotItem(FrameData data, int fsSlot) {
        Material material;
        try {
            material = Material.valueOf(data.getMcItem());
        } catch (IllegalArgumentException e) {
            material = Material.IRON_INGOT;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String label = fsSlot <= 4 ? "§eメイン" + fsSlot : "§bサブ" + (fsSlot - 4);
            meta.setDisplayName(data.getCategory().getColor() + "§l" + data.getName()
                    + " §8[" + label + "§8]");

            List<String> lore = new ArrayList<>();
            lore.add("§7" + data.getDescription());
            lore.add("");
            lore.add("§7カテゴリ: " + data.getCategory().getColoredName());
            if (data.getEtherUse() > 0) lore.add("§9エーテル: §f" + data.getEtherUse() + "/回");
            if (data.getEtherSustain() > 0) lore.add("§9持続: §f" + data.getEtherSustain() + "/秒");
            lore.add("");
            lore.add("§e左クリック §7→ フレームを変更");
            lore.add("§c右クリック §7→ フレームを解除");
            meta.setLore(lore);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(FrameCommand.FRAME_KEY, PersistentDataType.STRING, data.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createEmptySlotItem(int fsSlot) {
        Material material = fsSlot == 1 ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String label = fsSlot <= 4 ? "§eメイン" + fsSlot : "§bサブ" + (fsSlot - 4);
            if (fsSlot == 1) {
                meta.setDisplayName("§c§l武器スロット §8[" + label + "§8]");
                meta.setLore(List.of("§c必須: 武器フレームを装備してください", "",
                        "§7下のフレームをクリックして選択後、", "§7このスロットをクリックで装備"));
            } else {
                meta.setDisplayName("§7空きスロット §8[" + label + "§8]");
                meta.setLore(List.of("§7下のフレームをクリックして選択後、",
                        "§7このスロットをクリックで装備"));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createFrameIcon(FrameData data, boolean equipped, boolean selected) {
        Material material;
        try {
            material = Material.valueOf(data.getMcItem());
        } catch (IllegalArgumentException e) {
            material = Material.IRON_INGOT;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String prefix = selected ? "§e§l▶ " : equipped ? "§8[装備中] " : "";
            meta.setDisplayName(prefix + data.getCategory().getColor() + data.getName());

            List<String> lore = new ArrayList<>();
            lore.add("§7" + data.getDescription());
            lore.add("");
            lore.add("§7カテゴリ: " + data.getCategory().getColoredName());
            if (data.getDamage() > 0) lore.add("§7ダメージ: §f" + data.getDamage());
            if (data.getDamageMultiplier() != 1.0) lore.add("§7倍率: §f" + data.getDamageMultiplier() + "x");
            if (data.getEtherUse() > 0) lore.add("§9エーテル: §f" + data.getEtherUse() + "/回");
            if (data.getEtherSustain() > 0) lore.add("§9持続: §f" + data.getEtherSustain() + "/秒");
            if (data.getCooldown() > 0) lore.add("§7CT: §f" + data.getCooldown() + "秒");
            lore.add("");
            if (equipped) {
                lore.add("§8すでに装備中です");
            } else if (selected) {
                lore.add("§e選択中 §7→ 上のスロットをクリック");
            } else {
                lore.add("§eクリックで選択");
            }
            meta.setLore(lore);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            if (selected) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            }

            meta.getPersistentDataContainer().set(FrameCommand.FRAME_KEY, PersistentDataType.STRING, data.getId());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCategoryLabel(FrameCategory category) {
        Material glass = switch (category) {
            case STRIKER -> Material.RED_STAINED_GLASS_PANE;
            case GUNNER -> Material.YELLOW_STAINED_GLASS_PANE;
            case MARKSMAN -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case SUPPORT -> Material.LIME_STAINED_GLASS_PANE;
        };
        ItemStack item = new ItemStack(glass);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(category.getColor() + "§l" + category.getDisplayName());
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSeparator() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }
}
