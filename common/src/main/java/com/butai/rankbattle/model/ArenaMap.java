package com.butai.rankbattle.model;

/**
 * Represents an arena map configuration.
 */
public class ArenaMap {

    private final String id;
    private final String name;
    private final String worldName;
    private final double spawn1X, spawn1Y, spawn1Z;
    private final double spawn2X, spawn2Y, spawn2Z;
    private final double spectateX, spectateY, spectateZ;
    private final int borderRadius;
    private final String description;

    public ArenaMap(String id, String name, String worldName,
                    double spawn1X, double spawn1Y, double spawn1Z,
                    double spawn2X, double spawn2Y, double spawn2Z,
                    double spectateX, double spectateY, double spectateZ,
                    int borderRadius, String description) {
        this.id = id;
        this.name = name;
        this.worldName = worldName;
        this.spawn1X = spawn1X;
        this.spawn1Y = spawn1Y;
        this.spawn1Z = spawn1Z;
        this.spawn2X = spawn2X;
        this.spawn2Y = spawn2Y;
        this.spawn2Z = spawn2Z;
        this.spectateX = spectateX;
        this.spectateY = spectateY;
        this.spectateZ = spectateZ;
        this.borderRadius = borderRadius;
        this.description = description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public double getSpawn1X() { return spawn1X; }
    public double getSpawn1Y() { return spawn1Y; }
    public double getSpawn1Z() { return spawn1Z; }
    public double getSpawn2X() { return spawn2X; }
    public double getSpawn2Y() { return spawn2Y; }
    public double getSpawn2Z() { return spawn2Z; }
    public double getSpectateX() { return spectateX; }
    public double getSpectateY() { return spectateY; }
    public double getSpectateZ() { return spectateZ; }
    public int getBorderRadius() { return borderRadius; }
    public String getDescription() { return description; }
}
