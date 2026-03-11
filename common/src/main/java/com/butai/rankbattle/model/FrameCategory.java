package com.butai.rankbattle.model;

public enum FrameCategory {
    STRIKER("ストライカー", "§c"),
    GUNNER("ガナー", "§e"),
    MARKSMAN("マークスマン", "§b"),
    SUPPORT("サポート", "§a");

    private final String displayName;
    private final String color;

    FrameCategory(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public String getColoredName() {
        return color + displayName;
    }

    /**
     * Whether this category is a weapon type (affects RP).
     */
    public boolean isWeaponType() {
        return this == STRIKER || this == GUNNER || this == MARKSMAN;
    }

    /**
     * Convert to WeaponType. Returns null for SUPPORT.
     */
    public WeaponType toWeaponType() {
        return switch (this) {
            case STRIKER -> WeaponType.STRIKER;
            case GUNNER -> WeaponType.GUNNER;
            case MARKSMAN -> WeaponType.MARKSMAN;
            case SUPPORT -> null;
        };
    }

    public static FrameCategory fromString(String name) {
        if (name == null) return null;
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
