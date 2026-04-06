package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 19: Sodium Phase (quickMode).
 * If Sodium is installed, tests each Sodium-specific toggle in isolation to measure
 * its individual impact on frame times. Toggles tested include block face culling,
 * fog occlusion, entity culling, CPU render-ahead values, and animation toggles.
 * Each sub-test records 300 frames.
 * If Sodium is not loaded, the phase is skipped.
 */
public class SodiumPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SodiumPhase.class);

    /**
     * Each toggle has a key (used as the sub-test label), a Sodium option class field name,
     * and two states (on/off or value variants).
     */
    private static final List<SodiumToggle> TOGGLES = List.of(
            new SodiumToggle("block_face_culling_on", "performance.useBlockFaceCulling", true),
            new SodiumToggle("block_face_culling_off", "performance.useBlockFaceCulling", false),
            new SodiumToggle("fog_occlusion_on", "performance.useFogOcclusion", true),
            new SodiumToggle("fog_occlusion_off", "performance.useFogOcclusion", false),
            new SodiumToggle("entity_culling_on", "performance.useEntityCulling", true),
            new SodiumToggle("entity_culling_off", "performance.useEntityCulling", false),
            new SodiumToggle("animate_water_on", "performance.animateWater", true),
            new SodiumToggle("animate_water_off", "performance.animateWater", false),
            new SodiumToggle("animate_lava_on", "performance.animateLava", true),
            new SodiumToggle("animate_lava_off", "performance.animateLava", false),
            new SodiumToggle("animate_fire_on", "performance.animateFire", true),
            new SodiumToggle("animate_fire_off", "performance.animateFire", false),
            new SodiumToggle("animate_portal_on", "performance.animatePortal", true),
            new SodiumToggle("animate_portal_off", "performance.animatePortal", false),
            new SodiumToggle("cpu_render_ahead_0", "advanced.cpuRenderAheadLimit", 0),
            new SodiumToggle("cpu_render_ahead_1", "advanced.cpuRenderAheadLimit", 1),
            new SodiumToggle("cpu_render_ahead_2", "advanced.cpuRenderAheadLimit", 2),
            new SodiumToggle("cpu_render_ahead_3", "advanced.cpuRenderAheadLimit", 3)
    );

    private final Map<String, Object> savedValues = new LinkedHashMap<>();
    private Object sodiumOptionsInstance;

    @Override
    public String getPhaseId() {
        return "phase_19_sodium";
    }

    @Override
    public String getPhaseName() {
        return "Sodium Toggles";
    }

    @Override
    public int getPhaseNumber() {
        return 19;
    }

    @Override
    public List<String> getSubTestLabels() {
        List<String> labels = new ArrayList<>();
        for (SodiumToggle toggle : TOGGLES) {
            labels.add(toggle.label);
        }
        return labels;
    }

    @Override
    public String shouldSkip() {
        if (!FabricLoader.getInstance().isModLoaded("sodium")) {
            return "Sodium is not installed";
        }
        return null;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        try {
            if (sodiumOptionsInstance == null) {
                sodiumOptionsInstance = loadSodiumOptions();
                if (sodiumOptionsInstance == null) {
                    LOGGER.warn("Could not load Sodium options instance");
                    return;
                }
                saveAllCurrentValues();
            }

            // [CODE-REVIEW-FIX] H-020: Restore ALL saved values to baseline BEFORE applying the
            // current toggle. This ensures each sub-test measures exactly one toggle change in
            // isolation, rather than cumulative changes from previous sub-tests.
            restoreAllSavedValues();

            SodiumToggle toggle = findToggle(subTestLabel);
            if (toggle == null) {
                LOGGER.warn("Unknown Sodium sub-test label: {}", subTestLabel);
                return;
            }

            LOGGER.info("Setting Sodium option {} = {}", toggle.optionPath, toggle.value);
            setSodiumOption(toggle.optionPath, toggle.value);
            writeSodiumOptions();
        } catch (Exception e) {
            LOGGER.error("Failed to configure Sodium for sub-test: {}", subTestLabel, e);
        }
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        try {
            if (sodiumOptionsInstance != null) {
                restoreAllSavedValues();
                writeSodiumOptions();
                LOGGER.info("Restored all Sodium options to original values");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to restore Sodium options", e);
        }
        sodiumOptionsInstance = null;
        savedValues.clear();
    }

    private SodiumToggle findToggle(String label) {
        for (SodiumToggle t : TOGGLES) {
            if (t.label.equals(label)) return t;
        }
        return null;
    }

    /**
     * Loads the Sodium options singleton via reflection.
     * [CODE-REVIEW-FIX] M-007: Prefer options() or getInstance() to retrieve the existing singleton.
     * The previous load() call created a new instance from disk on each invocation rather than
     * reusing the in-memory singleton, which could discard runtime changes made by the player.
     * We fall back to load() only if no singleton accessor is available.
     */
    private Object loadSodiumOptions() {
        String[] classNames = {
            "me.jellysquid.mods.sodium.client.gui.SodiumGameOptions",
            "net.caffeinemc.mods.sodium.client.gui.SodiumGameOptions"
        };
        // [CODE-REVIEW-FIX] Try singleton accessors first (options(), getInstance()), fall back to load()
        String[] singletonMethods = {"options", "getInstance", "load"};

        for (String className : classNames) {
            try {
                Class<?> sodiumOptionsClass = Class.forName(className);
                for (String methodName : singletonMethods) {
                    try {
                        Method method = sodiumOptionsClass.getMethod(methodName);
                        Object result = method.invoke(null);
                        if (result != null) {
                            LOGGER.debug("Loaded Sodium options via {}.{}()", className, methodName);
                            return result;
                        }
                    } catch (NoSuchMethodException ignored) {
                        // Try next method name
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // Try next class name
            } catch (Exception e) {
                LOGGER.warn("Could not load Sodium options from {}", className, e);
            }
        }
        LOGGER.warn("Could not load Sodium options class from any known package");
        return null;
    }

    /**
     * Saves the current value of all toggled options so we can restore them later.
     */
    private void saveAllCurrentValues() {
        for (SodiumToggle toggle : TOGGLES) {
            try {
                Object value = getSodiumOption(toggle.optionPath);
                if (value != null && !savedValues.containsKey(toggle.optionPath)) {
                    savedValues.put(toggle.optionPath, value);
                }
            } catch (Exception e) {
                LOGGER.debug("Could not save Sodium option {}: {}", toggle.optionPath, e.toString());
            }
        }
    }

    /**
     * Restores all saved option values.
     */
    private void restoreAllSavedValues() {
        for (Map.Entry<String, Object> entry : savedValues.entrySet()) {
            try {
                setSodiumOption(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                LOGGER.warn("Could not restore Sodium option {}: {}", entry.getKey(), e.toString());
            }
        }
    }

    /**
     * Reads a Sodium option value via reflection.
     * The optionPath is in the format "category.fieldName" where category is a nested object field.
     */
    private Object getSodiumOption(String optionPath) {
        try {
            String[] parts = optionPath.split("\\.", 2);
            Object category = sodiumOptionsInstance.getClass().getField(parts[0]).get(sodiumOptionsInstance);
            return category.getClass().getField(parts[1]).get(category);
        } catch (Exception e) {
            LOGGER.debug("Could not read Sodium option {}: {}", optionPath, e.toString());
            return null;
        }
    }

    /**
     * Sets a Sodium option value via reflection.
     */
    private void setSodiumOption(String optionPath, Object value) {
        try {
            String[] parts = optionPath.split("\\.", 2);
            Object category = sodiumOptionsInstance.getClass().getField(parts[0]).get(sodiumOptionsInstance);
            java.lang.reflect.Field field = category.getClass().getField(parts[1]);
            if (value instanceof Integer intVal && field.getType() == int.class) {
                field.setInt(category, intVal);
            } else if (value instanceof Boolean boolVal && field.getType() == boolean.class) {
                field.setBoolean(category, boolVal);
            } else {
                field.set(category, value);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not set Sodium option {} = {}: {}", optionPath, value, e.toString());
        }
    }

    /**
     * Writes Sodium options to disk so they take effect.
     */
    private void writeSodiumOptions() {
        try {
            Method writeMethod = sodiumOptionsInstance.getClass().getMethod("writeToDisk");
            writeMethod.invoke(sodiumOptionsInstance);
        } catch (NoSuchMethodException e) {
            // Try alternative method names
            try {
                Method saveMethod = sodiumOptionsInstance.getClass().getMethod("save");
                saveMethod.invoke(sodiumOptionsInstance);
            } catch (Exception e2) {
                LOGGER.debug("Could not write Sodium options to disk: {}", e2.getMessage());
            }
        } catch (Exception e) {
            LOGGER.debug("Could not write Sodium options to disk: {}", e.toString());
        }
    }

    private record SodiumToggle(String label, String optionPath, Object value) {}
}
