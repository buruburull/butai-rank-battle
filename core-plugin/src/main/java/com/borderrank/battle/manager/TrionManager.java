package com.borderrank.battle.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Manages real-time trion (resource) management during matches.
 * Handles trion consumption, regeneration, leaks, and sustain costs.
 */
public class TrionManager {
    
    private final Map<UUID, Double> currentTrion = new HashMap<>();
    private final Map<UUID, Integer> maxTrion = new HashMap<>();
    private final Map<UUID, Set<String>> activeSustainTriggers = new HashMap<>();
    private BukkitTask tickTask;

    private static final double HP_LEAK_COEFFICIENT = 0.5; // Trion leak per missing HP per second
    private static final int WARNING_THRESHOLD_HIGH = 200; // Yellow warning
    private static final int WARNING_THRESHOLD_CRITICAL = 100; // Red critical

    /**
     * Initializes a player's trion at maximum value.
     *
     * @param playerId the UUID of the player
     * @param max the maximum trion value
     */
    public void initPlayer(UUID playerId, int max) {
        maxTrion.put(playerId, max);
        currentTrion.put(playerId, (double) max);
        activeSustainTriggers.put(playerId, new HashSet<>());
    }

    /**
     * Consumes trion from a player's current amount.
     *
     * @param playerId the UUID of the player
     * @param amount the amount of trion to consume
     * @return true if the player had sufficient trion, false otherwise
     */
    public boolean consumeTrion(UUID playerId, double amount) {
        Double current = currentTrion.get(playerId);
        if (current == null || current < amount) {
            return false;
        }
        
        currentTrion.put(playerId, current - amount);
        return true;
    }

    /**
     * Adds trion to a player's current amount.
     * Cannot exceed the maximum.
     *
     * @param playerId the UUID of the player
     * @param amount the amount of trion to add
     */
    public void addTrion(UUID playerId, double amount) {
        Double current = currentTrion.getOrDefault(playerId, 0.0);
        Integer max = maxTrion.get(playerId);
        
        if (max == null) {
            return;
        }
        
        double newValue = Math.min(current + amount, max);
        currentTrion.put(playerId, newValue);
    }

    /**
     * Retrieves the current trion value for a player.
     *
     * @param playerId the UUID of the player
     * @return the current trion amount, or 0 if player not initialized
     */
    public double getTrion(UUID playerId) {
        return currentTrion.getOrDefault(playerId, 0.0);
    }

    /**
     * Retrieves the maximum trion value for a player.
     *
     * @param playerId the UUID of the player
     * @return the maximum trion amount, or 0 if player not initialized
     */
    public int getMaxTrion(UUID playerId) {
        return maxTrion.getOrDefault(playerId, 0);
    }

    /**
     * Marks a sustain trigger as active for a player.
     *
     * @param playerId the UUID of the player
     * @param triggerId the ID of the sustain trigger
     */
    public void activateSustain(UUID playerId, String triggerId) {
        var sustains = activeSustainTriggers.computeIfAbsent(playerId, k -> new HashSet<>());
        sustains.add(triggerId);
    }

    /**
     * Marks a sustain trigger as inactive for a player.
     *
     * @param playerId the UUID of the player
     * @param triggerId the ID of the sustain trigger
     */
    public void deactivateSustain(UUID playerId, String triggerId) {
        var sustains = activeSustainTriggers.get(playerId);
        if (sustains != null) {
            sustains.remove(triggerId);
        }
    }

