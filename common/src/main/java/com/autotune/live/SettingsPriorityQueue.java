package com.autotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ordered priority queue for live setting adjustments.
 * Lower priority index = adjusted first (least visual disruption).
 */
public class SettingsPriorityQueue {

    private final List<AdjustableEntry> downgradeQueue = new ArrayList<>();
    private final List<AdjustableEntry> emergencyQueue = new ArrayList<>();
    private final List<AdjustableEntry> upgradeQueue = new ArrayList<>();

    public SettingsPriorityQueue() {
        initializeDowngradeQueue();
        initializeEmergencyQueue();
        initializeUpgradeQueue();
    }

    private void initializeDowngradeQueue() {
        // Ordered: least noticeable first
        downgradeQueue.add(new AdjustableEntry("vanilla.biome_blend", "Biome Blend", AdjustType.STEP_DOWN, 1));
        downgradeQueue.add(new AdjustableEntry("vanilla.entity_distance_scaling", "Entity Distance", AdjustType.REDUCE_10_PERCENT, 2));
        downgradeQueue.add(new AdjustableEntry("vanilla.mipmap_levels", "Mipmap Levels", AdjustType.STEP_DOWN, 3));
        downgradeQueue.add(new AdjustableEntry("vanilla.particles", "Particles", AdjustType.STEP_DOWN, 4));
        downgradeQueue.add(new AdjustableEntry("sodium.animation.weather", "Weather Animation", AdjustType.DISABLE, 5));
        downgradeQueue.add(new AdjustableEntry("sodium.animation.portal", "Portal Animation", AdjustType.DISABLE, 6));
        downgradeQueue.add(new AdjustableEntry("vanilla.cloud_render_mode", "Clouds", AdjustType.STEP_DOWN, 7));
        downgradeQueue.add(new AdjustableEntry("vanilla.entity_shadows", "Entity Shadows", AdjustType.DISABLE, 8));
        downgradeQueue.add(new AdjustableEntry("vanilla.smooth_lighting", "Smooth Lighting", AdjustType.DISABLE, 9));
        downgradeQueue.add(new AdjustableEntry("vanilla.render_distance", "Render Distance", AdjustType.STEP_DOWN, 10));
    }

    private void initializeEmergencyQueue() {
        emergencyQueue.add(new AdjustableEntry("vanilla.render_distance", "Render Distance", AdjustType.DROP_4, 11));
        emergencyQueue.add(new AdjustableEntry("vanilla.graphics_mode", "Graphics Mode", AdjustType.STEP_DOWN, 12));
        emergencyQueue.add(new AdjustableEntry("iris.shaders_enabled", "Shaders", AdjustType.DISABLE, 13));
    }

    private void initializeUpgradeQueue() {
        // Reverse of downgrade: re-enable most impactful visual settings first
        List<AdjustableEntry> reversed = new ArrayList<>(downgradeQueue);
        Collections.reverse(reversed);
        for (int i = 0; i < reversed.size(); i++) {
            AdjustableEntry orig = reversed.get(i);
            upgradeQueue.add(new AdjustableEntry(
                    orig.settingId(), orig.displayName(), orig.type().opposite(), i + 1));
        }
    }

    public List<AdjustableEntry> getDowngradeQueue() {
        return Collections.unmodifiableList(downgradeQueue);
    }

    public List<AdjustableEntry> getEmergencyQueue() {
        return Collections.unmodifiableList(emergencyQueue);
    }

    public List<AdjustableEntry> getUpgradeQueue() {
        return Collections.unmodifiableList(upgradeQueue);
    }

    public record AdjustableEntry(String settingId, String displayName, AdjustType type, int priority) {}

    public enum AdjustType {
        STEP_DOWN,
        STEP_UP,
        REDUCE_10_PERCENT,
        INCREASE_10_PERCENT,
        DISABLE,
        ENABLE,
        DROP_4;

        public AdjustType opposite() {
            return switch (this) {
                case STEP_DOWN, DROP_4 -> STEP_UP;
                case STEP_UP -> STEP_DOWN;
                case REDUCE_10_PERCENT -> INCREASE_10_PERCENT;
                case INCREASE_10_PERCENT -> REDUCE_10_PERCENT;
                case DISABLE -> ENABLE;
                case ENABLE -> DISABLE;
            };
        }
    }
}
