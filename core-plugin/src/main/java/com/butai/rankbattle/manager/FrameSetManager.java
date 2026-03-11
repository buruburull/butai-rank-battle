package com.butai.rankbattle.manager;

import com.butai.rankbattle.database.FrameSetDAO;
import com.butai.rankbattle.model.FrameCategory;
import com.butai.rankbattle.model.FrameData;
import com.butai.rankbattle.model.WeaponType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages player framesets (8-slot equipment configuration) in memory.
 * Handles validation, persistence via FrameSetDAO, and queue readiness checks.
 */
public class FrameSetManager {

    private static final int SLOT_COUNT = 8;
    private static final String CURRENT_PRESET = "_current";

    private final FrameRegistry frameRegistry;
    private final FrameSetDAO frameSetDAO;
    private final Logger logger;

    // In-memory cache: UUID -> String[8] (frame IDs, null for empty slots)
    private final Map<UUID, String[]> playerFrameSets = new ConcurrentHashMap<>();

    public FrameSetManager(FrameRegistry frameRegistry, FrameSetDAO frameSetDAO, Logger logger) {
        this.frameRegistry = frameRegistry;
        this.frameSetDAO = frameSetDAO;
        this.logger = logger;
    }

    /**
     * Load a player's current frameset from DB into memory.
     * Called on player join.
     */
    public void loadPlayer(UUID uuid) {
        String[] slots = frameSetDAO.loadFrameSet(uuid, CURRENT_PRESET);
        if (slots == null) {
            slots = new String[SLOT_COUNT];
        }
        playerFrameSets.put(uuid, slots);
    }

    /**
     * Save a player's current frameset to DB.
     */
    public void savePlayer(UUID uuid) {
        String[] slots = playerFrameSets.get(uuid);
        if (slots != null) {
            frameSetDAO.saveFrameSet(uuid, CURRENT_PRESET, slots);
        }
    }

