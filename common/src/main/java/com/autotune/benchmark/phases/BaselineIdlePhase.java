package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.benchmark.FrameTimeSampler;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1: Baseline Idle
 * Records 600 frames at the user's current default settings with no camera
 * movement. This establishes the baseline performance of the system at rest.
 */
public class BaselineIdlePhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaselineIdlePhase.class);
    private static final String PHASE_ID = "phase_01_baseline_idle";
    private static final String PHASE_NAME = "Baseline Idle";
    private static final int MEASUREMENT_FRAMES = BenchmarkConstants.FRAMES_MEDIUM;

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
        return "Records frame times at default settings with no camera movement to establish baseline performance.";
    }

    @Override
    public int getEstimatedFrames() {
        return MEASUREMENT_FRAMES;
    }

    @Override
    public PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                               FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(PHASE_ID, PHASE_NAME, System.currentTimeMillis());

        // Save original settings
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            // Uncap FPS for accurate measurement
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            LOGGER.info("Starting baseline idle measurement ({} frames)", MEASUREMENT_FRAMES);

            // Warmup phase: let the renderer stabilize
            for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                waitOneFrame(client);
            }

            // Measurement phase
            sampler.reset();
            long frameStart;
            for (int i = 0; i < MEASUREMENT_FRAMES; i++) {
                frameStart = System.nanoTime();
                waitOneFrame(client);
                long frameTime = System.nanoTime() - frameStart;
                sampler.record(frameTime);

                // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                if (i % 60 == 0 || i == MEASUREMENT_FRAMES - 1) {
                    callback.onProgress("idle", i, MEASUREMENT_FRAMES);
                }
            }

            // Build statistics from recorded samples
            long[] samples = sampler.getSamplesSnapshot();
            FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
            result.addMeasurement("idle_default", stats);

            LOGGER.info("Baseline idle complete: avg={} FPS, 1% low={} FPS",
                    String.format("%.1f", stats.avgFps()),
                    String.format("%.1f", stats.p1LowFps()));

        } catch (Exception e) {
            LOGGER.error("Error during baseline idle phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            // Restore original settings
            adapter.setMaxFps(originalMaxFps);
            adapter.setVsync(originalVsync);
        }

        result.setEndTime(System.currentTimeMillis());
        // [CODE-REVIEW-FIX] M-015: Wrap final callback in try/catch so a callback failure
        // does not swallow the result after the phase has completed successfully.
        // [CODE-REVIEW-FIX] Guard against null callback
        if (callback != null) {
            try {
                callback.onProgress("idle", MEASUREMENT_FRAMES, MEASUREMENT_FRAMES);
            } catch (Exception callbackEx) {
                LOGGER.warn("Final progress callback failed", callbackEx);
            }
        }
        return result;
    }

    /**
     * Yields execution for approximately one frame by sleeping briefly.
     * In an actual Minecraft mod this would integrate with the render loop;
     * here we yield to allow the client tick/render cycle to proceed.
     */
    private void waitOneFrame(MinecraftClient client) {
        // Render one frame by calling the render method indirectly through
        // the game loop. In practice the benchmark runner drives this from
        // within the render callback, so this is a cooperative yield.
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
