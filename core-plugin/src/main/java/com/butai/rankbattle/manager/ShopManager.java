package com.butai.rankbattle.manager;

import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.logging.Logger;

/**
 * Manages the tower shop system:
 * - Consumable items (weapons, potions, armor)
 * - Skill items (power charge, overdrive)
 * - Permanent upgrades (ether cap, tower HP)
 * - Shard rewards per MOB/ore type
 */
public class ShopManager {

    private final EtherGrowthManager growthManager;
    private final Logger logger;

    // Shard rewards for ore mining
    private static final Map<Material, Integer> ORE_SHARD_VALUES = Map.of(
            Material.COAL_ORE, 1,
            Material.DEEPSLATE_COAL_ORE, 1,
            Material.IRON_ORE, 2,
            Material.DEEPSLATE_IRON_ORE, 2,
            Material.GOLD_ORE, 4,
            Material.DEEPSLATE_GOLD_ORE, 4,
            Material.EMERALD_ORE, 6,
            Material.DEEPSLATE_EMERALD_ORE, 6
    );
    // Diamond ores handled separately (Map.of has 10 entry limit)
    private static final int DIAMOND_SHARD = 10;

    // ========== Shop Items ==========

    public enum ShopItemType {
        CONSUMABLE,
        SKILL,
        UPGRADE
    }

    public record ShopItem(
            String id,
            String displayName,
            Material icon,
            int price,
            ShopItemType type,
            String description
    ) {}

    // Consumable items
    public static final ShopItem DIAMOND_SWORD = new ShopItem("diamond_sword", "§bダイヤの剣", Material.DIAMOND_SWORD, 30, ShopItemType.CONSUMABLE, "タワー内で使える強力な剣");
    public static final ShopItem DIAMOND_PICKAXE = new ShopItem("diamond_pickaxe", "§bダイヤのピッケル", Material.DIAMOND_PICKAXE, 30, ShopItemType.CONSUMABLE, "鉱山の採掘速度UP");
    public static final ShopItem HEALING_POTION = new ShopItem("healing_potion", "§c回復ポーション", Material.SPLASH_POTION, 15, ShopItemType.CONSUMABLE, "即時回復II");
    public static final ShopItem GOLDEN_APPLE = new ShopItem("golden_apple", "§6金リンゴ", Material.GOLDEN_APPLE, 25, ShopItemType.CONSUMABLE, "吸収と再生効果");
    public static final ShopItem IRON_ARMOR = new ShopItem("iron_armor", "§f鉄装備セット", Material.IRON_CHESTPLATE, 50, ShopItemType.CONSUMABLE, "鉄ヘルメット/チェスト/レギンス/ブーツ");

    // Skill items
    public static final ShopItem POWER_CHARGE = new ShopItem("power_charge", "§c§lパワーチャージ", Material.BLAZE_POWDER, 40, ShopItemType.SKILL, "30秒間攻撃力+3 (CT60秒)");
    public static final ShopItem OVERDRIVE = new ShopItem("overdrive", "§4§lオーバードライブ", Material.BLAZE_ROD, 80, ShopItemType.SKILL, "15秒間攻撃力+6 (CT120秒)");

    // Permanent upgrades
    public static final String UPGRADE_ETHER_CAP = "ether_cap";
    public static final String UPGRADE_TOWER_HP = "tower_hp";

    public static final int ETHER_CAP_MAX_LEVEL = 10;
    public static final int ETHER_CAP_BASE_PRICE = 500;
    public static final double ETHER_CAP_PRICE_MULTIPLIER = 1.5;
    public static final int ETHER_CAP_PER_LEVEL = 10;

    public static final int TOWER_HP_MAX_LEVEL = 5;
    public static final int TOWER_HP_BASE_PRICE = 600;
    public static final double TOWER_HP_PRICE_MULTIPLIER = 1.5;

    // All consumable/skill items for GUI
    public static final List<ShopItem> CONSUMABLE_ITEMS = List.of(
            DIAMOND_SWORD, DIAMOND_PICKAXE, HEALING_POTION, GOLDEN_APPLE, IRON_ARMOR
    );
    public static final List<ShopItem> SKILL_ITEMS = List.of(
            POWER_CHARGE, OVERDRIVE
    );

