package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 29: HUD Overhead Phase.
 * Tests performance with the standard HUD visible versus the debug screen (F3) active.
 * Records 300 frames in each mode. The debug screen renders significantly more text
 * and data overlays, which can stress the font renderer, increase draw calls,
 * and reveal bottlenecks in the GUI rendering pipeline.
 */
public class HUDOverheadPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(HUDOverheadPhase.class);

    private boolean originalDebugEnabled;

    @Override
    public String getPhaseId() {
        return "phase_29_hud_overhead";
    }

    @Override
    public String getPhaseName() {
        return "HUD Overhead";
    }

    @Override
    public int getPhaseNumber() {
        return 29;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of("hud_normal", "hud_debug");
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        if (subTestLabel.equals("hud_normal")) {
            // Save original debug state and ensure debug HUD is off
            originalDebugEnabled = client.getDebugHud().shouldShowDebugHud();
            LOGGER.info("Testing with normal HUD (saving original debug={} )", originalDebugEnabled);
            setDebugHud(client, false);
        } else {
            LOGGER.info("Testing with debug screen (F3) active");
            setDebugHud(client, true);
        }
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring debug HUD to {}", originalDebugEnabled);
        setDebugHud(client, originalDebugEnabled);
    }

    /**
     * Toggles the debug HUD via the client's debug HUD accessor.
     * Uses reflection as a fallback since the toggle method may vary by version.
     */
    private void setDebugHud(MinecraftClient client, boolean enabled) {
        try {
            boolean currentlyEnabled = client.getDebugHud().shouldShowDebugHud();
            if (currentlyEnabled != enabled) {
                // The debug HUD is typically toggled via options.debugEnabled or
                // by invoking the key handler. We use reflection to set the field directly.
                java.lang.reflect.Field debugField = findDebugField(client);
                if (debugField != null) {
                    debugField.setAccessible(true);
                    debugField.setBoolean(client.getDebugHud(), enabled);
                } else {
                    // Fallback: try toggleDebugHud via reflection (method may not exist in all versions)
                    tryToggleViaReflection(client);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not set debug HUD state to {}: {}", enabled, e.toString()); // [CODE-REVIEW-FIX] L-002
            // Attempt toggle as last resort
            tryToggleViaReflection(client);
        }
    }

    /**
     * Attempts to find the boolean field controlling the debug HUD visibility.
     * [CODE-REVIEW-FIX] M-009: This heuristic is fragile -- it picks the first boolean field
     * whose value matches shouldShowDebugHud(), which may produce false positives if multiple
     * boolean fields happen to share the same value. A sanity check on the field name is
     * applied to reduce the risk of matching an unrelated field.
     */
    private java.lang.reflect.Field findDebugField(MinecraftClient client) {
        try {
            Class<?> debugHudClass = client.getDebugHud().getClass();
            for (java.lang.reflect.Field field : debugHudClass.getDeclaredFields()) {
                if (field.getType() == boolean.class) {
                    field.setAccessible(true);
                    // Check if toggling this field matches shouldShowDebugHud
                    boolean current = field.getBoolean(client.getDebugHud());
                    boolean hudShowing = client.getDebugHud().shouldShowDebugHud();
                    if (current == hudShowing) {
                        // [CODE-REVIEW-FIX] M-009: Sanity check -- prefer fields whose name
                        // contains "debug" (case-insensitive) to reduce false positive matches
                        if (!field.getName().toLowerCase().contains("debug")) {
                            LOGGER.debug("Skipping boolean field '{}' -- name does not contain 'debug'", field.getName());
                            continue;
                        }
                        return field;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not locate debug HUD boolean field: {}", e.toString()); // [CODE-REVIEW-FIX] L-002
        }
        return null;
    }

    /**
     * Attempts to call toggleDebugHud() via reflection since the method
     * may not exist in all MC versions.
     */
    private void tryToggleViaReflection(MinecraftClient client) {
        try {
            java.lang.reflect.Method toggleMethod = client.getDebugHud().getClass()
                    .getMethod("toggleDebugHud");
            toggleMethod.invoke(client.getDebugHud());
        } catch (NoSuchMethodException e) {
            LOGGER.debug("toggleDebugHud() not available in this MC version, skipping debug HUD sub-test");
        } catch (Exception e) {
            LOGGER.error("Failed to toggle debug HUD via reflection", e);
        }
    }
}
