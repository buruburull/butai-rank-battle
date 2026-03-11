package com.butai.rankbattle.command;

import com.butai.rankbattle.manager.FrameRegistry;
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

    public FrameCommand(FrameRegistry frameRegistry) {
        this.frameRegistry = frameRegistry;
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
            // TODO: set, view, remove, preset
            default -> {
                sendUsage(player);
                yield true;
            }
        };
    }

    private boolean handleList(Player player, String[] args) {
        // Optional category filter: /frame list [category]
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

    private void sendUsage(Player player) {
        MessageUtil.send(player, "§6/frame コマンド一覧:");
        player.sendMessage("  §e/frame list [category] §7- フレーム一覧");
        player.sendMessage("  §e/frame set <slot> <name> §7- フレーム装備");
        player.sendMessage("  §e/frame view §7- 現在のフレームセット");
        player.sendMessage("  §e/frame remove <slot> §7- フレーム解除");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("list", "set", "view", "remove"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("list".equals(sub)) {
                for (FrameCategory cat : FrameCategory.values()) {
                    completions.add(cat.name().toLowerCase());
                }
            } else if ("set".equals(sub)) {
                for (int i = 1; i <= 8; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if ("remove".equals(sub)) {
                for (int i = 1; i <= 8; i++) {
                    completions.add(String.valueOf(i));
                }
            }
        } else if (args.length == 3 && "set".equals(args[0].toLowerCase())) {
            completions.addAll(frameRegistry.getFrameIds());
        }

        String prefix = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .collect(Collectors.toList());
    }
}