    public ShopManager(EtherGrowthManager growthManager, Logger logger) {
        this.growthManager = growthManager;
        this.logger = logger;
    }

    // ========== Shard Rewards ==========

    public int getOreShard(Material material) {
        if (material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE) {
            return DIAMOND_SHARD;
        }
        return ORE_SHARD_VALUES.getOrDefault(material, 0);
    }

    public static final Map<org.bukkit.entity.EntityType, Integer> MOB_SHARD_VALUES = Map.of(
            org.bukkit.entity.EntityType.ZOMBIE, 2,
            org.bukkit.entity.EntityType.SKELETON, 3,
            org.bukkit.entity.EntityType.SPIDER, 5,
            org.bukkit.entity.EntityType.WITCH, 5,
            org.bukkit.entity.EntityType.VINDICATOR, 8,
            org.bukkit.entity.EntityType.PILLAGER, 8,
            org.bukkit.entity.EntityType.WARDEN, 20
    );

    public int getMobShard(org.bukkit.entity.EntityType type) {
        return MOB_SHARD_VALUES.getOrDefault(type, 0);
    }

    // ========== Purchase Logic ==========

    /**
     * Purchase a consumable item. Returns true if successful.
     */
    public boolean purchaseConsumable(Player player, ShopItem item) {
        UUID uuid = player.getUniqueId();
        int shards = growthManager.getShards(uuid);

        if (shards < item.price()) {
            MessageUtil.sendError(player, "§cシャードが不足しています。§7(所持: §d" + shards + " §7/ 必要: §d" + item.price() + "§7)");
            return false;
        }

        if (!growthManager.spendShards(uuid, item.price())) return false;

        // Give item to player
        switch (item.id()) {
            case "diamond_sword" -> player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND_SWORD));
            case "diamond_pickaxe" -> player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND_PICKAXE));
            case "healing_potion" -> {
                org.bukkit.inventory.ItemStack potion = new org.bukkit.inventory.ItemStack(Material.SPLASH_POTION);
                org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
                if (meta != null) {
                    meta.addCustomEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.INSTANT_HEALTH, 1, 1), true);
                    meta.setDisplayName("§c回復ポーション");
                    potion.setItemMeta(meta);
                }
                player.getInventory().addItem(potion);
            }
            case "golden_apple" -> player.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE));
            case "iron_armor" -> {
                player.getInventory().setHelmet(new org.bukkit.inventory.ItemStack(Material.IRON_HELMET));
                player.getInventory().setChestplate(new org.bukkit.inventory.ItemStack(Material.IRON_CHESTPLATE));
                player.getInventory().setLeggings(new org.bukkit.inventory.ItemStack(Material.IRON_LEGGINGS));
                player.getInventory().setBoots(new org.bukkit.inventory.ItemStack(Material.IRON_BOOTS));
            }
        }

        MessageUtil.sendSuccess(player, item.displayName() + " §7を購入しました。§8(§d-" + item.price() + " シャード§8)");
        return true;
    }

    /**
     * Purchase a skill item (power charge / overdrive).
     */
    public boolean purchaseSkillItem(Player player, ShopItem item) {
        UUID uuid = player.getUniqueId();
        int shards = growthManager.getShards(uuid);

        if (shards < item.price()) {
            MessageUtil.sendError(player, "§cシャードが不足しています。§7(所持: §d" + shards + " §7/ 必要: §d" + item.price() + "§7)");
            return false;
        }

        if (!growthManager.spendShards(uuid, item.price())) return false;

        // Create skill item with custom NBT
        org.bukkit.inventory.ItemStack skillItem = new org.bukkit.inventory.ItemStack(item.icon());
        org.bukkit.inventory.meta.ItemMeta meta = skillItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(item.displayName());
            meta.setLore(List.of("§7" + item.description(), "", "§e右クリックで発動"));
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey("butairankbattle", "skill_item"),
                    org.bukkit.persistence.PersistentDataType.STRING, item.id());
            skillItem.setItemMeta(meta);
        }
        player.getInventory().addItem(skillItem);

        MessageUtil.sendSuccess(player, item.displayName() + " §7を購入しました。§8(§d-" + item.price() + " シャード§8)");
        return true;
    }

    // ========== Permanent Upgrades ==========

    /**
     * Get the price for the next level of an upgrade.
     */
    public int getUpgradePrice(UUID uuid, String upgradeType) {
        int level = growthManager.getUpgradeLevel(uuid, upgradeType);
        return switch (upgradeType) {
            case UPGRADE_ETHER_CAP -> (int) (ETHER_CAP_BASE_PRICE * Math.pow(ETHER_CAP_PRICE_MULTIPLIER, level));
            case UPGRADE_TOWER_HP -> (int) (TOWER_HP_BASE_PRICE * Math.pow(TOWER_HP_PRICE_MULTIPLIER, level));
            default -> 0;
        };
    }

    /**
     * Get the max level for an upgrade type.
     */
    public int getUpgradeMaxLevel(String upgradeType) {
        return switch (upgradeType) {
            case UPGRADE_ETHER_CAP -> ETHER_CAP_MAX_LEVEL;
            case UPGRADE_TOWER_HP -> TOWER_HP_MAX_LEVEL;
            default -> 0;
        };
    }

    /**
     * Purchase a permanent upgrade. Returns true if successful.
     */
    public boolean purchaseUpgrade(Player player, String upgradeType) {
        UUID uuid = player.getUniqueId();
        int currentLevel = growthManager.getUpgradeLevel(uuid, upgradeType);
        int maxLevel = getUpgradeMaxLevel(upgradeType);

        if (currentLevel >= maxLevel) {
            MessageUtil.sendError(player, "§cこの強化は最大レベルに達しています。");
            return false;
        }

        int price = getUpgradePrice(uuid, upgradeType);
        int shards = growthManager.getShards(uuid);

        if (shards < price) {
            MessageUtil.sendError(player, "§cシャードが不足しています。§7(所持: §d" + shards + " §7/ 必要: §d" + price + "§7)");
            return false;
        }

        if (!growthManager.spendShards(uuid, price)) return false;

        int newLevel = currentLevel + 1;
        // Calculate total spent (sum of all previous prices + this one)
        int totalSpent = 0;
        for (int i = 0; i < newLevel; i++) {
            totalSpent += switch (upgradeType) {
                case UPGRADE_ETHER_CAP -> (int) (ETHER_CAP_BASE_PRICE * Math.pow(ETHER_CAP_PRICE_MULTIPLIER, i));
                case UPGRADE_TOWER_HP -> (int) (TOWER_HP_BASE_PRICE * Math.pow(TOWER_HP_PRICE_MULTIPLIER, i));
                default -> 0;
            };
        }

        growthManager.setUpgradeLevel(uuid, upgradeType, newLevel, totalSpent);

        // Apply upgrade effects
        switch (upgradeType) {
            case UPGRADE_ETHER_CAP -> {
                int bonusCap = newLevel * ETHER_CAP_PER_LEVEL;
                int baseCap = EtherGrowthManager.getBaseEtherCap()
                        + growthManager.getGrowthLevel(uuid) * EtherGrowthManager.getEtherPerLevel();
                int newCap = Math.min(baseCap + bonusCap, EtherGrowthManager.getMaxEtherCap());
                growthManager.getGrowthDAO().updateEtherCap(uuid, newCap);
                MessageUtil.sendSuccess(player, "§aエーテル上限+10！§7(Lv." + newLevel + "/" + maxLevel + ") §bエーテル上限: " + newCap);
            }
            case UPGRADE_TOWER_HP -> {
                MessageUtil.sendSuccess(player, "§aタワー体力+1♥！§7(Lv." + newLevel + "/" + maxLevel + ") §c最大HP+" + (newLevel * 2));
            }
        }

        MessageUtil.sendInfo(player, "§8(§d-" + price + " シャード §8| §7残り: §d" + growthManager.getShards(uuid) + "§8)");
        return true;
    }
}
