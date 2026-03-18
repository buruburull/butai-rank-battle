package com.butai.rankbattle.manager;

import com.butai.rankbattle.model.FrameData;
import com.butai.rankbattle.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages ether (energy) for players during matches.
 * - Max ether: 1000
 * - XP bar display: level = ether amount, bar = percentage
 * - HP leak: (maxHP - currentHP) * 0.5 per second
 * - Use cost: per-activation deduction
 * - Sustain cost: per-second deduction for toggle frames
 * - Warnings at 200 (yellow) and 100 (red blink)
 * - E-Shift at 0: teleport to lobby (not kill)
 */
public class EtherManager {

    private static final int DEFAULT_MAX_ETHER = 1000;
    private static final double DEFAULT_LEAK_COEFFICIENT = 0.5;
    private static final int WARNING_YELLOW = 200;
    private static final int WARNING_RED = 100;

    // Current leak coefficient (can be changed for sudden death)
    private double leakCoefficient = DEFAULT_LEAK_COEFFICIENT;

    private final JavaPlugin plugin;
    private final Logger logger;

    // Per-player max ether (dynamic, based on growth)
    private final Map<UUID, Integer> maxEtherMap = new ConcurrentHashMap<>();

    // Player ether state
    private final Map<UUID, Integer> etherMap = new ConcurrentHashMap<>();
    // Active sustain frames per player: frameId -> sustainCost
    private final Map<UUID, Map<String, Integer>> activeSustains = new ConcurrentHashMap<>();
    // E-Shift prevention flag
    private final Set<UUID> eShifted = ConcurrentHashMap.newKeySet();
    // Tick counter for red blink alternation
    private final Map<UUID, Integer> blinkCounter = new ConcurrentHashMap<>();

    // Lobby location (set from config)
    private Location lobbyLocation;

    // Tick task reference
    private BukkitTask tickTask;

    // Callback interface for match notification on E-Shift
    private EShiftCallback eShiftCallback;

    @FunctionalInterface
    public interface EShiftCallback {
        void onEShift(UUID playerUuid);
    }

    public EtherManager(JavaPlugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    /**
     * Set the lobby location for E-Shift teleport.
     */
    public void setLobbyLocation(Location location) {
        this.lobbyLocation = location;
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    /**
     * Set the callback for E-Shift events.
     */
    public void setEShiftCallback(EShiftCallback callback) {
        this.eShiftCallback = callback;
    }

    /**
     * Set a player's max ether (call before initPlayer, based on growth level).
     */
    public void setMaxEther(UUID uuid, int maxEther) {
        maxEtherMap.put(uuid, maxEther);
    }

    /**
     * Initialize a player's ether at match start.
     */
    public void initPlayer(UUID uuid) {
        int maxEther = getMaxEther(uuid);
        etherMap.put(uuid, maxEther);
        activeSustains.put(uuid, new ConcurrentHashMap<>());
        eShifted.remove(uuid);
        blinkCounter.put(uuid, 0);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            updateXpBar(player, maxEther, uuid);
        }
    }

    /**
     * Remove a player from ether tracking (match end or quit).
     */
    public void removePlayer(UUID uuid) {
        etherMap.remove(uuid);
        maxEtherMap.remove(uuid);
        activeSustains.remove(uuid);
        eShifted.remove(uuid);
        blinkCounter.remove(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.setLevel(0);
            player.setExp(0f);
        }
    }

    /**
     * Get current ether for a player.
     */
    public int getEther(UUID uuid) {
        return etherMap.getOrDefault(uuid, 0);
    }

    /**
     * Check if a player is being tracked by the ether system.
     */
    public boolean isTracking(UUID uuid) {
        return etherMap.containsKey(uuid);
    }

    /**
     * Check if a player has E-Shifted.
     */
    public boolean hasEShifted(UUID uuid) {
        return eShifted.contains(uuid);
    }

    /**
     * Consume ether for a frame use (one-time cost).
     * Returns true if enough ether was available, false otherwise.
     */
    public boolean consumeUse(UUID uuid, FrameData frame) {
        if (frame.getEtherUse() <= 0) return true;
        return consumeUse(uuid, frame.getEtherUse());
    }

    /**
     * Consume a specific amount of ether.
     * Returns true if enough ether was available.
     */
    public boolean consumeUse(UUID uuid, int amount) {
        Integer current = etherMap.get(uuid);
        if (current == null || current < amount) return false;

        int newEther = Math.max(0, current - amount);
        etherMap.put(uuid, newEther);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            updateXpBar(player, newEther, uuid);
        }

        if (newEther <= 0) {
            triggerEShift(uuid);
        }
        return true;
    }

    /**
     * Activate a sustain frame for a player.
     */
    public void activateSustain(UUID uuid, FrameData frame) {
        Map<String, Integer> sustains = activeSustains.get(uuid);
        if (sustains != null) {
            sustains.put(frame.getId(), frame.getEtherSustain());
        }
    }