    /**
     * Unload player from memory (save first).
     * Called on player quit.
     */
    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        playerFrameSets.remove(uuid);
    }

    /**
     * Get the current frameset for a player.
     * Returns a copy of the String[8] array.
     */
    public String[] getFrameSet(UUID uuid) {
        String[] slots = playerFrameSets.get(uuid);
        if (slots == null) {
            slots = new String[SLOT_COUNT];
            playerFrameSets.put(uuid, slots);
        }
        return Arrays.copyOf(slots, SLOT_COUNT);
    }

    /**
     * Get the FrameData at a specific slot, or null if empty/invalid.
     */
    public FrameData getFrameAt(UUID uuid, int slot) {
        String[] slots = playerFrameSets.get(uuid);
        if (slots == null || slot < 1 || slot > SLOT_COUNT) return null;
        String frameId = slots[slot - 1];
        if (frameId == null) return null;
        return frameRegistry.getFrame(frameId);
    }

    /**
     * Set a frame in a specific slot. Returns null on success, or an error message.
     */
    public String setFrame(UUID uuid, int slot, String frameId) {
        if (slot < 1 || slot > SLOT_COUNT) {
            return "スロット番号は1〜8で指定してください。";
        }

        FrameData frameData = frameRegistry.getFrame(frameId);
        if (frameData == null) {
            return "フレーム '" + frameId + "' が見つかりません。/frame list で確認してください。";
        }

        // Slot 1 must be a weapon frame
        if (slot == 1 && !frameData.isWeaponFrame()) {
            return "スロット1には武器系フレーム（STRIKER/GUNNER/MARKSMAN）のみ装備できます。";
        }

        String[] slots = playerFrameSets.computeIfAbsent(uuid, k -> new String[SLOT_COUNT]);

        // Duplicate check: same frame in another slot
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i == slot - 1) continue;
            if (frameId.equals(slots[i])) {
                return "フレーム '" + frameData.getName() + "' は既にスロット" + (i + 1) + "に装備されています。同一フレームの重複装備はできません。";
            }
        }

        slots[slot - 1] = frameId;
        return null; // success
    }

    /**
     * Remove a frame from a specific slot. Returns null on success, or an error message.
     */
    public String removeFrame(UUID uuid, int slot) {
        if (slot < 1 || slot > SLOT_COUNT) {
            return "スロット番号は1〜8で指定してください。";
        }

        String[] slots = playerFrameSets.get(uuid);
        if (slots == null || slots[slot - 1] == null) {
            return "スロット" + slot + "にはフレームが装備されていません。";
        }

        if (slot == 1) {
            return "スロット1の武器フレームは削除できません。別の武器フレームで上書きしてください。";
        }

        slots[slot - 1] = null;
        return null; // success
    }

    /**
     * Validate the frameset for queue entry.
     * Returns null if valid, or an error message.
     */
    public String validateForQueue(UUID uuid) {
        String[] slots = playerFrameSets.get(uuid);
        if (slots == null || slots[0] == null) {
            return "スロット1に武器フレームを装備してください。(/frame set 1 <フレーム名>)";
        }

        FrameData slot1Frame = frameRegistry.getFrame(slots[0]);
        if (slot1Frame == null) {
            return "スロット1のフレームが無効です。フレームを再設定してください。";
        }
        if (!slot1Frame.isWeaponFrame()) {
            return "スロット1には武器系フレーム（STRIKER/GUNNER/MARKSMAN）が必要です。";
        }

        // Duplicate check
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i] != null) {
                if (!seen.add(slots[i])) {
                    return "フレーム '" + slots[i] + "' が重複しています。重複を解除してください。";
                }
            }
        }

        return null; // valid
    }

    /**
     * Get the weapon type determined by slot 1.
     * Returns null if slot 1 is empty or invalid.
     */
    public WeaponType getWeaponType(UUID uuid) {
        String[] slots = playerFrameSets.get(uuid);
        if (slots == null || slots[0] == null) return null;
        FrameData frameData = frameRegistry.getFrame(slots[0]);
        if (frameData == null) return null;
        return frameData.getCategory().toWeaponType();
    }

    /**
     * Get all equipped FrameData for a player (non-null slots).
     */
    public List<FrameData> getEquippedFrames(UUID uuid) {
        List<FrameData> frames = new ArrayList<>();
        String[] slots = playerFrameSets.get(uuid);
        if (slots == null) return frames;
        for (String frameId : slots) {
            if (frameId != null) {
                FrameData data = frameRegistry.getFrame(frameId);
                if (data != null) {
                    frames.add(data);
                }
            }
        }
        return frames;
    }

    // ========== Preset Management ==========

    /**
     * Save current frameset as a named preset.
     */
    public String savePreset(UUID uuid, String presetName) {
        if (presetName == null || presetName.isBlank() || presetName.startsWith("_")) {
            return "無効なプリセット名です。";
        }
        String[] slots = playerFrameSets.get(uuid);
        if (slots == null) {
            return "フレームセットが未設定です。";
        }
        frameSetDAO.saveFrameSet(uuid, presetName, slots);
        return null; // success
    }

    /**
     * Load a named preset into the current frameset.
     */
    public String loadPreset(UUID uuid, String presetName) {
        if (presetName == null || presetName.isBlank() || presetName.startsWith("_")) {
            return "無効なプリセット名です。";
        }
        String[] slots = frameSetDAO.loadFrameSet(uuid, presetName);
        if (slots == null) {
            return "プリセット '" + presetName + "' が見つかりません。";
        }
        playerFrameSets.put(uuid, slots);
        return null; // success
    }

    /**
     * List all preset names for a player (excluding internal "_current").
     */
    public List<String> listPresets(UUID uuid) {
        List<String> all = frameSetDAO.listFrameSets(uuid);
        all.removeIf(name -> name.startsWith("_"));
        return all;
    }

    /**
     * Delete a named preset.
     */
    public String deletePreset(UUID uuid, String presetName) {
        if (presetName == null || presetName.isBlank() || presetName.startsWith("_")) {
            return "無効なプリセット名です。";
        }
        boolean deleted = frameSetDAO.deleteFrameSet(uuid, presetName);
        if (!deleted) {
            return "プリセット '" + presetName + "' が見つかりません。";
        }
        return null; // success
    }
}
