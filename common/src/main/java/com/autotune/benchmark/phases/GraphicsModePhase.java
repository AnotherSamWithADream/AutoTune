package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.benchmark.FrameTimeSampler;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 4: Graphics Mode
 * Tests FAST, FANCY, and FABULOUS graphics modes at 300 frames each.
 * This is a core setting that controls translucency sorting, leaf rendering,
 * weather effects, and more.
 */
public class GraphicsModePhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphicsModePhase.class);
    private static final String PHASE_ID = "phase_04_graphics_mode";
    private static final String PHASE_NAME = "Graphics Mode";
    private static final int FRAMES_PER_MODE = BenchmarkConstants.FRAMES_SHORT;

    /** Graphics modes to test, in order from cheapest to most expensive. */
    private static final GraphicsMode[] MODES = {
            GraphicsMode.FAST,
            GraphicsMode.FANCY,
            GraphicsMode.FABULOUS
    };

    private static final String[] MODE_LABELS = {"FAST", "FANCY", "FABULOUS"};

    @Override
    public String getId() {
        return PHASE_ID;
    }

    @Override
    public String getName() {
        return PHASE_NAME;
    }

    @Override
    public String getDescription() {
        return "Tests Fast, Fancy, and Fabulous graphics modes to measure their rendering cost.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_MODE * MODES.length;
    }

    @Override
    public boolean isQuickModePhase() {
        return true;
    }

    @Override
    public PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                               FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(PHASE_ID, PHASE_NAME, System.currentTimeMillis());

        // Save original settings
        GraphicsMode originalMode = adapter.getGraphicsMode();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int m = 0; m < MODES.length; m++) {
                String label = MODE_LABELS[m];
                LOGGER.info("Testing graphics mode: {} ({} frames)", label, FRAMES_PER_MODE);

                // Apply graphics mode
                adapter.setGraphicsMode(MODES[m]);

                // Settle to let renderer adapt to new mode
                for (int i = 0; i < BenchmarkConstants.SETTLE_FRAMES; i++) {
                    waitOneFrame(client);
                }

                // Warmup
                for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                    waitOneFrame(client);
                }

                // Measurement
                sampler.reset();
                long frameStart;
                for (int i = 0; i < FRAMES_PER_MODE; i++) {
                    frameStart = System.nanoTime();
                    waitOneFrame(client);
                    long frameTime = System.nanoTime() - frameStart;
                    sampler.record(frameTime);

                    // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                    if (i % 60 == 0 || i == FRAMES_PER_MODE - 1) {
                        callback.onProgress(label, completedFrames + i, totalFrames);
                    }
                }

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                LOGGER.info("  {}: avg={} FPS, 1% low={} FPS", label,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()));

                completedFrames += FRAMES_PER_MODE;
            }

        } catch (Exception e) {
            LOGGER.error("Error during graphics mode phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setGraphicsMode(originalMode);
            adapter.setMaxFps(originalMaxFps);
            adapter.setVsync(originalVsync);
        }

        result.setEndTime(System.currentTimeMillis());
        // [CODE-REVIEW-FIX] Guard against null callback after try-finally
        if (callback != null) {
            callback.onProgress("complete", getEstimatedFrames(), getEstimatedFrames());
        }
        return result;
    }

    private void waitOneFrame(MinecraftClient client) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
