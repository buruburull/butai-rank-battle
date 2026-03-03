package com.borderrank.battle.manager;

import com.borderrank.battle.model.TriggerCategory;
import com.borderrank.battle.model.TriggerData;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Registry for loading and managing trigger definitions from configuration files.
 * Provides access to trigger data and filtering capabilities.
 */
public class TriggerRegistry {
    
    private final Map<String, TriggerData> triggers = new HashMap<>();

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
                System.err.println("Failed to load trigger: " + triggerId);
                e.printStackTrace();
            }
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
                .cooldown(section.getInt("cooldown", 0))
                .baseAttackPower(section.getDouble("baseAttackPower", 0.0))
                .weaponType(section.getString("weaponType", "UNKNOWN"))
                .requiresLineOfSight(section.getBoolean("requiresLineOfSight", false))
                .isMainAttack(section.getBoolean("isMainAttack", false))
                .isSustain(section.getBoolean("isSustain", false));
        
        if (section.contains("sustainCost")) {
            builder.sustainCost(section.getDouble("sustainCost", 0.0));
        }
        
        if (section.contains("range")) {
            builder.range(section.getDouble("range", 0.0));
        }
        
        if (section.contains("castTime")) {
            builder.castTime(section.getInt("castTime", 0));
        }
        
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
