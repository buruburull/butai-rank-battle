package com.borderrank.battle.command;

import com.borderrank.battle.BRBPlugin;
import com.borderrank.battle.manager.LoadoutManager;
import com.borderrank.battle.manager.TriggerRegistry;
import com.borderrank.battle.model.Loadout;
import com.borderrank.battle.model.TriggerData;
import com.borderrank.battle.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Command handler for /trigger command.
 * Manages trigger loadouts and presets.
 */
public class TriggerCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendErrorMessage(sender, "This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendInfoMessage(player, "Usage: /trigger <list|set|remove|view|preset>");
            return true;
        }

        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "list" -> handleList(player, args);
            case "set" -> handleSet(player, args);
            case "remove" -> handleRemove(player, args);
            case "view" -> handleView(player, args);
            case "preset" -> handlePreset(player, args);
            default -> MessageUtil.sendErrorMessage(player, "Unknown subcommand: " + subcommand);
        }

        return true;
    }

    /**
     * Handle /trigger list command - lists available triggers.
     */
    private void handleList(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        TriggerRegistry registry = plugin.getTriggerRegistry();
        String category = args.length > 1 ? args[1].toUpperCase() : null;

        MessageUtil.sendInfoMessage(player, "=== Available Triggers ===");

        Map<String, TriggerData> triggers = registry.getAll();
        for (TriggerData trigger : triggers.values()) {
            if (category == null || trigger.getCategory().name().equalsIgnoreCase(category)) {
                MessageUtil.sendInfoMessage(player,
                    trigger.getId() + " - " + trigger.getName() +
                    " | Cost: " + trigger.getCost() +
                    " | Trion: " + trigger.getTrionUse());
            }
        }
    }

    /**
     * Handle /trigger set command - sets a trigger in a loadout slot.
     */
    private void handleSet(Player player, String[] args) {
        if (args.length < 3) {
            MessageUtil.sendErrorMessage(player, "Usage: /trigger set <slot 1-8> <trigger_id>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        TriggerRegistry registry = plugin.getTriggerRegistry();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();

        int slot;
        try {
            slot = Integer.parseInt(args[1]);
            if (slot < 1 || slot > 8) {
                MessageUtil.sendErrorMessage(player, "Slot must be between 1 and 8.");
                return;
            }
        } catch (NumberFormatException e) {
            MessageUtil.sendErrorMessage(player, "Invalid slot number.");
            return;
        }

        String triggerId = args[2].toLowerCase();
        TriggerData trigger = registry.get(triggerId);

        if (trigger == null) {
            MessageUtil.sendErrorMessage(player, "Trigger not found: " + triggerId);
            return;
        }

        UUID uuid = player.getUniqueId();
        int slotIndex = slot - 1; // Convert 1-based to 0-based

        // Get or create default loadout
        Loadout loadout = loadoutManager.getLoadout(uuid, "default");
        if (loadout == null) {
            loadout = new Loadout(uuid, "default");
            try {
                loadoutManager.saveLoadout(loadout);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create default loadout: " + e.getMessage());
            }
        }

        // Set the trigger in the slot
        loadout.setSlot(slotIndex, triggerId);
        loadout.calculateTotalCost(registry.getAll());

        // Check TP cost limit (15 TP max)
        if (loadout.getTotalCost() > 15) {
            loadout.setSlot(slotIndex, ""); // Revert
            loadout.calculateTotalCost(registry.getAll());
            MessageUtil.sendErrorMessage(player, "TP limit exceeded! Cost would be " + (loadout.getTotalCost() + trigger.getCost()) + "/15");
            return;
        }

        // Save loadout
        try {
            loadoutManager.saveLoadout(loadout);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save loadout: " + e.getMessage());
        }

        // Give the Minecraft item to the player in the correct hotbar slot
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(trigger.getMcItem());
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(org.bukkit.ChatColor.GREEN + trigger.getName());
            List<String> lore = new ArrayList<>();
            lore.add(org.bukkit.ChatColor.GRAY + trigger.getDescription());
            lore.add(org.bukkit.ChatColor.YELLOW + "Cost: " + trigger.getCost() + " TP");
            if (trigger.getTrionUse() > 0) {
                lore.add(org.bukkit.ChatColor.AQUA + "Trion: " + trigger.getTrionUse());
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        player.getInventory().setItem(slotIndex, item);

        MessageUtil.sendSuccessMessage(player, trigger.getName() + " をスロット " + slot + " にセット (Cost: " + loadout.getTotalCost() + "/15 TP)");
    }

    /**
     * Handle /trigger remove command - removes trigger from slot.
     */
    private void handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(player, "Usage: /trigger remove <slot 1-8>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        TriggerRegistry registry = plugin.getTriggerRegistry();

        int slot;
        try {
            slot = Integer.parseInt(args[1]);
            if (slot < 1 || slot > 8) {
                MessageUtil.sendErrorMessage(player, "Slot must be between 1 and 8.");
                return;
            }
        } catch (NumberFormatException e) {
            MessageUtil.sendErrorMessage(player, "Invalid slot number.");
            return;
        }

        UUID uuid = player.getUniqueId();
        int slotIndex = slot - 1;

        Loadout loadout = loadoutManager.getLoadout(uuid, "default");
        if (loadout != null) {
            loadout.setSlot(slotIndex, "");
            loadout.calculateTotalCost(registry.getAll());
            try {
                loadoutManager.saveLoadout(loadout);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to save loadout: " + e.getMessage());
            }
        }

        player.getInventory().setItem(slotIndex, null);
        MessageUtil.sendSuccessMessage(player, "スロット " + slot + " からトリガーを解除しました");
    }

    /**
     * Handle /trigger view command - shows current loadout.
     */
    private void handleView(Player player, String[] args) {
        BRBPlugin plugin = BRBPlugin.getInstance();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        TriggerRegistry registry = plugin.getTriggerRegistry();

        Loadout loadout = loadoutManager.getLoadout(player.getUniqueId(), "default");

        MessageUtil.sendInfoMessage(player, "=== Your Loadout ===");

        if (loadout == null || !loadout.isActive()) {
            MessageUtil.sendInfoMessage(player, "No triggers equipped. Use /trigger set <slot> <id>");
            return;
        }

        for (int i = 0; i < 8; i++) {
            String triggerId = loadout.getSlot(i);
            if (triggerId != null && !triggerId.isEmpty()) {
                TriggerData td = registry.get(triggerId);
                String name = td != null ? td.getName() : triggerId;
                int cost = td != null ? td.getCost() : 0;
                String slotLabel = (i < 4) ? "Main" : "Sub";
                MessageUtil.sendInfoMessage(player, "  Slot " + (i + 1) + " [" + slotLabel + "]: " + name + " (Cost: " + cost + ")");
            }
        }

        loadout.calculateTotalCost(registry.getAll());
        MessageUtil.sendInfoMessage(player, "Total Cost: " + loadout.getTotalCost() + "/15 TP");
    }

    /**
     * Handle /trigger preset command - manages presets.
     */
    private void handlePreset(Player player, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendErrorMessage(player, "Usage: /trigger preset <save|load|list|delete> <name>");
            return;
        }

        BRBPlugin plugin = BRBPlugin.getInstance();
        LoadoutManager loadoutManager = plugin.getLoadoutManager();
        TriggerRegistry registry = plugin.getTriggerRegistry();
        UUID uuid = player.getUniqueId();
        String action = args[1].toLowerCase();

        switch (action) {
            case "save" -> {
                if (args.length < 3) {
                    MessageUtil.sendErrorMessage(player, "Usage: /trigger preset save <name>");
                    return;
                }
                String presetName = args[2].toLowerCase();
                if ("default".equals(presetName)) {
                    MessageUtil.sendErrorMessage(player, "'default' はプリセット名として使用できません。");
                    return;
                }
                // Max 5 presets per player
                Map<String, Loadout> allLoadouts = loadoutManager.getPlayerLoadouts(uuid);
                long presetCount = allLoadouts.keySet().stream().filter(n -> !"default".equals(n)).count();
                if (presetCount >= 5 && !allLoadouts.containsKey(presetName)) {
                    MessageUtil.sendErrorMessage(player, "プリセットは最大5つまでです。不要なプリセットを /trigger preset delete <name> で削除してください。");
                    return;
                }
                // Copy current default loadout to the preset name
                Loadout currentLoadout = loadoutManager.getLoadout(uuid, "default");
                if (currentLoadout == null || !currentLoadout.isActive()) {
                    MessageUtil.sendErrorMessage(player, "現在のロードアウトが空です。先にトリガーをセットしてください。");
                    return;
                }
                // Create a new loadout with the preset name, copying slots from default
                String[] slotsCopy = new String[8];
                for (int i = 0; i < 8; i++) {
                    String s = currentLoadout.getSlot(i);
                    slotsCopy[i] = (s != null) ? s : "";
                }
                Loadout preset = new Loadout(uuid, presetName, slotsCopy);
                preset.calculateTotalCost(registry.getAll());
                try {
                    loadoutManager.saveLoadout(preset);
                    MessageUtil.sendSuccessMessage(player, "プリセット '" + presetName + "' を保存しました！ (" + preset.getTotalCost() + " TP)");
                } catch (Exception e) {
                    MessageUtil.sendErrorMessage(player, "プリセットの保存に失敗しました。");
                    plugin.getLogger().warning("Failed to save preset: " + e.getMessage());
                }
            }
            case "load" -> {
                if (args.length < 3) {
                    MessageUtil.sendErrorMessage(player, "Usage: /trigger preset load <name>");
                    return;
                }
                String presetName = args[2].toLowerCase();
                Loadout preset = loadoutManager.getLoadout(uuid, presetName);
                if (preset == null) {
                    MessageUtil.sendErrorMessage(player, "プリセット '" + presetName + "' が見つかりません。");
                    return;
                }
                // Copy preset slots into default loadout
                Loadout defaultLoadout = loadoutManager.getLoadout(uuid, "default");
                if (defaultLoadout == null) {
                    defaultLoadout = new Loadout(uuid, "default");
                }
                for (int i = 0; i < 8; i++) {
                    String s = preset.getSlot(i);
                    defaultLoadout.setSlot(i, (s != null) ? s : "");
                }
                defaultLoadout.calculateTotalCost(registry.getAll());
                try {
                    loadoutManager.saveLoadout(defaultLoadout);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to save default loadout: " + e.getMessage());
                }
                // Update player inventory with loaded triggers
                player.getInventory().clear();
                for (int i = 0; i < 8; i++) {
                    String triggerId = defaultLoadout.getSlot(i);
                    if (triggerId != null && !triggerId.isEmpty()) {
                        TriggerData td = registry.get(triggerId);
                        if (td != null) {
                            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(td.getMcItem());
                            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                meta.setDisplayName(org.bukkit.ChatColor.GREEN + td.getName());
                                List<String> lore = new ArrayList<>();
                                lore.add(org.bukkit.ChatColor.GRAY + td.getDescription());
                                lore.add(org.bukkit.ChatColor.YELLOW + "Cost: " + td.getCost() + " TP");
                                meta.setLore(lore);
                                item.setItemMeta(meta);
                            }
                            player.getInventory().setItem(i, item);
                        }
                    }
                }
                MessageUtil.sendSuccessMessage(player, "プリセット '" + presetName + "' を読み込みました！ (" + defaultLoadout.getTotalCost() + "/15 TP)");
            }
            case "list" -> {
                Map<String, Loadout> allLoadouts = loadoutManager.getPlayerLoadouts(uuid);
                MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m----------\u00a7r \u00a7e\u00a7lプリセット一覧 \u00a78\u00a7m----------");
                boolean hasPresets = false;
                for (Map.Entry<String, Loadout> entry : allLoadouts.entrySet()) {
                    if ("default".equals(entry.getKey())) continue;
                    hasPresets = true;
                    Loadout lo = entry.getValue();
                    lo.calculateTotalCost(registry.getAll());
                    // Build trigger summary
                    StringBuilder triggerNames = new StringBuilder();
                    for (int i = 0; i < 8; i++) {
                        String tid = lo.getSlot(i);
                        if (tid != null && !tid.isEmpty()) {
                            TriggerData td = registry.get(tid);
                            if (triggerNames.length() > 0) triggerNames.append(", ");
                            triggerNames.append(td != null ? td.getName() : tid);
                        }
                    }
                    MessageUtil.sendInfoMessage(player, "\u00a7e" + entry.getKey() + " \u00a77(" + lo.getTotalCost() + " TP) \u00a78- " + triggerNames);
                }
                if (!hasPresets) {
                    MessageUtil.sendInfoMessage(player, "\u00a77保存されたプリセットはありません。");
                    MessageUtil.sendInfoMessage(player, "\u00a77/trigger preset save <name> で現在のロードアウトを保存できます。");
                }
                MessageUtil.sendInfoMessage(player, "\u00a78\u00a7m--------------------------------------");
            }
            case "delete" -> {
                if (args.length < 3) {
                    MessageUtil.sendErrorMessage(player, "Usage: /trigger preset delete <name>");
                    return;
                }
                String presetName = args[2].toLowerCase();
                if ("default".equals(presetName)) {
                    MessageUtil.sendErrorMessage(player, "'default' は削除できません。");
                    return;
                }
                Loadout existing = loadoutManager.getLoadout(uuid, presetName);
                if (existing == null) {
                    MessageUtil.sendErrorMessage(player, "プリセット '" + presetName + "' が見つかりません。");
                    return;
                }
                // Delete from DB and cache
                plugin.getLoadoutManager().getPlayerLoadouts(uuid).remove(presetName);
                // Delete from DB via DAO
                org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    new com.borderrank.battle.database.LoadoutDAO(plugin.getDatabaseManager()).deleteLoadout(uuid, presetName);
                });
                // Remove from in-memory cache
                Map<String, Loadout> cached = loadoutManager.getPlayerLoadouts(uuid);
                // Force reload
                try {
                    loadoutManager.loadPlayerLoadouts(uuid);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to reload loadouts: " + e.getMessage());
                }
                MessageUtil.sendSuccessMessage(player, "プリセット '" + presetName + "' を削除しました。");
            }
            default -> MessageUtil.sendErrorMessage(player, "Unknown preset action: " + action + ". Use: save, load, list, delete");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("list");
            completions.add("set");
            completions.add("remove");
            completions.add("view");
            completions.add("preset");
        } else if (args.length == 2) {
            if ("set".equalsIgnoreCase(args[0])) {
                for (int i = 1; i <= 8; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if ("remove".equalsIgnoreCase(args[0])) {
                for (int i = 1; i <= 8; i++) {
                    completions.add(String.valueOf(i));
                }
            } else if ("preset".equalsIgnoreCase(args[0])) {
                completions.add("save");
                completions.add("load");
                completions.add("list");
                completions.add("delete");
            }
        } else if (args.length == 3) {
            if ("set".equalsIgnoreCase(args[0])) {
                BRBPlugin plugin = BRBPlugin.getInstance();
                TriggerRegistry registry = plugin.getTriggerRegistry();
                completions.addAll(registry.getAll().keySet());
            } else if ("preset".equalsIgnoreCase(args[0])
                    && ("load".equalsIgnoreCase(args[1]) || "delete".equalsIgnoreCase(args[1]))) {
                // Show saved preset names for load/delete
                if (sender instanceof Player p) {
                    BRBPlugin plugin = BRBPlugin.getInstance();
                    Map<String, Loadout> loadouts = plugin.getLoadoutManager().getPlayerLoadouts(p.getUniqueId());
                    for (String name : loadouts.keySet()) {
                        if (!"default".equals(name)) {
                            completions.add(name);
                        }
                    }
                }
            }
        }

        return completions;
    }
}
