package com.borderrank.battle.manager;

import com.borderrank.battle.model.TriggerCategory;
import com.borderrank.battle.model.TriggerData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class TriggerRegistry {

    private final Map<String, TriggerData> triggers = new HashMap<>();
    private final JavaPlugin plugin;

    private static final Map<String, TriggerCategory> CATEGORY_SECTIONS = new LinkedHashMap<>();
    static {
        CATEGORY_SECTIONS.put("attackers", TriggerCategory.ATTACKER);
        CATEGORY_SECTIONS.put("shooters", TriggerCategory.SHOOTER);
        CATEGORY_SECTIONS.put("snipers", TriggerCategory.SNIPER);
        CATEGORY_SECTIONS.put("support", TriggerCategory.SUPPORT);
    }

    public TriggerRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        loadTriggersFile();
    }

    private void loadTriggersFile() {
        File triggersFile = new File(plugin.getDataFolder(), "triggers.yml");
        if (!triggersFile.exists()) {
            try {
                plugin.saveResource("triggers.yml", false);
                plugin.getLogger().info("Saved default triggers.yml to plugin data folder");
            } catch (Exception e) {
                plugin.getLogger().warning("triggers.yml not found in resources: " + e.getMessage());
                File configFolder = new File(plugin.getServer().getWorldContainer(), "config");
                File altFile = new File(configFolder, "triggers.yml");
                if (altFile.exists()) {
                    triggersFile = altFile;
                    plugin.getLogger().info("Found triggers.yml in config/ folder");
                }
            }
        }

        if (triggersFile.exists()) {
            FileConfiguration triggersConfig = YamlConfiguration.loadConfiguration(triggersFile);
            load(triggersConfig);
            plugin.getLogger().info("Loaded " + triggers.size() + " triggers from triggers.yml");
        } else {
            plugin.getLogger().warning("triggers.yml not found! No triggers loaded.");
        }
    }

    public void load(FileConfiguration config) {
        triggers.clear();

        if (config == null) {
            plugin.getLogger().warning("Trigger config is null");
            return;
        }

        for (Map.Entry<String, TriggerCategory> entry : CATEGORY_SECTIONS.entrySet()) {
            String sectionName = entry.getKey();
            TriggerCategory category = entry.getValue();

            ConfigurationSection categorySection = config.getConfigurationSection(sectionName);
            if (categorySection == null) {
                plugin.getLogger().info("No section found for category: " + sectionName);
                continue;
            }

            for (String triggerId : categorySection.getKeys(false)) {
                ConfigurationSection triggerSection = categorySection.getConfigurationSection(triggerId);
                if (triggerSection == null) {
                    continue;
                }

                try {
                    TriggerData triggerData = parseTriggerData(triggerId, triggerSection, category);
                    triggers.put(triggerId, triggerData);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load trigger: " + triggerId + " - " + e.getMessage());
                }
            }
        }
    }

    public boolean reloadTriggers() {
        try {
            loadTriggersFile();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private TriggerData parseTriggerData(String triggerId, ConfigurationSection section, TriggerCategory category) {
        String displayName = section.getString("display_name", triggerId);
        String description = section.getString("description", "");
        int cost = section.getInt("cost", 0);
        int trionUsage = section.getInt("trion_usage", 0);
        int cooldown = section.getInt("cooldown_seconds", 0);

        String itemName = section.getString("minecraft_item", "STONE");
        Material mcItem;
        try {
            mcItem = Material.valueOf(itemName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material '" + itemName + "' for trigger " + triggerId + ", defaulting to STONE");
            mcItem = Material.STONE;
        }

        TriggerData.SlotType slotType;
        if (category == TriggerCategory.SUPPORT) {
            slotType = TriggerData.SlotType.SUB;
        } else {
            slotType = TriggerData.SlotType.MAIN;
        }

        double trionSustain = 0.0;
        String trionType = section.getString("trion_type", "none");
        if ("per_second_sustain".equals(trionType)) {
            trionSustain = trionUsage;
        }

        return TriggerData.builder()
                .id(triggerId)
                .name(displayName)
                .description(description)
                .category(category)
                .cost(cost)
                .trionUse(trionUsage)
                .trionSustain(trionSustain)
                .slotType(slotType)
                .mcItem(mcItem)
                .cooldown(cooldown)
                .build();
    }

    public TriggerData get(String id) {
        return triggers.get(id);
    }

    public Map<String, TriggerData> getAll() {
        return Collections.unmodifiableMap(triggers);
    }

    public List<TriggerData> getByCategory(TriggerCategory category) {
        return triggers.values().stream()
                .filter(trigger -> trigger.getCategory() == category)
                .toList();
    }

    public int getTriggerCount() {
        return triggers.size();
    }
}
