package com.borderrank.battle.manager;

import com.borderrank.battle.model.TriggerCategory;
import com.borderrank.battle.model.TriggerData;
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
            } catch (Exception e) {
                plugin.getLogger().warning("triggers.yml not found in resources, checking config/");
                // Try from config/ folder in the server root
                File configFolder = new File(plugin.getServer().getWorldContainer(), "config");
                File altFile = new File(configFolder, "triggers.yml");
                if (altFile.exists()) {
                    triggersFile = altFile;
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
     * Loads trigger definitions from the triggers.yml configuration section.
     * Parses each trigger entry and creates TriggerData objects.
     *
     * @param config the FileConfiguration containing the triggers section
     */
    public void load(FileConfiguration config) {
        triggers.clear();

        if (config == null || !config.contains("triggers")) {
            return;
        }

        var triggersSection = config.getConfigurationSection("triggers");
        if (triggersSection == null) {
            return;
        }

        for (String triggerId : triggersSection.getKeys(false)) {
            var triggerSection = triggersSection.getConfigurationSection(triggerId);
            if (triggerSection == null) {
                continue;
            }

            try {
                TriggerData triggerData = parseTriggerData(triggerId, triggerSection);
                triggers.put(triggerId, triggerData);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load trigger: " + triggerId + " - " + e.getMessage());
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
     * @return the parsed TriggerData object
     */
    private TriggerData parseTriggerData(String triggerId, org.bukkit.configuration.ConfigurationSection section) {
        var builder = TriggerData.builder()
                .id(triggerId)
                .name(section.getString("name", triggerId))
                .description(section.getString("description", ""))
                .category(TriggerCategory.valueOf(section.getString("category", "SUPPORT").toUpperCase()))
                .cost(section.getInt("cost", 0))
                .cooldown(section.getInt("cooldown", 0));

        return builder.build();
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
