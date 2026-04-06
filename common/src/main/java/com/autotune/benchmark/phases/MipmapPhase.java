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
 * Phase 13: Mipmap
 * Tests mipmap levels 0, 1, 2, 3, 4 at 300 frames each.
 * Mipmapping affects texture memory usage and sampling quality. Higher levels
 * use more VRAM for mipmap chains but can reduce texture aliasing at distance.
 * Changing mipmap level triggers a texture atlas rebuild.
 */
public class MipmapPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MipmapPhase.class);
    private static final String PHASE_ID = "phase_13_mipmap";
    private static final String PHASE_NAME = "Mipmap Levels";
    private static final int FRAMES_PER_LEVEL = BenchmarkConstants.FRAMES_SHORT;
    private static final int[] MIPMAP_LEVELS = {0, 1, 2, 3, 4};

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
        return "Tests mipmap levels 0 through 4 to measure texture memory and sampling performance impact.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_LEVEL * MIPMAP_LEVELS.length;
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
        int originalMipmap = adapter.getMipmapLevels();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int level : MIPMAP_LEVELS) {
                String label = "mipmap_" + level;
                LOGGER.info("Testing mipmap level {} ({} frames)", level, FRAMES_PER_LEVEL);

                // Apply mipmap level -- this triggers texture atlas rebuild
                adapter.setMipmapLevels(level);

                // Extended settle for texture rebuild
                for (int i = 0; i < BenchmarkConstants.SETTLE_FRAMES * 2; i++) {
                    waitOneFrame(client);
                }

                // Warmup
                for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                    waitOneFrame(client);
                }

                // Measurement
                sampler.reset();
                long frameStart;
                for (int i = 0; i < FRAMES_PER_LEVEL; i++) {
                    frameStart = System.nanoTime();
                    waitOneFrame(client);
                    long frameTime = System.nanoTime() - frameStart;
                    sampler.record(frameTime);

                    // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                    if (i % 60 == 0 || i == FRAMES_PER_LEVEL - 1) {
                        callback.onProgress(label, completedFrames + i, totalFrames);
                    }
                }

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                LOGGER.info("  Mipmap {}: avg={} FPS, 1% low={} FPS", level,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()));

                completedFrames += FRAMES_PER_LEVEL;
            }

        } catch (Exception e) {
            LOGGER.error("Error during mipmap phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setMipmapLevels(originalMipmap);
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
