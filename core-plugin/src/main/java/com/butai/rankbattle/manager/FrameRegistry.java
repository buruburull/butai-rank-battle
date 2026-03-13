package com.butai.rankbattle.manager;

import com.butai.rankbattle.model.ArenaMap;
import com.butai.rankbattle.model.FrameCategory;
import com.butai.rankbattle.model.FrameData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FrameRegistry {

    private final Logger logger;
    private final Map<String, FrameData> frames = new LinkedHashMap<>();
    private final Map<String, ArenaMap> arenaMaps = new LinkedHashMap<>();

    public FrameRegistry(Logger logger) {
        this.logger = logger;
    }

    /**
     * Load all frames from frames.yml.
     */
    public void loadFromFile(File file) {
        frames.clear();

        if (!file.exists()) {
            logger.warning("frames.yml not found at: " + file.getAbsolutePath());
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection framesSection = config.getConfigurationSection("frames");
        if (framesSection == null) {
            logger.warning("No 'frames' section found in frames.yml");
            return;
        }

        for (String id : framesSection.getKeys(false)) {
            ConfigurationSection sec = framesSection.getConfigurationSection(id);
            if (sec == null) continue;

            String name = sec.getString("name", id);
            FrameCategory category = FrameCategory.fromString(sec.getString("category", "support"));
            if (category == null) {
                logger.warning("Invalid category for frame: " + id);
                continue;
            }

            String mcItem = sec.getString("mc_item", "IRON_INGOT");
            int damage = sec.getInt("damage", 0);
            double damageMultiplier = sec.getDouble("damage_multiplier", 1.0);
            int etherUse = sec.getInt("ether_use", 0);
            int etherSustain = sec.getInt("ether_sustain", 0);
            int cooldown = sec.getInt("cooldown", 0);
            String description = sec.getString("description", "");

            FrameData frameData = new FrameData(id, name, category, mcItem,
                    damage, damageMultiplier, etherUse, etherSustain, cooldown, description);
            frames.put(id, frameData);
        }

        logger.info("Loaded " + frames.size() + " frames from frames.yml");

        // Load arena maps
        loadMaps(config);
    }

    /**
     * Load arena maps from the 'maps' section of frames.yml.
     */
    private void loadMaps(YamlConfiguration config) {
        arenaMaps.clear();

        ConfigurationSection mapsSection = config.getConfigurationSection("maps");
        if (mapsSection == null) {
            logger.info("No 'maps' section found in frames.yml");
            return;
        }

        for (String id : mapsSection.getKeys(false)) {
            ConfigurationSection sec = mapsSection.getConfigurationSection(id);
            if (sec == null) continue;

            String name = sec.getString("name", id);
            String worldName = sec.getString("world", "world");

            double s1x = sec.getDouble("spawn1.x", 15.0);
            double s1y = sec.getDouble("spawn1.y", 64.0);
            double s1z = sec.getDouble("spawn1.z", 0.0);
            double s2x = sec.getDouble("spawn2.x", -15.0);
            double s2y = sec.getDouble("spawn2.y", 64.0);
            double s2z = sec.getDouble("spawn2.z", 0.0);
            double spX = sec.getDouble("spectate.x", 0.0);
            double spY = sec.getDouble("spectate.y", 70.0);
            double spZ = sec.getDouble("spectate.z", 0.0);
            int borderRadius = sec.getInt("border_radius", 50);
            String description = sec.getString("description", "");

            ArenaMap map = new ArenaMap(id, name, worldName,
                    s1x, s1y, s1z, s2x, s2y, s2z,
                    spX, spY, spZ, borderRadius, description);
            arenaMaps.put(id, map);
        }

        logger.info("Loaded " + arenaMaps.size() + " arena maps from frames.yml");
    }

    /**
     * Get frame data by ID.
     */
    public FrameData getFrame(String id) {
        return frames.get(id.toLowerCase());
    }

    /**
     * Get all frames.
     */
    public Collection<FrameData> getAllFrames() {
        return Collections.unmodifiableCollection(frames.values());
    }

    /**
     * Get frames by category.
     */
    public List<FrameData> getFramesByCategory(FrameCategory category) {
        return frames.values().stream()
                .filter(f -> f.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Get all frame IDs.
     */
    public Set<String> getFrameIds() {
        return Collections.unmodifiableSet(frames.keySet());
    }

    /**
     * Check if a frame exists.
     */
    public boolean exists(String id) {
        return frames.containsKey(id.toLowerCase());
    }

    /**
     * Get frame count.
     */
    public int size() {
        return frames.size();
    }

    // ==================== Arena Map Methods ====================

    /**
     * Get arena map by ID.
     */
    public ArenaMap getArenaMap(String id) {
        return arenaMaps.get(id.toLowerCase());
    }

    /**
     * Get all arena maps.
     */
    public Collection<ArenaMap> getAllArenaMaps() {
        return Collections.unmodifiableCollection(arenaMaps.values());
    }

    /**
     * Get all arena map IDs.
     */
    public Set<String> getArenaMapIds() {
        return Collections.unmodifiableSet(arenaMaps.keySet());
    }

    /**
     * Get arena map count.
     */
    public int getArenaMapCount() {
        return arenaMaps.size();
    }
}
