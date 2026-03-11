package com.butai.rankbattle.model;

public class FrameData {

    private final String id;
    private final String name;
    private final FrameCategory category;
    private final String mcItem;
    private final int damage;
    private final double damageMultiplier;
    private final int etherUse;
    private final int etherSustain;
    private final int cooldown;
    private final String description;

    public FrameData(String id, String name, FrameCategory category, String mcItem,
                     int damage, double damageMultiplier, int etherUse, int etherSustain,
                     int cooldown, String description) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.mcItem = mcItem;
        this.damage = damage;
        this.damageMultiplier = damageMultiplier;
        this.etherUse = etherUse;
        this.etherSustain = etherSustain;
        this.cooldown = cooldown;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public FrameCategory getCategory() {
        return category;
    }

    public String getMcItem() {
        return mcItem;
    }

    public int getDamage() {
        return damage;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public int getEtherUse() {
        return etherUse;
    }

    public int getEtherSustain() {
        return etherSustain;
    }

    public int getCooldown() {
        return cooldown;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSustain() {
        return etherSustain > 0;
    }

    public boolean isWeaponFrame() {
        return category.isWeaponType();
    }
}
