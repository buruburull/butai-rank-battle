package com.borderrank.battle.model;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.md_5.bungee.api.ChatColor;

/**
 * Represents player rank classes with display names and colors.
 */
public enum RankClass {
    UNRANKED("未所属", ChatColor.WHITE, NamedTextColor.WHITE),
    C("C級", ChatColor.GRAY, NamedTextColor.GRAY),
    B("B級", ChatColor.BLUE, NamedTextColor.BLUE),
    A("A級", ChatColor.GOLD, NamedTextColor.GOLD),
    S("S級", ChatColor.LIGHT_PURPLE, NamedTextColor.LIGHT_PURPLE);

    private final String displayName;
    private final ChatColor color;
    private final TextColor adventureColor;

    RankClass(String displayName, ChatColor color, TextColor adventureColor) {
        this.displayName = displayName;
        this.color = color;
        this.adventureColor = adventureColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ChatColor getColor() {
        return color;
    }

    public TextColor getAdventureColor() {
        return adventureColor;
    }
}
