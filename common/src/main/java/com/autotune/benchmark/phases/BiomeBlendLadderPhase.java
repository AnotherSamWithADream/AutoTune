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
 * Phase 10: Biome Blend Ladder
 * Tests biome blend radii from 0 to 7 at 300 frames each.
 * Biome blend controls how smoothly biome colors (grass, water, foliage)
 * transition at biome boundaries. Higher values require sampling more
 * neighboring blocks for color interpolation during chunk meshing.
 */
public class BiomeBlendLadderPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BiomeBlendLadderPhase.class);
    private static final String PHASE_ID = "phase_10_biome_blend";
    private static final String PHASE_NAME = "Biome Blend Ladder";
    private static final int FRAMES_PER_STEP = BenchmarkConstants.FRAMES_SHORT;
    private static final int[] BLEND_RADII = {0, 1, 2, 3, 4, 5, 6, 7};

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
        return "Tests biome blend radii 0 through 7 to measure color interpolation overhead during chunk meshing.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_STEP * BLEND_RADII.length;
    }

    @Override
    public PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                               FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(PHASE_ID, PHASE_NAME, System.currentTimeMillis());

        // Save original settings
        int originalBlend = adapter.getBiomeBlendRadius();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int radius : BLEND_RADII) {
                String label = "blend_" + radius;
                LOGGER.info("Testing biome blend radius {} ({} frames)", radius, FRAMES_PER_STEP);

                // Apply biome blend radius -- this triggers chunk rebuild
                adapter.setBiomeBlendRadius(radius);

                // Extra settle time since biome blend changes cause chunk rebuilds
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

                LOGGER.info("  Blend {}: avg={} FPS, 1% low={} FPS", radius,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()));

                completedFrames += FRAMES_PER_STEP;
            }

        } catch (Exception e) {
            LOGGER.error("Error during biome blend ladder phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setBiomeBlendRadius(originalBlend);
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
