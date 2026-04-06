package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 22: Sustained Load Phase.
 * Runs 5000 continuous frames at high graphical settings to detect thermal throttling.
 * The phase monitors for FPS degradation over time by comparing the average FPS of
 * the first quarter to the last quarter of the run. A significant drop indicates
 * the GPU or CPU may be thermally throttling.
 *
 * High settings used: Fancy graphics, render distance 16, max FPS uncapped,
 * biome blend 5, particles ALL, smooth lighting ON.
 */
public class SustainedLoadPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SustainedLoadPhase.class);

    /**
     * FPS degradation threshold: if the last-quarter average is more than 15%
     * lower than the first-quarter average, we flag thermal throttling.
     */
    private static final double THROTTLE_THRESHOLD = 0.15;

    private GraphicsMode originalGraphicsMode;
    private int originalRenderDistance;
    private int originalMaxFps;
    private int originalBiomeBlend;
    private int originalParticles;
    private boolean originalSmoothLighting;

    private long[] quarterBoundaryTimes;

    @Override
    public String getPhaseId() {
        return "phase_22_sustained_load";
    }

    @Override
    public String getPhaseName() {
        return "Sustained Load";
    }

    @Override
    public int getPhaseNumber() {
        return 22;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of("sustained_high");
    }

    @Override
    public int getFramesPerSubTest() {
        return BenchmarkConstants.FRAMES_EXTREME;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        // Save original settings
        originalGraphicsMode = adapter.getGraphicsMode();
        originalRenderDistance = adapter.getRenderDistance();
        originalMaxFps = adapter.getMaxFps();
        originalBiomeBlend = adapter.getBiomeBlendRadius();
        originalParticles = adapter.getParticles();
        originalSmoothLighting = adapter.getSmoothLighting();

        LOGGER.info("Setting high-load settings for sustained test ({} frames)", BenchmarkConstants.FRAMES_EXTREME);

        // Apply high-stress settings
        adapter.setGraphicsMode(GraphicsMode.FANCY);
        adapter.setRenderDistance(16);
        adapter.setMaxFps(260);
        adapter.setBiomeBlendRadius(5);
        adapter.setParticles(0); // ALL
        adapter.setSmoothLighting(true);

        quarterBoundaryTimes = new long[5]; // boundaries at 0%, 25%, 50%, 75%, 100%
    }

    @Override
    public void onMeasurementFrame(String subTestLabel, int frameIndex, MinecraftClient client) {
        int totalFrames = BenchmarkConstants.FRAMES_EXTREME;
        int quarterSize = totalFrames / 4;

        // Record wall-clock times at quarter boundaries
        if (frameIndex == 0) {
            quarterBoundaryTimes[0] = System.nanoTime();
        }
        if (frameIndex == quarterSize) {
            quarterBoundaryTimes[1] = System.nanoTime();
        }
        if (frameIndex == quarterSize * 2) {
            quarterBoundaryTimes[2] = System.nanoTime();
        }
        if (frameIndex == quarterSize * 3) {
            quarterBoundaryTimes[3] = System.nanoTime();
        }
        if (frameIndex == totalFrames - 1) {
            quarterBoundaryTimes[4] = System.nanoTime();
        }
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        // Analyze thermal throttling
        if (quarterBoundaryTimes[0] > 0 && quarterBoundaryTimes[4] > 0) {
            int quarterSize = BenchmarkConstants.FRAMES_EXTREME / 4;
            double firstQuarterDuration = (quarterBoundaryTimes[1] - quarterBoundaryTimes[0]) / 1_000_000_000.0;
            double lastQuarterDuration = (quarterBoundaryTimes[4] - quarterBoundaryTimes[3]) / 1_000_000_000.0;

            double firstQuarterFps = firstQuarterDuration > 0 ? quarterSize / firstQuarterDuration : 0;
            double lastQuarterFps = lastQuarterDuration > 0 ? quarterSize / lastQuarterDuration : 0;

            // [CODE-REVIEW-FIX] SLF4J does not support {:.1f} format specifiers; use String.format
            LOGGER.info("Sustained load: first quarter avg FPS = {}, last quarter avg FPS = {}",
                    String.format("%.1f", firstQuarterFps), String.format("%.1f", lastQuarterFps));

            if (firstQuarterFps > 0) {
                double degradation = (firstQuarterFps - lastQuarterFps) / firstQuarterFps;
                if (degradation > THROTTLE_THRESHOLD) {
                    LOGGER.warn("Thermal throttling detected: {}% FPS degradation over sustained load",
                            String.format("%.1f", degradation * 100));
                } else {
                    LOGGER.info("No significant thermal throttling detected ({}% change)", String.format("%.1f", degradation * 100));
                }
            }
        }

        // Restore original settings
        LOGGER.info("Restoring original settings after sustained load test");
        adapter.setGraphicsMode(originalGraphicsMode);
        adapter.setRenderDistance(originalRenderDistance);
        adapter.setMaxFps(originalMaxFps);
        adapter.setBiomeBlendRadius(originalBiomeBlend);
        adapter.setParticles(originalParticles);
        adapter.setSmoothLighting(originalSmoothLighting);
    }
}
