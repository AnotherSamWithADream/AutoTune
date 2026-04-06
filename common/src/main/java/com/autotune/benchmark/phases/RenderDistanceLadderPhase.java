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
 * Phase 3: Render Distance Ladder
 * Tests render distances 4, 8, 12, 16, 20, 24, 28, 32 at 300 frames each.
 * This is one of the most impactful settings for performance and is included
 * in quick mode.
 */
public class RenderDistanceLadderPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderDistanceLadderPhase.class);
    private static final String PHASE_ID = "phase_03_render_distance";
    private static final String PHASE_NAME = "Render Distance Ladder";
    private static final int FRAMES_PER_STEP = BenchmarkConstants.FRAMES_SHORT;
    private static final int[] RENDER_DISTANCES = {4, 8, 12, 16, 20, 24, 28, 32};

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
        return "Tests render distances from 4 to 32 chunks to measure the performance cost of draw distance.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_STEP * RENDER_DISTANCES.length;
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
        int originalRd = adapter.getRenderDistance();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int rd : RENDER_DISTANCES) {
                String label = "rd_" + rd;
                LOGGER.info("Testing render distance {} ({} frames)", rd, FRAMES_PER_STEP);

                // Apply the render distance
                adapter.setRenderDistance(rd);

                // Settle frames: let chunks load/unload after the change
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
                for (int i = 0; i < FRAMES_PER_STEP; i++) {
                    frameStart = System.nanoTime();
                    waitOneFrame(client);
                    long frameTime = System.nanoTime() - frameStart;
                    sampler.record(frameTime);

                    // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                    if (i % 60 == 0 || i == FRAMES_PER_STEP - 1) {
                        callback.onProgress(label, completedFrames + i, totalFrames);
                    }
                }

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                LOGGER.info("  RD {}: avg={} FPS, 1% low={} FPS", rd,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()));

                completedFrames += FRAMES_PER_STEP;
            }

        } catch (Exception e) {
            LOGGER.error("Error during render distance ladder phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            // Restore original settings
            adapter.setRenderDistance(originalRd);
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
