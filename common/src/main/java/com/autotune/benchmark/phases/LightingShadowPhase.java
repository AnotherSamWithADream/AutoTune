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
 * Phase 5: Lighting and Shadow
 * Tests the four combinations of smooth lighting (on/off) and entity shadows (on/off)
 * at 300 frames each. These settings affect per-block lighting interpolation and
 * mob shadow rendering.
 */
public class LightingShadowPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(LightingShadowPhase.class);
    private static final String PHASE_ID = "phase_05_lighting_shadow";
    private static final String PHASE_NAME = "Lighting & Shadow";
    private static final int FRAMES_PER_COMBO = BenchmarkConstants.FRAMES_SHORT;

    /** All combinations to test: [smoothLighting, entityShadows]. */
    private static final boolean[][] COMBOS = {
            {false, false},
            {false, true},
            {true, false},
            {true, true}
    };

    private static final String[] COMBO_LABELS = {
            "smooth_off_shadow_off",
            "smooth_off_shadow_on",
            "smooth_on_shadow_off",
            "smooth_on_shadow_on"
    };

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
        return "Tests combinations of smooth lighting and entity shadows to measure their individual and combined cost.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_COMBO * COMBOS.length;
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
        boolean originalSmooth = adapter.getSmoothLighting();
        boolean originalShadows = adapter.getEntityShadows();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int c = 0; c < COMBOS.length; c++) {
                boolean smooth = COMBOS[c][0];
                boolean shadows = COMBOS[c][1];
                String label = COMBO_LABELS[c];

                LOGGER.info("Testing smooth={}, shadows={} ({} frames)", smooth, shadows, FRAMES_PER_COMBO);

                // Apply settings
                adapter.setSmoothLighting(smooth);
                adapter.setEntityShadows(shadows);

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
                for (int i = 0; i < FRAMES_PER_COMBO; i++) {
                    frameStart = System.nanoTime();
                    waitOneFrame(client);
                    long frameTime = System.nanoTime() - frameStart;
                    sampler.record(frameTime);

                    // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                    if (i % 60 == 0 || i == FRAMES_PER_COMBO - 1) {
                        callback.onProgress(label, completedFrames + i, totalFrames);
                    }
                }

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                LOGGER.info("  {}: avg={} FPS, 1% low={} FPS", label,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()));

                completedFrames += FRAMES_PER_COMBO;
            }

        } catch (Exception e) {
            LOGGER.error("Error during lighting/shadow phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setSmoothLighting(originalSmooth);
            adapter.setEntityShadows(originalShadows);
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
