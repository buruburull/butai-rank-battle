package com.butai.rankbattle.manager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Tracks player disconnects during matches and applies time-based penalties.
 * - Tracks disconnect timestamps within the last 1 hour
 * - 2 disconnects: warning only
 * - 3 disconnects: 5 minute queue ban
 * - 4+ disconnects: 15 minute queue ban
 * - Practice matches are excluded from tracking
 */
public class DisconnectTracker {

    private static final long TRACKING_WINDOW_MS = 60 * 60 * 1000L; // 1 hour
    private static final long PENALTY_5MIN_MS = 5 * 60 * 1000L;
    private static final long PENALTY_15MIN_MS = 15 * 60 * 1000L;

    private final Logger logger;

    // Player UUID -> list of disconnect timestamps (within last hour)
    private final Map<UUID, List<Long>> disconnectHistory = new ConcurrentHashMap<>();
    // Player UUID -> penalty expiry timestamp
    private final Map<UUID, Long> penalties = new ConcurrentHashMap<>();

    public DisconnectTracker(Logger logger) {
        this.logger = logger;
    }

    /**
     * Record a disconnect for a player during a ranked match.
     * Returns the penalty message to show, or null if just tracked (no penalty yet).
     */
    public String recordDisconnect(UUID uuid) {
        long now = System.currentTimeMillis();

        // Clean old entries and add new one
        List<Long> history = disconnectHistory.computeIfAbsent(uuid, k -> new ArrayList<>());
        history.removeIf(ts -> now - ts > TRACKING_WINDOW_MS);
        history.add(now);

        int count = history.size();
        logger.info("Disconnect tracked for " + uuid.toString().substring(0, 8)
                + " (count in last hour: " + count + ")");

        if (count >= 4) {
            penalties.put(uuid, now + PENALTY_15MIN_MS);
            return "§c切断ペナルティ: §f15分間§cキュー参加禁止 §8(直近1時間で" + count + "回切断)";
        } else if (count >= 3) {
            penalties.put(uuid, now + PENALTY_5MIN_MS);
            return "§c切断ペナルティ: §f5分間§cキュー参加禁止 §8(直近1時間で" + count + "回切断)";
        } else if (count >= 2) {
            return "§e⚠ 切断警告: §7直近1時間で" + count + "回の切断が記録されています。§c3回目から参加禁止ペナルティが発生します。";
        }

        return null;
    }

    /**
     * Check if a player is currently penalized.
     * Returns remaining penalty message, or null if not penalized.
     */
    public String getPenaltyMessage(UUID uuid) {
        Long expiryTime = penalties.get(uuid);
        if (expiryTime == null) return null;

        long now = System.currentTimeMillis();
        if (now >= expiryTime) {
            penalties.remove(uuid);
            return null;
        }

        long remainingMs = expiryTime - now;
        int remainingSeconds = (int) (remainingMs / 1000);
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;

        return "§c切断ペナルティ中です。§7残り: §f" + minutes + "分" + seconds + "秒";
    }

    /**
     * Check if a player is penalized (boolean).
     */
    public boolean isPenalized(UUID uuid) {
        return getPenaltyMessage(uuid) != null;
    }

    /**
     * Get message to show on reconnect (penalty or warning).
     * Returns penalty message if penalized, warning if 2+ disconnects, or null.
     */
    public String getReconnectMessage(UUID uuid) {
        // Check active penalty first
        String penalty = getPenaltyMessage(uuid);
        if (penalty != null) return penalty;

        // Check for warning (2+ disconnects but no active penalty)
        int count = getDisconnectCount(uuid);
        if (count >= 2) {
            return "§e⚠ 切断警告: §7直近1時間で" + count + "回の切断が記録されています。§c3回目から参加禁止ペナルティが発生します。";
        }

        return null;
    }

    /**
     * Get disconnect count in the last hour.
     */
    public int getDisconnectCount(UUID uuid) {
        List<Long> history = disconnectHistory.get(uuid);
        if (history == null) return 0;

        long now = System.currentTimeMillis();
        history.removeIf(ts -> now - ts > TRACKING_WINDOW_MS);
        return history.size();
    }
}
