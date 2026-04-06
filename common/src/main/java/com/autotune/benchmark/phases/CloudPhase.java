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
 * Phase 14: Cloud Rendering
 * Tests cloud render modes OFF (0), FAST (1), and FANCY (2) at 300 frames each.
 * Cloud rendering adds geometry to the sky pass; fancy clouds use 3D volumetric
 * meshes while fast clouds use a flat 2D plane.
 */
public class CloudPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(CloudPhase.class);
    private static final String PHASE_ID = "phase_14_clouds";
    private static final String PHASE_NAME = "Cloud Rendering";
    private static final int FRAMES_PER_MODE = BenchmarkConstants.FRAMES_SHORT;

    /** Cloud modes: 0=OFF, 1=FAST, 2=FANCY. */
    private static final int[] CLOUD_MODES = {0, 1, 2};
    private static final String[] MODE_LABELS = {"OFF", "FAST", "FANCY"};

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
        return "Tests Off, Fast, and Fancy cloud rendering modes to measure sky pass overhead.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_MODE * CLOUD_MODES.length;
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
        int originalClouds = adapter.getCloudRenderMode();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int m = 0; m < CLOUD_MODES.length; m++) {
                int mode = CLOUD_MODES[m];
                String label = MODE_LABELS[m];

                LOGGER.info("Testing clouds: {} (mode={}, {} frames)", label, mode, FRAMES_PER_MODE);

                // Apply cloud mode
                adapter.setCloudRenderMode(mode);

                // Settle
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
                result.addMeasurement("clouds_" + label, stats);

                LOGGER.info("  Clouds {}: avg={} FPS, 1% low={} FPS", label,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()));

                completedFrames += FRAMES_PER_MODE;
            }

        } catch (Exception e) {
            LOGGER.error("Error during cloud phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setCloudRenderMode(originalClouds);
            adapter.setMaxFps(originalMaxFps);
            adapter.setVsync(originalVsync);
        }

        result.setEndTime(System.currentTimeMillis());
        callback.onProgress("complete", getEstimatedFrames(), getEstimatedFrames());
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
