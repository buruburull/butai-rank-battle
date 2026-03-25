package com.butai.rankbattle.listener;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.manager.EtherGrowthManager;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles right-click activation of skill items purchased from the shop.
 * - Power Charge: +3 attack for 30s, cooldown 60s
 * - Overdrive: +6 attack for 15s, cooldown 120s
 */
public class SkillItemListener implements Listener {

    public static final NamespacedKey SKILL_ITEM_KEY = new NamespacedKey("butairankbattle", "skill_item");

    private static final NamespacedKey POWER_CHARGE_MODIFIER = new NamespacedKey("butairankbattle", "power_charge");
    private static final NamespacedKey OVERDRIVE_MODIFIER = new NamespacedKey("butairankbattle", "overdrive");

    // Cooldown tracking: player UUID -> end timestamp (ms)
    private final Map<UUID, Long> powerChargeCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> overdriveCooldown = new ConcurrentHashMap<>();

    private final EtherGrowthManager growthManager;

    public SkillItemListener(EtherGrowthManager growthManager) {
        this.growthManager = growthManager;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String skillId = meta.getPersistentDataContainer().get(SKILL_ITEM_KEY, PersistentDataType.STRING);
        if (skillId == null) return;

        // Must be in tower
        if (!growthManager.isInTower(player.getUniqueId())) return;

        event.setCancelled(true);

        switch (skillId) {
            case "power_charge" -> activatePowerCharge(player, item);
            case "overdrive" -> activateOverdrive(player, item);
        }
    }

    private void activatePowerCharge(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();

        // Cooldown check
        long now = System.currentTimeMillis();
        Long cooldownEnd = powerChargeCooldown.get(uuid);
        if (cooldownEnd != null && now < cooldownEnd) {
            int remaining = (int) ((cooldownEnd - now) / 1000);
            MessageUtil.sendError(player, "§cクールダウン中... §7(残り" + remaining + "秒)");
            return;
        }

        // Consume item
        item.setAmount(item.getAmount() - 1);

        // Apply attack boost +3 for 30 seconds
        applyAttackBoost(player, POWER_CHARGE_MODIFIER, 3.0, 30);

        // Set cooldown (60 seconds)
        powerChargeCooldown.put(uuid, now + 60_000);

        MessageUtil.sendSuccess(player, "§c§lパワーチャージ発動！§7攻撃力+3 (30秒)");
        player.sendTitle("§c§l⚡ パワーチャージ！", "§7攻撃力+3 / 30秒", 5, 20, 10);
    }

    private void activateOverdrive(Player player, ItemStack item) {
        UUID uuid = player.getUniqueId();

        // Cooldown check
        long now = System.currentTimeMillis();
        Long cooldownEnd = overdriveCooldown.get(uuid);
        if (cooldownEnd != null && now < cooldownEnd) {
            int remaining = (int) ((cooldownEnd - now) / 1000);
            MessageUtil.sendError(player, "§cクールダウン中... §7(残り" + remaining + "秒)");
            return;
        }

        // Consume item
        item.setAmount(item.getAmount() - 1);

        // Apply attack boost +6 for 15 seconds
        applyAttackBoost(player, OVERDRIVE_MODIFIER, 6.0, 15);

        // Set cooldown (120 seconds)
        overdriveCooldown.put(uuid, now + 120_000);

        MessageUtil.sendSuccess(player, "§4§lオーバードライブ発動！§7攻撃力+6 (15秒)");
        player.sendTitle("§4§l🔥 オーバードライブ！", "§7攻撃力+6 / 15秒", 5, 20, 10);
    }

    private void applyAttackBoost(Player player, NamespacedKey modifierKey, double amount, int durationSeconds) {
        AttributeInstance attackAttr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr == null) return;

        // Remove existing modifier if any
        attackAttr.removeModifier(modifierKey);

        // Add new modifier
        AttributeModifier modifier = new AttributeModifier(
                modifierKey, amount, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND);
        attackAttr.addModifier(modifier);

        // Schedule removal
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    AttributeInstance attr = player.getAttribute(Attribute.ATTACK_DAMAGE);
                    if (attr != null) {
                        attr.removeModifier(modifierKey);
                    }
                    MessageUtil.sendInfo(player, "§7攻撃力ブーストが終了しました。");
                }
            }
        }.runTaskLater(BRBPlugin.getInstance(), durationSeconds * 20L);
    }

    /**
     * Clean up cooldowns and modifiers when player leaves tower.
     */
    public void onPlayerLeaveTower(UUID uuid) {
        powerChargeCooldown.remove(uuid);
        overdriveCooldown.remove(uuid);

        Player player = org.bukkit.Bukkit.getPlayer(uuid);
        if (player != null) {
            AttributeInstance attr = player.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attr != null) {
                attr.removeModifier(POWER_CHARGE_MODIFIER);
                attr.removeModifier(OVERDRIVE_MODIFIER);
            }
        }
    }
}
