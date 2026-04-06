package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Phase 21: Resolution Scale Phase.
 * If Iris is loaded (provides render scale support), tests rendering at
 * 50%, 75%, 100%, 125%, 150%, and 200% resolution scale.
 * Records 300 frames at each scale level.
 * Lower resolution scales reduce the internal render resolution, improving performance
 * at the cost of visual quality. Higher scales provide supersampling.
 * If Iris is not loaded, the phase is skipped.
 */
public class ResolutionScalePhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResolutionScalePhase.class);

    private static final float[] SCALE_VALUES = {0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 2.00f};
    private static final String[] SCALE_LABELS = {"scale_50", "scale_75", "scale_100", "scale_125", "scale_150", "scale_200"};

    private float originalRenderScale;
    // [CODE-REVIEW-FIX] M-003: Use a boolean guard to save originals on first call,
    // regardless of which sub-test runs first (order-independent).
    private boolean savedOriginals = false;

    @Override
    public String getPhaseId() {
        return "phase_21_resolution_scale";
    }

    @Override
    public String getPhaseName() {
        return "Resolution Scale";
    }

    @Override
    public int getPhaseNumber() {
        return 21;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of(SCALE_LABELS);
    }

    @Override
    public String shouldSkip() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return "Iris is not installed (render scale requires Iris)";
        }
        return null;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        int idx = -1;
        for (int i = 0; i < SCALE_LABELS.length; i++) {
            if (SCALE_LABELS[i].equals(subTestLabel)) {
                idx = i;
                break;
            }
        }

        if (idx < 0) {
            LOGGER.warn("Unknown resolution scale sub-test: {}", subTestLabel);
            return;
        }

        if (!savedOriginals) {
            originalRenderScale = getRenderScale();
            savedOriginals = true;
            LOGGER.info("Saving original render scale: {}", originalRenderScale);
        }

        float targetScale = SCALE_VALUES[idx];
        LOGGER.info("Setting render scale to {}%", (int) (targetScale * 100));
        setRenderScale(targetScale);
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring render scale to {}", originalRenderScale);
        setRenderScale(originalRenderScale);
        // [CODE-REVIEW-FIX] Reset so a second benchmark run re-captures current values
        savedOriginals = false;
    }

    private float getRenderScale() {
        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method instanceMethod = irisApiClass.getMethod("getInstance");
            Object irisApi = instanceMethod.invoke(null);
            Method getScale = irisApi.getClass().getMethod("getRenderScale");
            Object result = getScale.invoke(irisApi);
            if (result instanceof Float f) {
                return f;
            } else if (result instanceof Number n) {
                return n.floatValue();
            }
        } catch (Exception e) {
            LOGGER.debug("Could not get Iris render scale: {}", e.toString());
        }
        return 1.0f;
    }

    private void setRenderScale(float scale) {
        try {
            Class<?> irisConfigClass = Class.forName("net.irisshaders.iris.config.IrisConfig");
            Method getInstanceMethod = irisConfigClass.getMethod("getInstance");
            Object config = getInstanceMethod.invoke(null);

            // Try setRenderScale first
            try {
                Method setScale = config.getClass().getMethod("setRenderScale", float.class);
                setScale.invoke(config, scale);
                return;
            } catch (NoSuchMethodException ignored) {}

            // Try setResolutionScale
            try {
                Method setScale = config.getClass().getMethod("setResolutionScale", float.class);
                setScale.invoke(config, scale);
                return;
            } catch (NoSuchMethodException ignored) {}

            // Try setShadingScale
            try {
                Method setScale = config.getClass().getMethod("setShadingScale", float.class);
                setScale.invoke(config, scale);
            } catch (NoSuchMethodException e) {
                LOGGER.warn("Could not find any render scale setter in Iris config");
            }
        } catch (Exception e) {
            LOGGER.warn("Could not set render scale to {}: {}", scale, e.toString());
        }
    }
}
