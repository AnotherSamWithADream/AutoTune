package com.autotune.optimizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Summary of setting changes for UI preview before applying.
 * Shows what will change, what the old and new values are, and why.
 */
public record SettingsChangeSummary(List<SettingChange> changes, int totalChanges,
                                    int improvedCount, int reducedCount, int unchangedCount) {

    public SettingsChangeSummary(List<SettingChange> changes, int totalChanges,
                                 int improvedCount, int reducedCount, int unchangedCount) {
        this.changes = List.copyOf(changes);
        this.totalChanges = totalChanges;
        this.improvedCount = improvedCount;
        this.reducedCount = reducedCount;
        this.unchangedCount = unchangedCount;
    }

    /**
     * Represents a single setting change.
     */
    public record SettingChange(
            String settingId,
            String name,
            String oldValue,
            String newValue,
            String reason
    ) {
        /**
         * Returns true if the setting value actually changed.
         */
        public boolean isChanged() {
            return !oldValue.equals(newValue);
        }
    }

    @Override
    public String toString() {
        return "SettingsChangeSummary{total=" + totalChanges
                + ", improved=" + improvedCount
                + ", reduced=" + reducedCount
                + ", unchanged=" + unchangedCount + "}";
    }

    /**
     * Builder for constructing a SettingsChangeSummary.
     */
    public static class Builder {
        private final List<SettingChange> changes = new ArrayList<>();
        private int improved = 0;
        private int reduced = 0;
        private int unchanged = 0;

        public Builder addChange(String settingId, String name, String oldValue,
                                  String newValue, String reason) {
            changes.add(new SettingChange(settingId, name, oldValue, newValue, reason));
            if (oldValue.equals(newValue)) {
                unchanged++;
            } else {
                // Heuristic: if reason contains "increase" or "enable" treat as improved,
                // if "decrease" or "reduce" or "disable" treat as reduced
                String lowerReason = reason.toLowerCase();
                if (lowerReason.contains("increas") || lowerReason.contains("enabl")
                        || lowerReason.contains("improv") || lowerReason.contains("higher")
                        || lowerReason.contains("boost")) {
                    improved++;
                } else {
                    reduced++;
                }
            }
            return this;
        }

        public Builder markImproved() { improved++; return this; }
        public Builder markReduced() { reduced++; return this; }
        public Builder markUnchanged() { unchanged++; return this; }

        public SettingsChangeSummary build() {
            // [CODE-REVIEW-FIX] M-010: totalChanges is now changes.size() for consistency
            // with the actual number of change entries, not just improved+reduced
            int total = changes.size();
            return new SettingsChangeSummary(changes, total, improved, reduced, unchanged);
        }
    }
}
