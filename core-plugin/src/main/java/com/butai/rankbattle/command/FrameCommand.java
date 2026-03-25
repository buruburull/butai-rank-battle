package com.butai.rankbattle.command;

import com.butai.rankbattle.BRBPlugin;
import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.model.FrameCategory;
import com.butai.rankbattle.model.FrameData;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.butai.rankbattle.gui.FrameSetGUI;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FrameCommand implements CommandExecutor, TabCompleter {

    public static final NamespacedKey FRAME_KEY = new NamespacedKey(BRBPlugin.getInstance(), "frame_id");
    public static final NamespacedKey LOBBY_ITEM_KEY = new NamespacedKey("butairankbattle", "lobby_item");

    private final FrameRegistry frameRegistry;
    private final FrameSetManager frameSetManager;
    private FrameSetGUI frameSetGUI;

    public FrameCommand(FrameRegistry frameRegistry, FrameSetManager frameSetManager) {
        this.frameRegistry = frameRegistry;
        this.frameSetManager = frameSetManager;
    }

    public void setFrameSetGUI(FrameSetGUI gui) {
        this.frameSetGUI = gui;
    }

    public FrameSetGUI getFrameSetGUI() {
        return frameSetGUI;
    }

    /**
     * Create an ItemStack for a frame with custom name, lore, and PDC tag.
     */
    private ItemStack createFrameItem(FrameData frameData, int slot) {
        Material material;
        try {
            material = Material.valueOf(frameData.getMcItem());
        } catch (IllegalArgumentException e) {
            material = Material.IRON_INGOT;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String slotLabel = slot <= 4 ? "メイン" + slot : "サブ" + (slot - 4);
            meta.setDisplayName(frameData.getCategory().getColor() + "§l" + frameData.getName()
                    + " §8[" + slotLabel + "]");

            List<String> lore = new ArrayList<>();
            lore.add("§7" + frameData.getDescription());
            lore.add("");
            lore.add("§7カテゴリ: " + frameData.getCategory().getColoredName());
            if (frameData.getDamage() > 0) {
                lore.add("§7ダメージ: §f" + frameData.getDamage());
            }
            if (frameData.getDamageMultiplier() != 1.0) {
                lore.add("§7倍率: §f" + frameData.getDamageMultiplier() + "x");
            }
            if (frameData.getEtherUse() > 0) {
                lore.add("§9エーテル消費: §f" + frameData.getEtherUse() + "/回");
            }
            if (frameData.getEtherSustain() > 0) {
                lore.add("§9エーテル持続: §f" + frameData.getEtherSustain() + "/秒");
            }
            if (frameData.getCooldown() > 0) {
                lore.add("§7クールタイム: §f" + frameData.getCooldown() + "秒");
            }
            lore.add("");
            lore.add("§8§oBRB Frame");
            meta.setLore(lore);

            // All frame items are unbreakable
            meta.setUnbreakable(true);

            meta.getPersistentDataContainer().set(FRAME_KEY, PersistentDataType.STRING, frameData.getId());
            item.setItemMeta(meta);
        }

        // Seeker (Trident): add Loyalty enchantment so it returns
        if ("TRIDENT".equals(frameData.getMcItem())) {
            item.addUnsafeEnchantment(Enchantment.LOYALTY, 3);
        }

        return item;
    }

    /**
     * Place a frame item into the player's hotbar at the corresponding slot.
     * Frame slot 1-8 maps to hotbar slot 0-7.
     */
    private void giveFrameItem(Player player, FrameData frameData, int slot) {
        ItemStack item = createFrameItem(frameData, slot);
        player.getInventory().setItem(slot - 1, item);
    }

    /**
     * Refresh all hotbar slots based on the current frameset, and add lobby utility items.
     * Layout: [Frame1-8] [Arrows(if ranged)]
     * Bottom row (slots 27-35): [...] [Guide Book:31] [Stats Clock:32] [FrameSet Star:33] [...]
     */
    public void refreshHotbar(Player player) {
        // Clear entire inventory first for clean state
        player.getInventory().clear();

        String[] slots = frameSetManager.getFrameSet(player.getUniqueId());
        boolean needsArrows = false;

        for (int i = 0; i < 8; i++) {
            if (slots[i] != null) {
                FrameData data = frameRegistry.getFrame(slots[i]);
                if (data != null) {
                    giveFrameItem(player, data, i + 1);

                    // Check if this frame uses a ranged weapon that needs arrows
                    String mcItem = data.getMcItem();
                    if ("BOW".equals(mcItem) || "CROSSBOW".equals(mcItem)) {
                        needsArrows = true;
                    }
                    continue;
                }
            }
            // Clear slot if empty or invalid
            player.getInventory().setItem(i, null);
        }

        // Give arrows if any ranged frame is equipped
        if (needsArrows) {
            player.getInventory().setItem(8, new ItemStack(Material.ARROW, 64));
        }

        // === Lobby utility items (bottom inventory row) ===
        player.getInventory().setItem(31, createGuideBook());
        player.getInventory().setItem(32, createStatsItem());
        player.getInventory().setItem(33, createFrameSetItem());
    }

    /**
     * Create the guide book with game explanation and commands.
     */
    private ItemStack createGuideBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("BRB ガイドブック");
            meta.setAuthor("BUTAI");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);

            // Page 1: Overview
            meta.addPage(
                    "§l§6BUTAI Rank Battle\n§l§6ガイドブック\n\n"
                    + "§0フレーム（特殊兵装）を装備し、\n"
                    + "エーテル（戦闘エネルギー）を\n"
                    + "管理しながらランクマッチで\n"
                    + "対戦する競技型PvPです。\n\n"
                    + "§8→ 次のページへ"
            );

            // Page 2: Frames
            meta.addPage(
                    "§l§6フレームについて\n\n"
                    + "§0■ STRIKER: 近接戦闘型\n"
                    + "  Crescent, Fang, Bastion\n\n"
                    + "§0■ GUNNER: 射撃型\n"
                    + "  Pulse, Nova, Seeker, Frost\n\n"
                    + "§0■ MARKSMAN: 狙撃型\n"
                    + "  Falcon, Volt, Zenith\n\n"
                    + "§0■ SUPPORT: 補助型\n"
                    + "  Leap, Cloak, Warp 他"
            );

            // Page 3: Ether
            meta.addPage(
                    "§l§6エーテルシステム\n\n"
                    + "§0基本上限: 1000\n"
                    + "成長で最大2000まで拡張可能\n\n"
                    + "§0■ XPバーで残量表示\n"
                    + "■ HP減少でリーク発生\n"
                    + "■ 200以下で黄色警告\n"
                    + "■ 100以下で赤色警告\n"
                    + "■ 0で§cE-Shift§0（緊急離脱）\n\n"
                    + "§8※E-Shiftはキルではなく\n"
                    + "§8ロビーへの緊急テレポート"
            );

            // Page 4: Match
            meta.addPage(
                    "§l§6マッチの種類\n\n"
                    + "§0■ ソロランク (5分)\n"
                    + "  1vs1、RP変動あり\n\n"
                    + "§0■ チームランク (10分)\n"
                    + "  チーム対抗、RP変動あり\n\n"
                    + "§0■ プラクティス (5分)\n"
                    + "  1vs1、RP変動なし\n\n"
                    + "§0時間切れ→ジャッジ判定\n"
                    + "同点→サドンデス(60秒)"
            );

            // Page 5: Rank
            meta.addPage(
                    "§l§6ランクシステム\n\n"
                    + "§6§lS級§0: 15,000+ RP\n"
                    + "§c§lA級§0: 10,000+ RP\n"
                    + "§b§lB級§0:  5,000+ RP\n"
                    + "§a§lC級§0:  5,000未満\n\n"
                    + "§0武器タイプ別にRP追跡:\n"
                    + "STRIKER / GUNNER / MARKSMAN\n\n"
                    + "§0スロット1の武器カテゴリで\n"
                    + "RP変動対象が決まります。"
            );

            // Page 6: Growth & Shop
            meta.addPage(
                    "§l§6成長システム\n\n"
                    + "§0鉱山で採掘 → EP+シャード\n"
                    + "MOBタワーで討伐 → EP+シャード\n\n"
                    + "§0■ EP: レベルアップ用\n"
                    + "  Lv UP→エーテル上限+25\n\n"
                    + "§0■ シャード: タワー内ショップ\n"
                    + "  消耗品/スキル/永続強化を購入\n\n"
                    + "§8タワー各階のショップNPCへ"
            );

            // Page 7: Commands
            meta.addPage(
                    "§l§6コマンド一覧 (1/2)\n\n"
                    + "§0§l[フレーム]\n"
                    + "§0/frame §7- 装備GUI\n"
                    + "§0/frame list §7- 一覧\n"
                    + "§0/frame set <S> <名前>\n"
                    + "§0/frame view §7- 確認\n"
                    + "§0/frame remove <S>\n"
                    + "§0/frame preset §7save/load\n\n"
                    + "§0§l[チーム]\n"
                    + "§0/team create <名前>\n"
                    + "§0/team invite <プレイヤー>"
            );

            // Page 8: Commands 2
            meta.addPage(
                    "§l§6コマンド一覧 (2/2)\n\n"
                    + "§0§l[ランクマッチ]\n"
                    + "§0/rank solo §7- ソロキュー\n"
                    + "§0/rank team §7- チームキュー\n"
                    + "§0/rank practice §7- 練習\n"
                    + "§0/rank cancel §7- キャンセル\n"
                    + "§0/rank stats §7- 戦績\n"
                    + "§0/rank top §7- TOP10\n"
                    + "§0/rank history §7- 履歴\n"
                    + "§0/rank spectate §7- 観戦"
            );

            meta.getPersistentDataContainer().set(LOBBY_ITEM_KEY, PersistentDataType.STRING, "guide");
            book.setItemMeta(meta);
        }
        return book;
    }

    /**
     * Create the stats clock item (right-click to show stats).
     */
    private ItemStack createStatsItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l戦績確認");
            meta.setLore(List.of(
                    "§7右クリックで自分の戦績を表示",
                    "",
                    "§8ランク・RP・勝敗を確認"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(LOBBY_ITEM_KEY, PersistentDataType.STRING, "stats");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Create the frameset nether star item (right-click to open frame GUI).
     */
    private ItemStack createFrameSetItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b§lフレーム設定");
            meta.setLore(List.of(
                    "§7右クリックでフレームセットGUIを開く",
                    "",
                    "§8フレームの装備・変更はこちら"
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(LOBBY_ITEM_KEY, PersistentDataType.STRING, "frameset");
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            if (frameSetGUI != null) {
                frameSetGUI.openGUI(player);
            } else {
                sendUsage(player);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        return switch (sub) {
            case "list" -> handleList(player, args);
            case "set" -> handleSet(player, args);
            case "view" -> handleView(player);
            case "remove" -> handleRemove(player, args);
            case "preset" -> handlePreset(player, args);
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private boolean handleList(Player player, String[] args) {
        FrameCategory filterCategory = null;
        if (args.length >= 2) {
            filterCategory = FrameCategory.fromString(args[1]);
            if (filterCategory == null) {
                MessageUtil.sendError(player, "無効なカテゴリです。striker / gunner / marksman / support");
                return true;
            }
        }

        MessageUtil.send(player, "§6§l=== フレーム一覧 ===");

        for (FrameCategory category : FrameCategory.values()) {
            if (filterCategory != null && filterCategory != category) continue;

            List<FrameData> categoryFrames = frameRegistry.getFramesByCategory(category);
            if (categoryFrames.isEmpty()) continue;

            player.sendMessage("");
            player.sendMessage(category.getColoredName() + " §8(" + categoryFrames.size() + ")");
            player.sendMessage("§8" + "─".repeat(30));

            for (FrameData frame : categoryFrames) {
                StringBuilder sb = new StringBuilder();
                sb.append("  ").append(category.getColor()).append(frame.getName());
                sb.append(" §8[§7").append(frame.getId()).append("§8]");

                if (frame.getDamage() > 0) {
                    sb.append(" §7DMG:§f").append(frame.getDamage());
                }
                if (frame.getDamageMultiplier() != 1.0) {
                    sb.append(" §7x§f").append(frame.getDamageMultiplier());
                }
                if (frame.getEtherUse() > 0) {
                    sb.append(" §9E:§f").append(frame.getEtherUse()).append("/回");
                }
                if (frame.getEtherSustain() > 0) {
                    sb.append(" §9E:§f").append(frame.getEtherSustain()).append("/秒");
                }
                if (frame.getCooldown() > 0) {
                    sb.append(" §7CT:§f").append(frame.getCooldown()).append("秒");
                }

                player.sendMessage(sb.toString());
                player.sendMessage("    §7" + frame.getDescription());
            }
        }

        return true;
    }

    private boolean handleSet(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendError(player, "使い方: /frame set <スロット1-8> <フレーム名>");
            return true;
        }

        int slot;
        try {
            slot = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.sendError(player, "スロット番号は1〜8の数字で指定してください。");
            return true;
        }

        String frameId = args[2].toLowerCase();
        String error = frameSetManager.setFrame(player.getUniqueId(), slot, frameId);
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        FrameData frameData = frameRegistry.getFrame(frameId);
        giveFrameItem(player, frameData, slot);

        String slotLabel = slot <= 4 ? "メイン" + slot : "サブ" + (slot - 4);
        MessageUtil.sendSuccess(player, "§f" + slotLabel + " §7に "
                + frameData.getCategory().getColor() + frameData.getName() + " §7を装備しました。");

        return true;
    }

    private boolean handleView(Player player) {
        String[] slots = frameSetManager.getFrameSet(player.getUniqueId());

        MessageUtil.send(player, "§6§l=== フレームセット ===");

        // Main slots (1-4)
        player.sendMessage("§e§lメイン枠");
        for (int i = 0; i < 4; i++) {
            printSlot(player, i + 1, slots[i], i == 0);
        }

        // Sub slots (5-8)
        player.sendMessage("");
        player.sendMessage("§b§lサブ枠");
        for (int i = 4; i < 8; i++) {
            printSlot(player, i + 1, slots[i], false);
        }

        // Weapon type info
        var weaponType = frameSetManager.getWeaponType(player.getUniqueId());
        if (weaponType != null) {
            player.sendMessage("");
            player.sendMessage("§7武器タイプ: " + weaponType.getColor() + weaponType.getDisplayName());
        }

        return true;
    }

    private void printSlot(Player player, int slot, String frameId, boolean isSlot1) {
        String slotLabel = slot <= 4 ? "メイン" + slot : "サブ" + (slot - 4);
        if (frameId == null) {
            String hint = isSlot1 ? " §c(必須: 武器フレーム)" : "";
            player.sendMessage("  §7" + slotLabel + ": §8[空]" + hint);
        } else {
            FrameData data = frameRegistry.getFrame(frameId);
            if (data != null) {
                player.sendMessage("  §7" + slotLabel + ": " + data.getCategory().getColor()
                        + data.getName() + " §8[" + data.getId() + "]");
            } else {
                player.sendMessage("  §7" + slotLabel + ": §c不明 (" + frameId + ")");
            }
        }
    }

    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendError(player, "使い方: /frame remove <スロット1-8>");
            return true;
        }

        int slot;
        try {
            slot = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            MessageUtil.sendError(player, "スロット番号は1〜8の数字で指定してください。");
            return true;
        }

        String error = frameSetManager.removeFrame(player.getUniqueId(), slot);
        if (error != null) {
            MessageUtil.sendError(player, error);
            return true;
        }

        player.getInventory().setItem(slot - 1, null);

        String slotLabel = slot <= 4 ? "メイン" + slot : "サブ" + (slot - 4);
        MessageUtil.sendSuccess(player, "§f" + slotLabel + " §7のフレームを解除しました。");
        return true;
    }

    private boolean handlePreset(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendError(player, "使い方: /frame preset <save|load|list|delete> [名前]");
            return true;
        }

        String action = args[1].toLowerCase();
        return switch (action) {
            case "save" -> handlePresetSave(player, args);
            case "load" -> handlePresetLoad(player, args);
            case "list" -> handlePresetList(player);
            case "delete" -> handlePresetDelete(player, args);
            default -> {
                MessageUtil.sendError(player, "使い方: /frame preset <save|load|list|delete> [名前]");
                yield true;
            }
        };
    }

    private boolean handlePresetSave(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendError(player, "使い方: /frame preset save <名前>");
            return true;
        }
        String name = args[2];
        String error = frameSetManager.savePreset(player.getUniqueId(), name);
        if (error != null) {
            MessageUtil.sendError(player, error);
        } else {
            MessageUtil.sendSuccess(player, "プリセット '§f" + name + "§a' を保存しました。");
        }
        return true;
    }

    private boolean handlePresetLoad(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendError(player, "使い方: /frame preset load <名前>");
            return true;
        }
        String name = args[2];
        String error = frameSetManager.loadPreset(player.getUniqueId(), name);
        if (error != null) {
            MessageUtil.sendError(player, error);
        } else {
            refreshHotbar(player);
            MessageUtil.sendSuccess(player, "プリセット '§f" + name + "§a' を読み込みました。");
        }
        return true;
    }

    private boolean handlePresetList(Player player) {
        List<String> presets = frameSetManager.listPresets(player.getUniqueId());
        if (presets.isEmpty()) {
            MessageUtil.sendInfo(player, "保存済みプリセットはありません。");
        } else {
            MessageUtil.send(player, "§6§l=== プリセット一覧 ===");
            for (String name : presets) {
                player.sendMessage("  §e" + name);
            }
        }
        return true;
    }

    private boolean handlePresetDelete(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendError(player, "使い方: /frame preset delete <名前>");
            return true;
        }
        String name = args[2];
        String error = frameSetManager.deletePreset(player.getUniqueId(), name);
        if (error != null) {
            MessageUtil.sendError(player, error);
        } else {
            MessageUtil.sendSuccess(player, "プリセット '§f" + name + "§a' を削除しました。");
        }
        return true;
    }

    private void sendUsage(Player player) {
        MessageUtil.send(player, "§6/frame コマンド一覧:");
        player.sendMessage("  §e/frame list [category] §7- フレーム一覧");
        player.sendMessage("  §e/frame set <slot> <name> §7- フレーム装備");
        player.sendMessage("  §e/frame view §7- 現在のフレームセット");
        player.sendMessage("  §e/frame remove <slot> §7- フレーム解除");
        player.sendMessage("  §e/frame preset save|load|list|delete §7- プリセット管理");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("list", "set", "view", "remove", "preset"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "list" -> {
                    for (FrameCategory cat : FrameCategory.values()) {
                        completions.add(cat.name().toLowerCase());
                    }
                }
                case "set", "remove" -> {
                    for (int i = 1; i <= 8; i++) {
                        completions.add(String.valueOf(i));
                    }
                }
                case "preset" -> completions.addAll(List.of("save", "load", "list", "delete"));
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ("set".equals(sub)) {
                completions.addAll(frameRegistry.getFrameIds());
            } else if ("preset".equals(sub)) {
                String action = args[1].toLowerCase();
                if ("load".equals(action) || "delete".equals(action)) {
                    if (sender instanceof Player player) {
                        completions.addAll(frameSetManager.listPresets(player.getUniqueId()));
                    }
                }
            }
        }

        String prefix = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }
}
