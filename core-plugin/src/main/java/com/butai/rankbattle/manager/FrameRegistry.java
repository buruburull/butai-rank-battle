package com.butai.rankbattle.manager;

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
}
