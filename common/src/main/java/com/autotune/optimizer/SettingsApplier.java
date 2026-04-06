package com.autotune.optimizer;

import com.autotune.platform.PlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Applies computed OptimalSettings to the game by invoking each setting's
 * applier function through the registry. Also builds a change summary for
 * UI preview before applying.
 */
public class SettingsApplier {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoTune/SettingsApplier");

    /**
     * Applies all optimal settings to the game.
     *
     * @param settings the computed optimal settings
     * @param registry the settings registry containing applier functions
     * @param adapter  the platform adapter for chunk/renderer operations
     */
    public void apply(OptimalSettings settings, SettingsRegistry registry, PlatformAdapter adapter) {
        int applied = 0;
        int failed = 0;
        boolean needsChunkReload = false;

        for (var entry : settings.getValues().entrySet()) {
            String settingId = entry.getKey();
            Object value = entry.getValue();

            SettingDefinition<?> def = registry.get(settingId);
            if (def == null) {
                LOGGER.warn("Unknown setting ID during apply: {}", settingId);
                failed++;
                continue;
            }

            try {
                Object coerced = coerceForApply(value, def);
                applyValue(def, coerced);
                applied++;

                // [CODE-REVIEW-FIX] Hardcoded chunk-reload triggers. Future: add requiresChunkReload field to SettingDefinition.
                if (settingId.equals("vanilla.render_distance")
                        || settingId.equals("vanilla.simulation_distance")
                        || settingId.equals("vanilla.biome_blend")
                        || settingId.equals("vanilla.mipmap_levels")
                        || settingId.equals("vanilla.graphics_mode")) {
                    needsChunkReload = true;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to apply setting {}: {}", settingId, e.toString());
                failed++;
            }
        }

        // Reload chunks if any chunk-affecting settings changed
        if (needsChunkReload) {
            try {
                adapter.reloadChunks();
                LOGGER.debug("Chunks reloaded after settings apply");
            } catch (Exception e) {
                LOGGER.error("Failed to reload chunks: {}", e.toString());
            }
        }

        LOGGER.info("Settings applied: {} successful, {} failed", applied, failed);
    }

    /**
     * Builds a summary of all changes that would occur when applying optimal settings.
     * Does NOT actually apply the changes. Used for UI preview.
     *
     * @param settings the computed optimal settings
     * @param registry the settings registry containing current value readers
     * @return a summary of all changes
     */
    public SettingsChangeSummary buildChangeSummary(OptimalSettings settings, SettingsRegistry registry) {
        SettingsChangeSummary.Builder builder = new SettingsChangeSummary.Builder();

        Collection<SettingDefinition<?>> allDefs = registry.getAll();
        int totalSettings = 0;

        for (SettingDefinition<?> def : allDefs) {
            String settingId = def.id();
            Object newValue = settings.getValue(settingId);

            if (newValue == null) continue;
            totalSettings++;

            Object currentValue;
            try {
                currentValue = def.reader().get();
            } catch (Exception e) {
                currentValue = def.defaultValue();
            }

            String currentStr = formatValue(currentValue, def);
            String newStr = formatValue(newValue, def);
            String explanation = settings.getExplanation(settingId);
            if (explanation == null) explanation = "Optimized based on benchmark results";

            // Determine direction of change for classification
            String reason;
            int comparison = compareValues(currentValue, newValue, def);
            if (comparison == 0) {
                reason = "No change needed - already at optimal value";
                builder.addChange(settingId, def.displayName(), currentStr, newStr, reason);
            } else if (comparison > 0) {
                // New value is higher (quality increase)
                reason = "Increased quality: " + explanation;
                builder.addChange(settingId, def.displayName(), currentStr, newStr, reason);
            } else {
                // New value is lower (quality decrease for performance)
                reason = "Reduced for performance: " + explanation;
                builder.addChange(settingId, def.displayName(), currentStr, newStr, reason);
            }
        }

        return builder.build();
    }

    // Unchecked/rawtypes: raw SettingDefinition used to pass Object value to Consumer<T>.accept(); type safety ensured by coerceForApply
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void applyValue(SettingDefinition def, Object value) {
        def.applier().accept(value);
    }

    /**
     * Coerces the value to the correct type for the setting's applier.
     */
    private Object coerceForApply(Object value, SettingDefinition<?> def) {
        if (value == null) return def.defaultValue();

        Class<?> type = def.type();

        if (type == Integer.class) {
            if (value instanceof Number n) return n.intValue();
            try { return Integer.parseInt(String.valueOf(value)); }
            catch (NumberFormatException e) { return def.defaultValue(); }
        }

        if (type == Float.class) {
            if (value instanceof Number n) return n.floatValue();
            try { return Float.parseFloat(String.valueOf(value)); }
            catch (NumberFormatException e) { return def.defaultValue(); }
        }

        if (type == Boolean.class) {
            if (value instanceof Boolean) return value;
            if (value instanceof Number n) return n.doubleValue() >= 0.5;
            return Boolean.parseBoolean(String.valueOf(value));
        }

        return value;
    }

    // [CODE-REVIEW-FIX] Hardcoded formatting for known settings. Future: move to SettingDefinition.formatter field.
    /**
     * Formats a setting value for human-readable display.
     */
    private String formatValue(Object value, SettingDefinition<?> def) {
        if (value == null) return "N/A";

        String settingId = def.id();

        // Special formatting for known settings
        switch (value) {
            case Boolean b -> {
                return b ? "On" : "Off";
            }
            case Float f -> {
                if (settingId.contains("volume") || settingId.contains("opacity")
                        || settingId.contains("gamma") || settingId.contains("scale")) {
                    return String.format("%.0f%%", f * 100);
                }
                if (settingId.contains("entity_distance")) {
                    return String.format("%.1fx", f);
                }
                return String.format("%.1f", f);
            }
            case Integer i -> {
            // Named discrete values
            switch (settingId) {
                case "vanilla.graphics_mode" -> {
                    return switch (i) {
                        case 1 -> "Fast";
                        case 3 -> "Fabulous";
                        default -> "Fancy";
                    };
                }
                case "vanilla.particles" -> {
                    return switch (i) {
                        case 0 -> "All";
                        case 1 -> "Decreased";
                        default -> "Minimal";
                    };
                }
                case "vanilla.cloud_render_mode" -> {
                    return switch (i) {
                        case 0 -> "Off";
                        case 1 -> "Fast";
                        default -> "Fancy";
                    };
                }
                case "vanilla.attack_indicator" -> {
                    return switch (i) {
                        case 0 -> "Off";
                        case 1 -> "Crosshair";
                        default -> "Hotbar";
                    };
                }
                case "vanilla.narrator" -> {
                    return switch (i) {
                        case 0 -> "Off";
                        case 1 -> "All";
                        case 2 -> "Chat";
                        default -> "System";
                    };
                }
                case "vanilla.gui_scale" -> {
                    return i == 0 ? "Auto" : String.valueOf(i);
                }
            }
            if (settingId.contains("render_distance") || settingId.contains("simulation_distance")) {
                return i + " chunks";
            }
            }
            default -> {
            }
        }

        return String.valueOf(value);
    }

    /**
     * Compares two setting values.
     * Returns > 0 if newValue represents higher quality,
     * < 0 if newValue represents lower quality, 0 if equal.
     */
    private int compareValues(Object current, Object newValue, SettingDefinition<?> def) {
        if (current == null || newValue == null) return 0;
        if (current.equals(newValue)) return 0;

        if (current instanceof Number curN && newValue instanceof Number newN) {
            return Double.compare(newN.doubleValue(), curN.doubleValue());
        }

        if (current instanceof Boolean curB && newValue instanceof Boolean newB) {
            // True is typically "higher quality" for most settings
            if (curB.equals(newB)) return 0;
            return newB ? 1 : -1;
        }

        return 0;
    }
}
