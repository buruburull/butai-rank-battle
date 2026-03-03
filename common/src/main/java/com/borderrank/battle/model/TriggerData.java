package com.borderrank.battle.model;

import org.bukkit.Material;

/**
 * Represents a Trigger definition loaded from YAML configuration.
 */
public class TriggerData {
    /**
     * Enum for trigger categories.
     */
    public enum TriggerCategory {
        ATTACKER,
        SHOOTER,
        SNIPER,
        SUPPORT
    }

    /**
     * Enum for slot types.
     */
    public enum SlotType {
        MAIN,
        SUB,
        BOTH
    }

    private final String id;
    private final String name;
    private final TriggerCategory category;
    private final int cost;
    private final int trionUse;
    private final double trionSustain;
    private final SlotType slotType;
    private final Material mcItem;
    private final int cooldown;
    private final String description;

    /**
     * Constructs a TriggerData instance.
     *
     * @param id the trigger ID
     * @param name the trigger name
     * @param category the trigger category
     * @param cost the cost in TP
     * @param trionUse the trion usage
     * @param trionSustain the trion sustain rate
     * @param slotType the slot type
     * @param mcItem the Minecraft item representation
     * @param cooldown the cooldown in ticks
     * @param description the trigger description
     */
    public TriggerData(String id, String name, TriggerCategory category, int cost, int trionUse,
                       double trionSustain, SlotType slotType, Material mcItem, int cooldown,
                       String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.cost = cost;
        this.trionUse = trionUse;
        this.trionSustain = trionSustain;
        this.slotType = slotType;
        this.mcItem = mcItem;
        this.cooldown = cooldown;
        this.description = description;
    }

    /**
     * Gets the trigger ID.
     *
     * @return the trigger ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the trigger name.
     *
     * @return the trigger name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the trigger category.
     *
     * @return the trigger category
     */
    public TriggerCategory getCategory() {
        return category;
    }

    /**
     * Gets the cost in TP.
     *
     * @return the cost
     */
    public int getCost() {
        return cost;
    }

    /**
     * Gets the trion usage.
     *
     * @return the trion usage
     */
    public int getTrionUse() {
        return trionUse;
    }

    /**
     * Gets the trion sustain rate.
     *
     * @return the trion sustain rate
     */
    public double getTrionSustain() {
        return trionSustain;
    }

    /**
     * Gets the slot type.
     *
     * @return the slot type
     */
    public SlotType getSlotType() {
        return slotType;
    }

    /**
     * Gets the Minecraft item representation.
     *
     * @return the Minecraft item
     */
    public Material getMcItem() {
        return mcItem;
    }

    /**
     * Gets the cooldown in ticks.
     *
     * @return the cooldown in ticks
     */
    public int getCooldown() {
        return cooldown;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Builder class for TriggerData.
     */
    public static class Builder {
        private String id;
        private String name;
        private TriggerCategory category;
        private int cost;
        private int trionUse;
        private double trionSustain;
        private SlotType slotType;
        private Material mcItem;
        private int cooldown;
        private String description = "";

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder category(TriggerCategory category) {
            this.category = category;
            return this;
        }

        public Builder cost(int cost) {
            this.cost = cost;
            return this;
        }

        public Builder trionUse(int trionUse) {
            this.trionUse = trionUse;
            return this;
        }

        public Builder trionSustain(double trionSustain) {
            this.trionSustain = trionSustain;
            return this;
        }

        public Builder slotType(SlotType slotType) {
            this.slotType = slotType;
            return this;
        }

        public Builder mcItem(Material mcItem) {
            this.mcItem = mcItem;
            return this;
        }

        public Builder cooldown(int cooldown) {
            this.cooldown = cooldown;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public TriggerData build() {
            if (id == null || name == null || category == null || slotType == null || mcItem == null) {
                throw new IllegalStateException("Required fields must be set");
            }
            return new TriggerData(id, name, category, cost, trionUse, trionSustain, slotType, mcItem, cooldown, description);
        }
    }

    @Override
    public String toString() {
        return "TriggerData{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", category=" + category +
                ", cost=" + cost +
                ", trionUse=" + trionUse +
                ", trionSustain=" + trionSustain +
                ", slotType=" + slotType +
                ", mcItem=" + mcItem +
                ", cooldown=" + cooldown +
                ", description='" + description + '\'' +
                '}';
    }
}