    /**
     * Deactivate a sustain frame for a player.
     */
    public void deactivateSustain(UUID uuid, String frameId) {
        Map<String, Integer> sustains = activeSustains.get(uuid);
        if (sustains != null) {
            sustains.remove(frameId);
        }
    }

    /**
     * Check if a sustain frame is active for a player.
     */
    public boolean isSustainActive(UUID uuid, String frameId) {
        Map<String, Integer> sustains = activeSustains.get(uuid);
        return sustains != null && sustains.containsKey(frameId);
    }

    /**
     * Start the ether tick loop (call once, runs every 20 ticks = 1 second).
     */
    public void startTickLoop() {
        if (tickTask != null) return;

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Stop the ether tick loop.
     */
    public void stopTickLoop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /**
     * Called every second. Processes HP leak, sustain costs, warnings, and E-Shift.
     */
    private void tick() {
        for (Map.Entry<UUID, Integer> entry : etherMap.entrySet()) {
            UUID uuid = entry.getKey();
            int currentEther = entry.getValue();

            if (eShifted.contains(uuid)) continue;

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            int drain = 0;

            // HP leak: (maxHP - currentHP) * 0.5
            double maxHealth = player.getMaxHealth();
            double currentHealth = player.getHealth();
            if (currentHealth < maxHealth) {
                drain += (int) Math.ceil((maxHealth - currentHealth) * leakCoefficient);
            }

            // Sustain costs
            Map<String, Integer> sustains = activeSustains.get(uuid);
            if (sustains != null && !sustains.isEmpty()) {
                for (int cost : sustains.values()) {
                    drain += cost;
                }
            }

            // Apply drain
            if (drain > 0) {
                int newEther = Math.max(0, currentEther - drain);
                etherMap.put(uuid, newEther);
                currentEther = newEther;
            }

            // Update XP bar
            updateXpBar(player, currentEther, uuid);

            // Warnings
            if (currentEther <= 0) {
                triggerEShift(uuid);
            } else if (currentEther <= WARNING_RED) {
                // Red blink: alternate title visibility
                int blink = blinkCounter.getOrDefault(uuid, 0);
                if (blink % 2 == 0) {
                    player.sendTitle("§c§l⚠ E-Shift 危険 ⚠", "§cエーテル: " + currentEther, 0, 25, 5);
                }
                blinkCounter.put(uuid, blink + 1);
            } else if (currentEther <= WARNING_YELLOW) {
                MessageUtil.sendWarning(player, "⚠ エーテル残量低下: §f" + currentEther);
            }
        }
    }

    /**
     * Trigger E-Shift for a player (ether depleted).
     */
    private void triggerEShift(UUID uuid) {
        if (!eShifted.add(uuid)) return; // Already E-Shifted (double prevention)

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            // Visual feedback
            player.sendTitle("§c§lE-SHIFT", "§7エーテル枯渇 - 緊急離脱", 5, 40, 20);
            MessageUtil.send(player, "§c§lエマージェンシーシフト発動！ §7エーテルが枯渇しました。");

            // Teleport to lobby
            if (lobbyLocation != null) {
                player.teleport(lobbyLocation);
            } else {
                // Fallback: teleport to world spawn
                player.teleport(player.getWorld().getSpawnLocation());
            }

            // Clear sustains
            activeSustains.remove(uuid);

            // Reset XP bar
            player.setLevel(0);
            player.setExp(0f);
        }

        // Notify match system
        if (eShiftCallback != null) {
            eShiftCallback.onEShift(uuid);
        }

        logger.info("E-Shift triggered for " + uuid);
    }

    /**
     * Update the XP bar to reflect ether amount.
     * Level number = ether amount, bar = percentage.
     */
    private void updateXpBar(Player player, int ether, UUID uuid) {
        player.setLevel(ether);
        int maxEther = getMaxEther(uuid);
        player.setExp(Math.max(0f, Math.min(1f, (float) ether / maxEther)));
    }

    /**
     * Set the leak coefficient (default 0.5, sudden death uses 1.5).
     */
    public void setLeakCoefficient(double coefficient) {
        this.leakCoefficient = coefficient;
    }

    /**
     * Reset leak coefficient to default.
     */
    public void resetLeakCoefficient() {
        this.leakCoefficient = DEFAULT_LEAK_COEFFICIENT;
    }

    /**
     * Get the maximum ether value for a player.
     */
    public int getMaxEther(UUID uuid) {
        return maxEtherMap.getOrDefault(uuid, DEFAULT_MAX_ETHER);
    }

    /**
     * Get the default max ether (for non-growth contexts).
     */
    public int getDefaultMaxEther() {
        return DEFAULT_MAX_ETHER;
    }

    /**
     * Check if any players are currently being tracked.
     */
    public boolean hasActivePlayers() {
        return !etherMap.isEmpty();
    }

    /**
     * Get all tracked player UUIDs.
     */
    public Set<UUID> getTrackedPlayers() {
        return Collections.unmodifiableSet(etherMap.keySet());
    }
}
