package com.autotune.live;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Records all live adjustments made during a session for display in the UI.
 */
public class AdjustmentHistory {

    // [CODE-REVIEW-FIX] Use ArrayDeque instead of ArrayList for O(1) removal from head
    private final Deque<AdjustmentEntry> entries = new ArrayDeque<>();
    private final int maxEntries;
    private int totalCount; // [CODE-REVIEW-FIX] Track total across evictions

    public AdjustmentHistory(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public void record(String settingId, String settingName, String oldValue, String newValue,
                       String reason, AdaptiveState stateDuringAdjustment) {
        AdjustmentEntry entry = new AdjustmentEntry(
                System.currentTimeMillis(),
                settingId,
                settingName,
                oldValue,
                newValue,
                reason,
                stateDuringAdjustment
        );
        entries.addLast(entry);
        totalCount++;
        if (entries.size() > maxEntries) {
            entries.removeFirst(); // [CODE-REVIEW-FIX] O(1) instead of O(n) ArrayList.remove(0)
        }
    }

    public List<AdjustmentEntry> getEntries() {
        return List.copyOf(entries);
    }

    public int getTotalAdjustments() {
        return totalCount;
    }

    public AdjustmentEntry getLastAdjustment() {
        return entries.isEmpty() ? null : entries.peekLast();
    }

    public void clear() {
        entries.clear();
        totalCount = 0;
    }

    public record AdjustmentEntry(
            long timestamp,
            String settingId,
            String settingName,
            String oldValue,
            String newValue,
            String reason,
            AdaptiveState state
    ) {
        public String getDirectionSymbol() {
            // Try numeric comparison
            try {
                double oldNum = Double.parseDouble(oldValue);
                double newNum = Double.parseDouble(newValue);
                return newNum > oldNum ? "\u2191" : "\u2193";
            } catch (NumberFormatException e) {
                return "\u21C4";
            }
        }

        public String toDisplayString() {
            return String.format("%s %s: %s \u2192 %s", getDirectionSymbol(), settingName, oldValue, newValue);
        }
    }
}
