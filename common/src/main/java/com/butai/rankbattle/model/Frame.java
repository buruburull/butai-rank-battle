package com.butai.rankbattle.model;

/**
 * Represents an equipped frame instance for a player.
 */
public class Frame {

    private final FrameData data;
    private final int slot;

    public Frame(FrameData data, int slot) {
        this.data = data;
        this.slot = slot;
    }

    public FrameData getData() {
        return data;
    }

    public int getSlot() {
        return slot;
    }

    public String getId() {
        return data.getId();
    }

    public String getName() {
        return data.getName();
    }

    public FrameCategory getCategory() {
        return data.getCategory();
    }
}
