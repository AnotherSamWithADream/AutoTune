package com.autotune.optimizer;

import java.util.HashMap;
import java.util.Map;

/**
 * Encodes known interactions between game settings that affect performance
 * in non-linear ways. When two settings are adjusted together, the performance
 * impact may be more or less than the sum of their individual impacts.
 *
 * A multiplier > 1.0 means the combined cost is higher than expected (synergistic penalty).
 * A multiplier < 1.0 means the combination is less costly than expected (diminishing cost).
 * A multiplier of 1.0 means no interaction.
 */
public class InteractionMatrix {

    /**
     * Key for an interaction pair. Order-independent.
     */
    private record PairKey(String a, String b) {
        PairKey {
            if (a.compareTo(b) > 0) {
                String tmp = a;
                a = b;
                b = tmp;
            }
        }
    }

    /**
     * Stores multipliers for setting pair interactions keyed by value ranges.
     */
    private record InteractionEntry(double baseMultiplier, ValueCondition condition1,
                                       ValueCondition condition2) {
    }

    @FunctionalInterface
    private interface ValueCondition {
        boolean test(Object value);
    }

    private final Map<PairKey, InteractionEntry> interactions = new HashMap<>();

    public InteractionMatrix() {
        registerKnownInteractions();
    }

    private void registerKnownInteractions() {
        // Render distance x Graphics mode: high RD + FANCY = superlinear cost
        register("vanilla.render_distance", "vanilla.graphics_mode",
                1.35, v -> toInt(v) >= 16, v -> toInt(v) >= 2);

        // Render distance x Simulation distance: both high = heavy chunk load
        register("vanilla.render_distance", "vanilla.simulation_distance",
                1.25, v -> toInt(v) >= 16, v -> toInt(v) >= 12);

        // Render distance x Biome blend: large blend at high RD = expensive
        register("vanilla.render_distance", "vanilla.biome_blend",
                1.20, v -> toInt(v) >= 12, v -> toInt(v) >= 5);

        // Graphics mode x Entity shadows: FANCY + shadows = moderate interaction
        register("vanilla.graphics_mode", "vanilla.entity_shadows",
                1.15, v -> toInt(v) >= 2, InteractionMatrix::toBool);

        // Graphics mode x Smooth lighting: FANCY + smooth lighting synergy
        register("vanilla.graphics_mode", "vanilla.smooth_lighting",
                1.10, v -> toInt(v) >= 2, InteractionMatrix::toBool);

        // Graphics mode x Particles: FANCY + ALL particles = heavy
        register("vanilla.graphics_mode", "vanilla.particles",
                1.20, v -> toInt(v) >= 2, v -> toInt(v) == 0);

        // Graphics mode x Cloud render mode: FANCY + FANCY clouds
        register("vanilla.graphics_mode", "vanilla.cloud_render_mode",
                1.15, v -> toInt(v) >= 2, v -> toInt(v) >= 2);

        // Entity distance x Entity shadows: far entities + shadows
        register("vanilla.entity_distance_scaling", "vanilla.entity_shadows",
                1.10, v -> toFloat(v) >= 1.0f, InteractionMatrix::toBool);

        // Render distance x Clouds: high RD + fancy clouds = more cloud rendering
        register("vanilla.render_distance", "vanilla.cloud_render_mode",
                1.10, v -> toInt(v) >= 16, v -> toInt(v) >= 2);

        // Mipmap x Render distance: high mipmap at high RD = VRAM heavy
        register("vanilla.mipmap_levels", "vanilla.render_distance",
                1.08, v -> toInt(v) >= 3, v -> toInt(v) >= 16);

        // Sodium chunk update threads x render distance
        register("sodium.chunk_update_threads", "vanilla.render_distance",
                0.90, v -> toInt(v) >= 3, v -> toInt(v) >= 16);

        // Sodium use block face culling reduces cost of high RD
        register("sodium.use_block_face_culling", "vanilla.render_distance",
                0.85, InteractionMatrix::toBool, v -> toInt(v) >= 12);

        // Sodium entity culling reduces entity draw cost at high entity distance
        register("sodium.use_entity_culling", "vanilla.entity_distance_scaling",
                0.80, InteractionMatrix::toBool, v -> toFloat(v) >= 1.0f);

        // Iris shaders x Graphics mode: shaders override much of graphics mode impact
        register("iris.shaders_enabled", "vanilla.graphics_mode",
                0.60, InteractionMatrix::toBool, v -> toInt(v) >= 1);

        // Iris shaders x Render distance: shaders + high RD = very expensive
        register("iris.shaders_enabled", "vanilla.render_distance",
                1.50, InteractionMatrix::toBool, v -> toInt(v) >= 16);

        // Iris shaders x Smooth lighting: shaders override smooth lighting
        register("iris.shaders_enabled", "vanilla.smooth_lighting",
                0.70, InteractionMatrix::toBool, InteractionMatrix::toBool);

        // Iris shaders x Entity shadows: shaders have own shadow system
        register("iris.shaders_enabled", "vanilla.entity_shadows",
                0.50, InteractionMatrix::toBool, InteractionMatrix::toBool);

        // Sodium animations x Render distance: animations at high RD
        register("sodium.animation.water", "vanilla.render_distance",
                1.05, InteractionMatrix::toBool, v -> toInt(v) >= 16);

        register("sodium.animation.lava", "vanilla.render_distance",
                1.05, InteractionMatrix::toBool, v -> toInt(v) >= 16);
    }

