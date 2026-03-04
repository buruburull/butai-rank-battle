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

/**
 * Registry for loading and managing trigger definitions from configuration files.
 * Provides access to trigger data and filtering capabilities.
 */
public class TriggerRegistry {

    private final Map<String, TriggerData> triggers = new HashMap<>();
    private final JavaPlugin plugin;

    /**
     * Category section names in triggers.yml mapped to TriggerCategory enum.
     */
    private static final Map<String, TriggerCategory> CATEGORY_SECTIONS = new LinkedHashMap<>();
    static {
        CATEGORY_SECTIONS.put("attackers", TriggerCategory.ATTACKER);
        CATEGORY_SECTIONS.put("shooters", TriggerCategory.SHOOTER);
        CATEGORY_SECTIONS.put("snipers", TriggerCategory.SNIPER);
        CATEGORY_SECTIONS.put("support", TriggerCategory.SUPPORT);
    }

    /**
     * Constructs a TriggerRegistry with a plugin instance.
     *
     * @param plugin the JavaPlugin instance for accessing configuration
     */
    public TriggerRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        loadTriggersFile();
    }

    /**
     * Loads triggers.yml from the plugin data folder.
     * If not found, copies the default from resources.
     */
    private void loadTriggersFile() {
        File triggersFile = new File(plugin.getDataFolder(), "triggers.yml");
        if (!triggersFile.exists()) {
            // Try to save from resources
            try {
                plugin.saveResource("triggers.yml", false);
                plugin.getLogger().info("Saved default triggers.yml to plugin data folder");
            } catch (Exception e) {
                plugin.getLogger().warning("triggers.yml not found in resources: " + e.getMessage());
                // Try from config/ folder in the server root
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

    /**
     * Loads trigger definitions from the triggers.yml configuration.
     * The YAML structure uses category-based sections: attackers, shooters, snipers, support.
     * Each section contains trigger entries with their properties.
     *
     * @param config the FileConfiguration containing the trigger sections
     */
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

    /**
     * Reloads all triggers from the triggers.yml file.
     *
     * @return true if reload was successful, false otherwise
     */
    public boolean reloadTriggers() {
        try {
            loadTriggersFile();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Parses a single trigger entry from the configuration.
     *
     * @param triggerId the unique identifier for the trigger
     * @param section the configuration section containing trigger properties
     * @param category the category this trigger belongs to
     * @return the parsed TriggerData object
     */
    private TriggerData parseTriggerData(String triggerId, ConfigurationSection section, TriggerCategory category) {
        String displayName = section.getString("display_name", triggerId);
        String description = section.getString("description", "");
        int cost = section.getInt("cost", 0);
        int trionUsage = section.getInt("trion_usage", 0);
        int cooldown = section.getInt("cooldown_seconds", 0);

        // Parse minecraft item
        String itemName = section.getString("minecraft_item", "STONE");
        Material mcItem;
        try {
            mcItem = Material.valueOf(itemName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material '" + itemName + "' for trigger " + triggerId + ", defaulting to STONE");
            mcItem = Material.STONE;
        }

        // Determine slot type based on category
        TriggerData.SlotType slotType;
        if (category == TriggerCategory.SUPPORT) {
            slotType = TriggerData.SlotType.SUB;
        } else {
            slotType = TriggerData.SlotType.MAIN;
        }

        // Parse trion sustain from trion_type
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

    /**
     * Retrieves a trigger by its unique identifier.
     *
     * @param id the trigger ID
     * @return the TriggerData if found, null otherwise
     */
    public TriggerData get(String id) {
        return triggers.get(id);
    }

    /**
     * Retrieves all loaded triggers as an unmodifiable map.
     *
     * @return an unmodifiable view of all triggers
     */
    public Map<String, TriggerData> getAll() {
        return Collections.unmodifiableMap(triggers);
    }

    /**
     * Retrieves all triggers belonging to a specific category.
     *
     * @param category the TriggerCategory to filter by
     * @return a list of TriggerData objects in the specified category
     */
    public List<TriggerData> getByCategory(TriggerCategory category) {
        return triggers.values().stream()
                .filter(trigger -> trigger.getCategory() == category)
                .toList();
    }

    /**
     * Gets the total count of loaded triggers.
     *
     * @return the number of triggers
     */
    public int getTriggerCount() {
        return triggers.size();
    }
}
