package com.borderrank.battle.manager;

import com.borderrank.battle.model.Loadout;
import com.borderrank.battle.model.TriggerData;
import com.borderrank.battle.model.WeaponType;
import com.borderrank.battle.database.LoadoutDAO;

import java.sql.Connection;
import java.util.*;

/**
 * Manages player loadouts with in-memory caching and database persistence.
 * Handles loadout validation, slot management, and weapon type detection.
 */
public class LoadoutManager {
    
    private final Map<UUID, Map<String, Loadout>> playerLoadouts = new HashMap<>();
    private final LoadoutDAO loadoutDAO;

    /**
     * Constructs a LoadoutManager with database access.
     *
     * @param loadoutDAO the data access object for loadout persistence
     */
    public LoadoutManager(LoadoutDAO loadoutDAO) {
        this.loadoutDAO = loadoutDAO;
    }

    /**
     * Loads all loadouts for a player from the database.
     *
     * @param playerId the UUID of the player
     * @param connection the database connection
     * @throws Exception if database operations fail
     */
    public void loadPlayerLoadouts(UUID playerId, Connection connection) throws Exception {
        var loadouts = loadoutDAO.getPlayerLoadouts(playerId, connection);
        playerLoadouts.put(playerId, loadouts);
    }

    /**
     * Saves or updates a loadout to the database and cache.
     *
     * @param playerId the UUID of the player
     * @param loadout the loadout to save
     * @param connection the database connection
     * @throws Exception if database operations fail
     */
    public void saveLoadout(UUID playerId, Loadout loadout, Connection connection) throws Exception {
        loadoutDAO.saveLoadout(playerId, loadout, connection);
        var playerLoadouts = this.playerLoadouts.computeIfAbsent(playerId, k -> new HashMap<>());
        playerLoadouts.put(loadout.getName(), loadout);
    }

    /**
     * Retrieves the active loadout for a player.
     * Returns the default loadout if no active loadout is set.
     *
     * @param playerId the UUID of the player
     * @return the active loadout, or null if none exists
     */
    public Loadout getActiveLoadout(UUID playerId) {
        var playerLoadouts = this.playerLoadouts.get(playerId);
        if (playerLoadouts == null || playerLoadouts.isEmpty()) {
            return null;
        }
        
        return playerLoadouts.values().stream()
                .filter(Loadout::isActive)
                .findFirst()
                .orElseGet(() -> playerLoadouts.values().iterator().next());
    }

    /**
     * Retrieves a specific loadout by name.
     *
     * @param playerId the UUID of the player
     * @param loadoutName the name of the loadout
     * @return the loadout, or null if not found
     */
    public Loadout getLoadout(UUID playerId, String loadoutName) {
        var playerLoadouts = this.playerLoadouts.get(playerId);
        return playerLoadouts != null ? playerLoadouts.get(loadoutName) : null;
    }

    /**
     * Retrieves all loadouts for a player.
     *
     * @param playerId the UUID of the player
     * @return a map of loadout names to Loadout objects
     */
    public Map<String, Loadout> getPlayerLoadouts(UUID playerId) {
        var loadouts = playerLoadouts.get(playerId);
        return loadouts != null ? new HashMap<>(loadouts) : new HashMap<>();
    }

    /**
     * Sets a trigger in a specific loadout slot.
     * Validates that the total cost does not exceed the maximum.
     *
     * @param playerId the UUID of the player
     * @param loadoutName the name of the loadout
     * @param slotIndex the slot index (0-indexed)
     * @param triggerId the ID of the trigger to equip
     * @param triggerRegistry the registry containing trigger definitions
     * @param maxTP the maximum trion points allowed
     * @return true if the trigger was set successfully, false otherwise
     */
    public boolean setSlot(UUID playerId, String loadoutName, int slotIndex, String triggerId, 
                          TriggerRegistry triggerRegistry, int maxTP) {
        var loadout = getLoadout(playerId, loadoutName);
        if (loadout == null) {
            return false;
        }
        
        var triggerData = triggerRegistry.get(triggerId);
        if (triggerData == null) {
            return false;
        }
        
        var slots = loadout.getSlots();
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return false;
        }
        
        // Calculate the cost change
        var currentSlot = slots.get(slotIndex);
        int currentCost = currentSlot != null && !currentSlot.isEmpty() ? 
                triggerRegistry.get(currentSlot).getCost() : 0;
        int newCost = triggerData.getCost();
        int costDifference = newCost - currentCost;
        
        // Check if adding this trigger exceeds the limit
        if (loadout.getTotalCost() + costDifference > maxTP) {
            return false;
        }
        
        slots.set(slotIndex, triggerId);
        return true;
    }

    /**
     * Validates a loadout against specified criteria.
     * Checks cost constraints, main attack requirement, and trigger availability.
     *
     * @param loadout the loadout to validate
     * @param triggerRegistry the registry containing trigger definitions
     * @param maxTP the maximum trion points allowed
     * @return true if the loadout is valid, false otherwise
     */
    public boolean validateLoadout(Loadout loadout, TriggerRegistry triggerRegistry, int maxTP) {
        if (loadout == null || loadout.getSlots().isEmpty()) {
            return false;
        }
        
        int totalCost = 0;
        boolean hasMainAttack = false;
        
        for (int i = 0; i < loadout.getSlots().size(); i++) {
            String triggerId = loadout.getSlots().get(i);
            
            if (triggerId == null || triggerId.isEmpty()) {
                // Slots may be empty, but slot 0 (main attack) must have a trigger
                if (i == 0) {
                    return false;
                }
                continue;
            }
            
            TriggerData triggerData = triggerRegistry.get(triggerId);
            if (triggerData == null) {
                return false;
            }
            
            totalCost += triggerData.getCost();
            
            // Slots 0-3 are main attack slots
            if (i < 4 && triggerData.isMainAttack()) {
                hasMainAttack = true;
            }
        }
        
        // Must have at least one main attack and stay within cost limit
        return hasMainAttack && totalCost <= maxTP;
    }

    /**
     * Determines the dominant weapon type from a loadout's main attack slots.
     * Examines slots 0-3 and returns the most common weapon type.
     *
     * @param loadout the loadout to analyze
     * @param triggerRegistry the registry containing trigger definitions
     * @return the dominant WeaponType, or null if unable to determine
     */
    public WeaponType getWeaponType(Loadout loadout, TriggerRegistry triggerRegistry) {
        if (loadout == null || loadout.getSlots().isEmpty()) {
            return null;
        }
        
        Map<String, Integer> weaponTypeCounts = new HashMap<>();
        
        // Count weapon types from main attack slots (0-3)
        for (int i = 0; i < Math.min(4, loadout.getSlots().size()); i++) {
            String triggerId = loadout.getSlots().get(i);
            
            if (triggerId == null || triggerId.isEmpty()) {
                continue;
            }
            
            TriggerData triggerData = triggerRegistry.get(triggerId);
            if (triggerData != null && triggerData.isMainAttack()) {
                String weaponType = triggerData.getWeaponType();
                weaponTypeCounts.put(weaponType, weaponTypeCounts.getOrDefault(weaponType, 0) + 1);
            }
        }
        
        // Return the most common weapon type
        return weaponTypeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> {
                    try {
                        return WeaponType.valueOf(entry.getKey().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    /**
     * Clears all cached loadouts for a player.
     *
     * @param playerId the UUID of the player
     */
    public void clearPlayerCache(UUID playerId) {
        playerLoadouts.remove(playerId);
    }

    /**
     * Clears all cached loadouts.
     */
    public void clearAllCache() {
        playerLoadouts.clear();
    }
}
