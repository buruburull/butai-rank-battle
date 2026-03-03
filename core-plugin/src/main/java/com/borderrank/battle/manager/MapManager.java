package com.borderrank.battle.manager;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Manages available maps and arena selection for matches.
 */
public class MapManager {
    
    private final List<String> availableMaps = new ArrayList<>();
    private final Random random = new Random();

    /**
     * Loads map definitions from configuration.
     * Reads from the maps section of the config.
     *
     * @param config the FileConfiguration containing the maps section
     */
    public void loadMaps(FileConfiguration config) {
        availableMaps.clear();
        
        if (config == null || !config.contains("maps")) {
            return;
        }
        
        var mapsSection = config.getConfigurationSection("maps");
        if (mapsSection == null) {
            return;
        }
        
        for (String mapName : mapsSection.getKeys(false)) {
            var mapSection = mapsSection.getConfigurationSection(mapName);
            if (mapSection == null) {
                continue;
            }
            
            // Validate map has required properties (name, spawn points)
            if (mapSection.contains("name") && mapSection.contains("spawn-points")) {
                availableMaps.add(mapName);
            }
        }
    }

    /**
     * Selects a random map from the available maps.
     *
     * @return a randomly selected map name, or null if no maps are available
     */
    public String selectRandomMap() {
        if (availableMaps.isEmpty()) {
            return null;
        }
        
        return availableMaps.get(random.nextInt(availableMaps.size()));
    }

    /**
     * Gets all available maps.
     *
     * @return an unmodifiable list of map names
     */
    public List<String> getAvailableMaps() {
        return Collections.unmodifiableList(availableMaps);
    }

    /**
     * Gets the number of available maps.
     *
     * @return the count of maps
     */
    public int getMapCount() {
        return availableMaps.size();
    }

    /**
     * Checks if a specific map is available.
     *
     * @param mapName the name of the map
     * @return true if the map exists, false otherwise
     */
    public boolean mapExists(String mapName) {
        return availableMaps.contains(mapName);
    }

    /**
     * Adds a map to the available list.
     *
     * @param mapName the name of the map to add
     */
    public void addMap(String mapName) {
        if (!availableMaps.contains(mapName)) {
            availableMaps.add(mapName);
        }
    }

    /**
     * Removes a map from the available list.
     *
     * @param mapName the name of the map to remove
     * @return true if the map was removed, false if it didn't exist
     */
    public boolean removeMap(String mapName) {
        return availableMaps.remove(mapName);
    }

    /**
     * Clears all maps from the available list.
     */
    public void clearMaps() {
        availableMaps.clear();
    }
}
