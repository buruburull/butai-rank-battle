package com.butai.rankbattle.gui;

import com.butai.rankbattle.manager.EtherGrowthManager;
import com.butai.rankbattle.manager.ShopManager;
import com.butai.rankbattle.manager.ShopManager.ShopItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Shop GUI (54-slot chest) for the tower shop.
 *
 * Layout:
 *   Row 0: [deco] [shard info] [deco x5] [deco] [deco]
 *   Row 1: separator x9
 *   Row 2: Consumable items (diamond sword, pickaxe, potion, apple, armor)
 *   Row 3: Skill items (power charge, overdrive) + separator
 *   Row 4: Permanent upgrades (ether cap, tower HP)
 *   Row 5: separator x9
 */
public class ShopGUI {

    public static final String GUI_TITLE = "§0§lシャードショップ";

    private final ShopManager shopManager;
    private final EtherGrowthManager growthManager;

    // Maps inventory slot -> shop item id
    private final Map<Integer, String> slotItemMap = new LinkedHashMap<>();

    public ShopGUI(ShopManager shopManager, EtherGrowthManager growthManager) {
        this.shopManager = shopManager;
        this.growthManager = growthManager;
    }

    public void openShop(Player player) {
        Inventory inv = buildInventory(player);
        player.openInventory(inv);
    }

    public Inventory buildInventory(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        UUID uuid = player.getUniqueId();
        slotItemMap.clear();

        int shards = growthManager.getShards(uuid);

        // === Row 0: Header ===
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, createSeparator());
        }
        inv.setItem(4, createShardInfo(shards));

        // === Row 1: Separator with category label ===
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, createSeparator());
        }
        inv.setItem(9, createCategoryLabel("§e§l消耗品", Material.CHEST));

        // === Row 2: Consumable items ===
        int slot = 18;
        for (ShopItem item : ShopManager.CONSUMABLE_ITEMS) {
            inv.setItem(slot, createShopItemIcon(item, shards));
            slotItemMap.put(slot, item.id());
            slot++;
        }
        while (slot < 27) {
            inv.setItem(slot, createSeparator());
            slot++;
        }

        // === Row 3: Skill items ===
        inv.setItem(27, createCategoryLabel("§c§lスキル", Material.BLAZE_POWDER));
        slot = 28;
        for (ShopItem item : ShopManager.SKILL_ITEMS) {
            inv.setItem(slot, createShopItemIcon(item, shards));
            slotItemMap.put(slot, item.id());
            slot++;
        }
        while (slot < 36) {
            inv.setItem(slot, createSeparator());
            slot++;
        }

        // === Row 4: Permanent upgrades ===
        inv.setItem(36, createCategoryLabel("§a§l永続強化", Material.NETHER_STAR));

        // Ether cap upgrade
        int etherCapLevel = growthManager.getUpgradeLevel(uuid, ShopManager.UPGRADE_ETHER_CAP);
        int etherCapPrice = shopManager.getUpgradePrice(uuid, ShopManager.UPGRADE_ETHER_CAP);
        int etherCapMax = ShopManager.ETHER_CAP_MAX_LEVEL;
        inv.setItem(37, createUpgradeIcon("§b§lエーテル上限+10", Material.EXPERIENCE_BOTTLE,
                etherCapLevel, etherCapMax, etherCapPrice, shards,
                "ランクマッチのエーテル上限を+10",
                "現在のボーナス: §b+" + (etherCapLevel * ShopManager.ETHER_CAP_PER_LEVEL)));
        slotItemMap.put(37, "upgrade_ether_cap");

        // Tower HP upgrade
        int towerHpLevel = growthManager.getUpgradeLevel(uuid, ShopManager.UPGRADE_TOWER_HP);
        int towerHpPrice = shopManager.getUpgradePrice(uuid, ShopManager.UPGRADE_TOWER_HP);
        int towerHpMax = ShopManager.TOWER_HP_MAX_LEVEL;
        inv.setItem(38, createUpgradeIcon("§c§lタワー体力+1♥", Material.RED_DYE,
                towerHpLevel, towerHpMax, towerHpPrice, shards,
                "タワー内の最大HPを+1♥",
                "現在のボーナス: §c+" + towerHpLevel + "♥"));
        slotItemMap.put(38, "upgrade_tower_hp");

        for (slot = 39; slot < 45; slot++) {
            inv.setItem(slot, createSeparator());
        }

        // === Row 5: Footer ===
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, createSeparator());
        }

        return inv;
    }

    /**
     * Get the shop item/action ID for a given inventory slot.
     */
    public String getItemIdAtSlot(int slot) {
        return slotItemMap.get(slot);
    }

    // === Item Creators ===

    private ItemStack createShardInfo(int shards) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d§l所持シャード: " + shards);
            meta.setLore(List.of(
                    "§7MOBを倒したり鉱石を採掘すると",
                    "§7シャードを獲得できます。"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createShopItemIcon(ShopItem shopItem, int playerShards) {
        ItemStack item = new ItemStack(shopItem.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(shopItem.displayName());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + shopItem.description());
            lore.add("");
            lore.add("§d価格: " + shopItem.price() + " シャード");
            lore.add("");
            if (playerShards >= shopItem.price()) {
                lore.add("§a▶ クリックで購入");
            } else {
                lore.add("§c✖ シャード不足");
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createUpgradeIcon(String name, Material icon, int level, int maxLevel,
                                         int price, int playerShards, String desc, String bonusDesc) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + desc);
            lore.add("§7" + bonusDesc);
            lore.add("");
            lore.add("§7レベル: §f" + level + "/" + maxLevel);
            if (level >= maxLevel) {
                lore.add("");
                lore.add("§6§l✦ 最大レベル到達！");
            } else {
                lore.add("§d次のレベル: " + price + " シャード");
                lore.add("");
                if (playerShards >= price) {
                    lore.add("§a▶ クリックで購入");
                } else {
                    lore.add("§c✖ シャード不足");
                }
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCategoryLabel(String name, Material icon) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
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
