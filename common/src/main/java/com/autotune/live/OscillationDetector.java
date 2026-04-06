package com.autotune.live;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects when settings oscillate (toggled up then down or vice versa within a short window).
 * Locks oscillating settings for a configurable duration.
 */
public class OscillationDetector {

    // [CODE-REVIEW-FIX] Use ConcurrentHashMap for thread-safe access from tick + UI threads
    private final Map<String, AdjustmentRecord> recentAdjustments = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedSettings = new ConcurrentHashMap<>();
    private final long lockDurationMs;

    public OscillationDetector(long lockDurationMs) {
        this.lockDurationMs = lockDurationMs;
    }

    public void recordAdjustment(String settingId, AdjustmentDirection direction) {
        long now = System.currentTimeMillis();
        AdjustmentRecord previous = recentAdjustments.get(settingId);

        if (previous != null && previous.direction != direction
                && (now - previous.timestamp) < 30_000) {
            // Oscillation detected: setting was adjusted in opposite direction within 30s
            lockedSettings.put(settingId, now + lockDurationMs);
            recentAdjustments.remove(settingId);
        } else {
            recentAdjustments.put(settingId, new AdjustmentRecord(direction, now));
        }
    }

    public boolean isLocked(String settingId) {
        Long unlockTime = lockedSettings.get(settingId);
        if (unlockTime == null) return false;
        if (System.currentTimeMillis() >= unlockTime) {
            lockedSettings.remove(settingId);
            return false;
        }
        return true;
    }

    public long getRemainingLockMs(String settingId) {
        Long unlockTime = lockedSettings.get(settingId);
        if (unlockTime == null) return 0;
        return Math.max(0, unlockTime - System.currentTimeMillis());
    }

    public void clearAll() {
        recentAdjustments.clear();
        lockedSettings.clear();
    }

    public enum AdjustmentDirection {
        UP, DOWN
    }

    private record AdjustmentRecord(AdjustmentDirection direction, long timestamp) {}
}
