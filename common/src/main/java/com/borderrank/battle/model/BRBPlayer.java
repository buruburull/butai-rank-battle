package com.borderrank.battle.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a Border Rank Battle player with their stats and equipment.
 */
public class BRBPlayer {
    private final UUID uuid;
    private String name;
    private RankClass rankClass;
    private int trionCap;
    private int trionMax;
    private final Map<WeaponType, WeaponRP> weaponRPMap;

    /**
     * Constructs a BRBPlayer instance.
     *
     * @param uuid the player's UUID
     * @param name the player's name
     * @param rankClass the player's rank class
     */
    public BRBPlayer(UUID uuid, String name, RankClass rankClass) {
        this.uuid = uuid;
        this.name = name;
        this.rankClass = rankClass;
        this.trionCap = 15;
        this.trionMax = 1000;
        this.weaponRPMap = new HashMap<>();
        initializeWeapons();
    }

    /**
     * Constructs a BRBPlayer instance with custom trion values.
     *
     * @param uuid the player's UUID
     * @param name the player's name
     * @param rankClass the player's rank class
     * @param trionCap the trion cap
     * @param trionMax the maximum trion
     */
    public BRBPlayer(UUID uuid, String name, RankClass rankClass, int trionCap, int trionMax) {
        this.uuid = uuid;
        this.name = name;
        this.rankClass = rankClass;
        this.trionCap = trionCap;
        this.trionMax = trionMax;
        this.weaponRPMap = new HashMap<>();
        initializeWeapons();
    }

    /**
     * Initializes all weapon types with default RP values.
     */
    private void initializeWeapons() {
        for (WeaponType type : WeaponType.values()) {
            weaponRPMap.put(type, new WeaponRP(type));
        }
    }

    /**
     * Gets the player's UUID.
     *
     * @return the player's UUID
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the player's name.
     *
     * @return the player's name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the player's name.
     *
     * @param name the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the player's rank class.
     *
     * @return the player's rank class
     */
    public RankClass getRankClass() {
        return rankClass;
    }

    /**
     * Sets the player's rank class.
     *
     * @param rankClass the new rank class
     */
    public void setRankClass(RankClass rankClass) {
        this.rankClass = rankClass;
    }

    /**
     * Gets the player's trion cap.
     *
     * @return the trion cap
     */
    public int getTrionCap() {
        return trionCap;
    }

    /**
     * Sets the player's trion cap.
     *
     * @param trionCap the new trion cap
     */
    public void setTrionCap(int trionCap) {
        this.trionCap = trionCap;
    }

    /**
     * Gets the player's maximum trion.
     *
     * @return the maximum trion
     */
    public int getTrionMax() {
        return trionMax;
    }

    /**
     * Sets the player's maximum trion.
     *
     * @param trionMax the new maximum trion
     */
    public void setTrionMax(int trionMax) {
        this.trionMax = trionMax;
    }

    /**
     * Gets the weapon RP map for all weapon types.
     *
     * @return the weapon RP map
     */
    public Map<WeaponType, WeaponRP> getWeaponRPMap() {
        return weaponRPMap;
    }

    /**
     * Gets the weapon RP for a specific weapon type.
     *
     * @param type the weapon type
     * @return the weapon RP, or null if not found
     */
    public WeaponRP getWeaponRP(WeaponType type) {
        return weaponRPMap.get(type);
    }

    /**
     * Sets the weapon RP for a specific weapon type.
     *
     * @param type the weapon type
     * @param weaponRP the weapon RP data
     */
    public void setWeaponRP(WeaponType type, WeaponRP weaponRP) {
        weaponRPMap.put(type, weaponRP);
    }

    /**
     * Gets the highest RP value across all weapon types.
     *
     * @return the highest RP value
     */
    public int getHighestRP() {
        return weaponRPMap.values().stream()
                .mapToInt(WeaponRP::getRp)
                .max()
                .orElse(0);
    }

    /**
     * Gets the total wins across all weapon types.
     *
     * @return the total wins
     */
    public int getTotalWins() {
        return weaponRPMap.values().stream()
                .mapToInt(WeaponRP::getWins)
                .sum();
    }

    /**
     * Gets the total losses across all weapon types.
     *
     * @return the total losses
     */
    public int getTotalLosses() {
        return weaponRPMap.values().stream()
                .mapToInt(WeaponRP::getLosses)
                .sum();
    }

    @Override
    public String toString() {
        return "BRBPlayer{" +
                "uuid=" + uuid +
                ", name='" + name + '\'' +
                ", rankClass=" + rankClass +
                ", trionCap=" + trionCap +
                ", trionMax=" + trionMax +
                ", weaponRPMap=" + weaponRPMap +
                '}';
    }
}
