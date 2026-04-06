package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 30: Combined Verification Phase.
 * Applies the optimizer's recommended optimal settings all at once, then measures
 * 1000 frames to verify actual performance against the predicted FPS.
 * If actual FPS deviates from the prediction by more than 10%, the result is
 * flagged for correction. This catches cases where settings interact in ways
 * the optimizer's per-setting model did not account for.
 *
 * The optimal settings are supplied via the static {@link #setOptimalSettings(OptimalSettings)}
 * method before this phase executes. If no settings are provided, the phase is skipped.
 */
public class CombinedVerificationPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(CombinedVerificationPhase.class);

    /**
     * If actual FPS is more than this fraction off from predicted, flag it.
     */
    private static final double DEVIATION_THRESHOLD = 0.10;

    /**
     * Thread-safe holder for the optimizer's computed settings.
     * Set before the benchmark runner reaches phase 30.
     */
    private static volatile OptimalSettings optimalSettings;

    // Saved original settings for restoration
    private int savedRenderDistance;
    private int savedSimulationDistance;
    private GraphicsMode savedGraphicsMode;
    private boolean savedSmoothLighting;
    private int savedMaxFps;
    private boolean savedVsync;
    private int savedBiomeBlend;
    private boolean savedEntityShadows;
    private int savedParticles;
    private int savedMipmapLevels;
    private int savedCloudMode;
    private int savedFov;
    private int savedGuiScale;
    private float savedEntityDistance;
    private double savedGamma;
    private boolean savedFullscreen;

    @Override
    public String getPhaseId() {
        return "phase_30_combined_verification";
    }

    @Override
    public String getPhaseName() {
        return "Combined Verification";
    }

    @Override
    public int getPhaseNumber() {
        return 30;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of("optimal_combined");
    }

    @Override
    public int getFramesPerSubTest() {
        return BenchmarkConstants.FRAMES_LONG;
    }

    @Override
    public String shouldSkip() {
        if (optimalSettings == null) {
            return "No optimal settings have been computed yet";
        }
        return null;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        OptimalSettings settings = optimalSettings;
        if (settings == null) {
            LOGGER.warn("No optimal settings available for verification");
            return;
        }

        // Save all current settings
        saveCurrentSettings(adapter);

        LOGGER.info("Applying {} optimal settings for verification (predicted FPS: {})",
                settings.settingsMap().size(), settings.predictedFps());

        // Apply each optimal setting
        applyOptimalSettings(settings, adapter);
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring all settings after combined verification");
        restoreSettings(adapter);
    }

    private void saveCurrentSettings(PlatformAdapter adapter) {
        savedRenderDistance = adapter.getRenderDistance();
        savedSimulationDistance = adapter.getSimulationDistance();
        savedGraphicsMode = adapter.getGraphicsMode();
        savedSmoothLighting = adapter.getSmoothLighting();
        savedMaxFps = adapter.getMaxFps();
        savedVsync = adapter.getVsync();
        savedBiomeBlend = adapter.getBiomeBlendRadius();
        savedEntityShadows = adapter.getEntityShadows();
        savedParticles = adapter.getParticles();
        savedMipmapLevels = adapter.getMipmapLevels();
        savedCloudMode = adapter.getCloudRenderMode();
        savedFov = adapter.getFov();
        savedGuiScale = adapter.getGuiScale();
        savedEntityDistance = adapter.getEntityDistanceScaling();
        savedGamma = adapter.getGamma();
        savedFullscreen = adapter.getFullscreen();
    }

    private void restoreSettings(PlatformAdapter adapter) {
        adapter.setRenderDistance(savedRenderDistance);
        adapter.setSimulationDistance(savedSimulationDistance);
        adapter.setGraphicsMode(savedGraphicsMode);
        adapter.setSmoothLighting(savedSmoothLighting);
        adapter.setMaxFps(savedMaxFps);
        adapter.setVsync(savedVsync);
        adapter.setBiomeBlendRadius(savedBiomeBlend);
        adapter.setEntityShadows(savedEntityShadows);
        adapter.setParticles(savedParticles);
        adapter.setMipmapLevels(savedMipmapLevels);
        adapter.setCloudRenderMode(savedCloudMode);
        adapter.setFov(savedFov);
        adapter.setGuiScale(savedGuiScale);
        adapter.setEntityDistanceScaling(savedEntityDistance);
        adapter.setGamma(savedGamma);
        adapter.setFullscreen(savedFullscreen);
    }

    private void applyOptimalSettings(OptimalSettings settings, PlatformAdapter adapter) {
        Map<String, Object> map = settings.settingsMap();

        if (map.containsKey("renderDistance")) {
            adapter.setRenderDistance(((Number) map.get("renderDistance")).intValue());
        }
        if (map.containsKey("simulationDistance")) {
            adapter.setSimulationDistance(((Number) map.get("simulationDistance")).intValue());
        }
        if (map.containsKey("graphicsMode")) {
            Object val = map.get("graphicsMode");
            if (val instanceof GraphicsMode gm) {
                adapter.setGraphicsMode(gm);
            } else if (val instanceof Number n) {
                adapter.setGraphicsMode(GraphicsMode.values()[n.intValue()]);
            }
        }
        if (map.containsKey("smoothLighting")) {
            adapter.setSmoothLighting((Boolean) map.get("smoothLighting"));
        }
        if (map.containsKey("maxFps")) {
            adapter.setMaxFps(((Number) map.get("maxFps")).intValue());
        }
        if (map.containsKey("vsync")) {
            adapter.setVsync((Boolean) map.get("vsync"));
        }
        if (map.containsKey("biomeBlendRadius")) {
            adapter.setBiomeBlendRadius(((Number) map.get("biomeBlendRadius")).intValue());
        }
        if (map.containsKey("entityShadows")) {
            adapter.setEntityShadows((Boolean) map.get("entityShadows"));
        }
        if (map.containsKey("particles")) {
            adapter.setParticles(((Number) map.get("particles")).intValue());
        }
        if (map.containsKey("mipmapLevels")) {
            adapter.setMipmapLevels(((Number) map.get("mipmapLevels")).intValue());
        }
        if (map.containsKey("cloudRenderMode")) {
            adapter.setCloudRenderMode(((Number) map.get("cloudRenderMode")).intValue());
        }
        if (map.containsKey("fov")) {
            adapter.setFov(((Number) map.get("fov")).intValue());
        }
        if (map.containsKey("guiScale")) {
            adapter.setGuiScale(((Number) map.get("guiScale")).intValue());
        }
        if (map.containsKey("entityDistanceScaling")) {
            adapter.setEntityDistanceScaling(((Number) map.get("entityDistanceScaling")).floatValue());
        }
        if (map.containsKey("gamma")) {
            adapter.setGamma(((Number) map.get("gamma")).doubleValue());
        }
        if (map.containsKey("fullscreen")) {
            adapter.setFullscreen((Boolean) map.get("fullscreen"));
        }
    }

    /**
     * Holds the optimizer's computed optimal settings and its predicted FPS.
     * The benchmark runner or optimizer populates this before phase 30 executes.
     */
    public record OptimalSettings(Map<String, Object> settingsMap, double predictedFps) {

        public OptimalSettings(Map<String, Object> settingsMap, double predictedFps) {
            this.settingsMap = new LinkedHashMap<>(settingsMap);
            this.predictedFps = predictedFps;
        }

        /**
         * Checks whether the actual measured FPS deviates from the prediction
         * by more than the allowed threshold.
         *
         * @param actualFps the measured average FPS from the verification run
         * @return true if the deviation exceeds 10%, indicating the prediction needs correction
         */
        public boolean isFlagged(double actualFps) {
            if (predictedFps <= 0) return false;
            double deviation = Math.abs(actualFps - predictedFps) / predictedFps;
            return deviation > DEVIATION_THRESHOLD;
        }

        /**
         * Returns the percentage deviation between actual and predicted FPS.
         */
        public double getDeviationPercent(double actualFps) {
            if (predictedFps <= 0) return 0;
            return ((actualFps - predictedFps) / predictedFps) * 100.0;
        }
    }
}