    private void register(String setting1, String setting2, double multiplier,
                           ValueCondition cond1, ValueCondition cond2) {
        interactions.put(new PairKey(setting1, setting2),
                new InteractionEntry(multiplier, cond1, cond2));
    }

    /**
     * Returns the interaction multiplier for two settings at given values.
     * Returns 1.0 if no known interaction exists or conditions are not met.
     *
     * @param setting1 first setting ID
     * @param setting2 second setting ID
     * @param value1   current value of setting1
     * @param value2   current value of setting2
     * @return the interaction multiplier
     */
    public double getMultiplier(String setting1, String setting2, Object value1, Object value2) {
        PairKey key = new PairKey(setting1, setting2);
        InteractionEntry entry = interactions.get(key);
        if (entry == null) return 1.0;

        // Determine which value corresponds to which condition.
        // PairKey normalizes order, so we need to check both orientations.
        boolean match;
        if (setting1.compareTo(setting2) <= 0) {
            match = safeTest(entry.condition1, value1) && safeTest(entry.condition2, value2);
        } else {
            match = safeTest(entry.condition1, value2) && safeTest(entry.condition2, value1);
        }

        return match ? entry.baseMultiplier : 1.0;
    }

    /**
     * Returns all known interaction pairs involving a given setting.
     */
    public Map<String, Double> getInteractionsFor(String settingId, Map<String, Object> currentValues) {
        Map<String, Double> result = new HashMap<>();
        for (Map.Entry<PairKey, InteractionEntry> e : interactions.entrySet()) {
            PairKey pair = e.getKey();
            String other = null;
            if (pair.a().equals(settingId)) other = pair.b();
            else if (pair.b().equals(settingId)) other = pair.a();

            if (other != null && currentValues.containsKey(settingId) && currentValues.containsKey(other)) {
                double mult = getMultiplier(settingId, other,
                        currentValues.get(settingId), currentValues.get(other));
                if (Math.abs(mult - 1.0) > 0.001) {
                    result.put(other, mult);
                }
            }
        }
        return result;
    }

    private static boolean safeTest(ValueCondition cond, Object value) {
        try {
            return cond.test(value);
        } catch (Exception e) {
            return false;
        }
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    private static float toFloat(Object v) {
        if (v instanceof Number n) return n.floatValue();
        try { return Float.parseFloat(String.valueOf(v)); } catch (Exception e) { return 0f; }
    }

    private static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return Boolean.parseBoolean(String.valueOf(v));
    }
}
