package com.butai.rankbattle.command;

import com.butai.rankbattle.manager.FrameRegistry;
import com.butai.rankbattle.manager.FrameSetManager;
import com.butai.rankbattle.model.FrameCategory;
import com.butai.rankbattle.model.FrameData;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FrameCommand implements CommandExecutor, TabCompleter {

    private final FrameRegistry frameRegistry;
    private final FrameSetManager frameSetManager;

    public FrameCommand(FrameRegistry frameRegistry, FrameSetManager frameSetManager) {
        this.frameRegistry = frameRegistry;
        this.frameSetManager = frameSetManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
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
