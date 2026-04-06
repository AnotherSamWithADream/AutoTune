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
 * Phase 9: Translucency
 * Tests transparency rendering by comparing FANCY and FABULOUS graphics modes.
 * FABULOUS enables translucency sorting (Fabulous! graphics) which uses
 * additional framebuffer passes for correct alpha blending of water, stained
 * glass, and other translucent blocks. 300 frames each.
 */
public class TranslucencyPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslucencyPhase.class);
    private static final String PHASE_ID = "phase_09_translucency";
    private static final String PHASE_NAME = "Translucency";
    private static final int FRAMES_PER_MODE = BenchmarkConstants.FRAMES_SHORT;

    private static final GraphicsMode[] MODES = {GraphicsMode.FANCY, GraphicsMode.FABULOUS};
    private static final String[] MODE_LABELS = {"FANCY", "FABULOUS"};

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
        return "Compares Fancy vs Fabulous graphics to isolate the cost of translucency sorting.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_MODE * MODES.length;
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
                LOGGER.info("Testing translucency with {} mode ({} frames)", label, FRAMES_PER_MODE);

                adapter.setGraphicsMode(MODES[m]);

                // Settle for graphics mode change
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
                result.addMeasurement("translucency_" + label, stats);

                LOGGER.info("  {}: avg={} FPS, 1% low={} FPS", label,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()));

                completedFrames += FRAMES_PER_MODE;
            }

        } catch (Exception e) {
            LOGGER.error("Error during translucency phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setGraphicsMode(originalMode);
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