    /**
     * Starts the repeating tick task that manages trion decay and sustain costs.
     * Runs every 20 ticks (1 second) and updates XP bar display.
     *
     * @param plugin the plugin instance for task scheduling
     * @param activePlayers a set of player UUIDs currently in matches
     */
    public void startTickLoop(Plugin plugin, Set<UUID> activePlayers) {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID playerId : new ArrayList<>(activePlayers)) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    continue;
                }
                
                Integer maxTrionValue = maxTrion.get(playerId);
                if (maxTrionValue == null) {
                    continue;
                }
                
                double currentValue = currentTrion.getOrDefault(playerId, (double) maxTrionValue);
                
                // Calculate HP leak
                int maxHealth = (int) player.getHealthScale();
                if (maxHealth == 0) {
                    maxHealth = 20;
                }
                double currentHealth = player.getHealth();
                double missingHP = maxHealth - currentHealth;
                double hpLeak = missingHP * HP_LEAK_COEFFICIENT;
                
                // Calculate sustain costs
                double sustainCost = 0.0;
                var sustains = activeSustainTriggers.getOrDefault(playerId, new HashSet<>());
                // Note: sustain cost calculation requires TriggerRegistry injection
                // For now, we apply a base cost per active sustain
                sustainCost = sustains.size() * 1.0; // 1 TP per sustain per second
                
                // Apply decay
                double newTrion = currentValue - hpLeak - sustainCost;
                newTrion = Math.max(0, newTrion);
                
                currentTrion.put(playerId, newTrion);
                
                // Update XP bar
                updateXPBar(player, newTrion, maxTrionValue);
                
                // Show warning if below threshold
                showTrionWarning(player, newTrion, maxTrionValue);
                
                // Check bailout condition
                if (newTrion <= 0) {
                    triggerBailout(player);
                }
            }
        }, 20L, 20L); // Run every 20 ticks (1 second), start after 1 second
    }

    /**
     * Stops the repeating tick task.
     */
    public void stopTickLoop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /**
     * Triggers an emergency bailout for a player when trion is depleted.
     * Kills the player and broadcasts a message.
     *
     * @param player the player to bail out
     */
    public void triggerBailout(Player player) {
        player.setHealth(0);
        Bukkit.broadcastMessage("§c[BAILOUT] " + player.getName() + " was eliminated (Trion depleted)!");
    }

    /**
     * Updates the XP bar to display current trion status.
     * Level shows integer trion amount, experience bar shows percentage.
     *
     * @param player the player to update
     * @param trion the current trion amount
     * @param maxTrion the maximum trion amount
     */
    public void updateXPBar(Player player, double trion, int maxTrion) {
        player.setLevel((int) Math.ceil(trion));
        if (maxTrion > 0) {
            float percentage = (float) (trion / maxTrion);
            player.setExp(Math.max(0, Math.min(1, percentage)));
        }
    }

    /**
     * Displays a trion warning in the action bar when trion falls below thresholds.
     *
     * @param player the player to warn
     * @param trion the current trion amount
     * @param maxTrion the maximum trion amount
     */
    private void showTrionWarning(Player player, double trion, int maxTrion) {
        if (trion < WARNING_THRESHOLD_CRITICAL) {
            // Red blinking critical warning
            String blinking = System.currentTimeMillis() % 500 < 250 ? "§4" : "§c";
            player.sendActionBar(blinking + "⚠ CRITICAL: Trion depleting! ⚠");
        } else if (trion < WARNING_THRESHOLD_HIGH) {
            // Yellow warning
            player.sendActionBar("§e⚠ Low Trion: " + (int) trion + " / " + maxTrion);
        }
    }

    /**
     * Checks if a player is initialized in the trion manager.
     *
     * @param playerId the UUID of the player
     * @return true if the player is initialized, false otherwise
     */
    public boolean isPlayerInitialized(UUID playerId) {
        return currentTrion.containsKey(playerId);
    }

    /**
     * Removes a player from the trion manager (usually when match ends).
     *
     * @param playerId the UUID of the player
     */
    public void removePlayer(UUID playerId) {
        currentTrion.remove(playerId);
        maxTrion.remove(playerId);
        activeSustainTriggers.remove(playerId);
    }

    /**
     * Clears all player data from the manager.
     */
    public void clearAll() {
        currentTrion.clear();
        maxTrion.clear();
        activeSustainTriggers.clear();
        stopTickLoop();
    }

    /**
     * Gets the number of active sustain triggers for a player.
     *
     * @param playerId the UUID of the player
     * @return the count of active sustain triggers
     */
    public int getActiveSustainCount(UUID playerId) {
        var sustains = activeSustainTriggers.get(playerId);
        return sustains != null ? sustains.size() : 0;
    }
}
