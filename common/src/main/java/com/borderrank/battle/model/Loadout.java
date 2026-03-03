package com.borderrank.battle.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a player's loadout with up to 8 trigger slots.
 * Slots 0-3 are main triggers, slots 4-7 are sub triggers.
 */
public class Loadout {
    private static final int SLOT_COUNT = 8;
    private static final int MAIN_SLOTS = 4;

    private final String name;
    private final String[] slots;
    private int totalCost;

    /**
     * Constructs a Loadout instance.
     *
     * @param name the loadout name
     */
    public Loadout(String name) {
        this.name = name;
        this.slots = new String[SLOT_COUNT];
        this.totalCost = 0;
    }

    /**
     * Constructs a Loadout instance with initial slot data.
     *
     * @param name the loadout name
     * @param slots the initial slots (must be size 8)
     */
    public Loadout(String name, String[] slots) {
        if (slots.length != SLOT_COUNT) {
            throw new IllegalArgumentException("Slots array must have exactly " + SLOT_COUNT + " elements");
        }
        this.name = name;
        this.slots = slots.clone();
        this.totalCost = 0;
    }

    /**
     * Gets the loadout name.
     *
     * @return the loadout name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the trigger ID at the specified slot.
     *
     * @param slot the slot index (0-7)
     * @return the trigger ID, or null if empty
     * @throws IndexOutOfBoundsException if slot is out of range
     */
    public String getSlot(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Slot must be between 0 and " + (SLOT_COUNT - 1));
        }
        return slots[slot];
    }

    /**
     * Sets a trigger at the specified slot.
     *
     * @param slot the slot index (0-7)
     * @param triggerId the trigger ID, or null to clear the slot
     * @throws IndexOutOfBoundsException if slot is out of range
     */
    public void setSlot(int slot, String triggerId) {
        if (slot < 0 || slot >= SLOT_COUNT) {
            throw new IndexOutOfBoundsException("Slot must be between 0 and " + (SLOT_COUNT - 1));
        }
        slots[slot] = triggerId;
    }

    /**
     * Gets all slots.
     *
     * @return the slots array
     */
    public String[] getSlots() {
        return slots.clone();
    }

    /**
     * Gets the total cost of the loadout.
     *
     * @return the total cost
     */
    public int getTotalCost() {
        return totalCost;
    }

    /**
     * Calculates the total cost of this loadout based on trigger data.
     *
     * @param triggerDataMap a map of trigger ID to TriggerData
     * @return the calculated total cost
     */
    public int calculateTotalCost(Map<String, TriggerData> triggerDataMap) {
        int cost = 0;
        for (String triggerId : slots) {
            if (triggerId != null && !triggerId.isEmpty()) {
                TriggerData triggerData = triggerDataMap.get(triggerId);
                if (triggerData != null) {
                    cost += triggerData.getCost();
                }
            }
        }
        this.totalCost = cost;
        return cost;
    }

    /**
     * Checks if this loadout is valid based on the maximum TP.
     *
     * @param maxTP the maximum TP available
     * @return true if the total cost is within the limit, false otherwise
     */
    public boolean isValid(int maxTP) {
        return totalCost <= maxTP;
    }

    /**
     * Gets all main triggers (slots 0-3).
     *
     * @return a list of main trigger IDs (excludes null entries)
     */
    public List<String> getMainTriggers() {
        List<String> mainTriggers = new ArrayList<>();
        for (int i = 0; i < MAIN_SLOTS; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) {
                mainTriggers.add(slots[i]);
            }
        }
        return mainTriggers;
    }

    /**
     * Gets all sub triggers (slots 4-7).
     *
     * @return a list of sub trigger IDs (excludes null entries)
     */
    public List<String> getSubTriggers() {
        List<String> subTriggers = new ArrayList<>();
        for (int i = MAIN_SLOTS; i < SLOT_COUNT; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) {
                subTriggers.add(slots[i]);
            }
        }
        return subTriggers;
    }

    /**
     * Gets the count of non-null slots.
     *
     * @return the number of filled slots
     */
    public int getFilledSlotCount() {
        int count = 0;
        for (String slot : slots) {
            if (slot != null && !slot.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return "Loadout{" +
                "name='" + name + '\'' +
                ", totalCost=" + totalCost +
                ", filledSlots=" + getFilledSlotCount() +
                '}';
    }
}
