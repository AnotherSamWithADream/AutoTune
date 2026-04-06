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
 * Phase 12: Chunk Loading
 * Simulates a teleport-like event by rapidly changing render distance from
 * minimum to maximum and back, forcing the chunk system to load and unload
 * large batches of chunks. Runs 3 trials.
 *
 * Each trial:
 * 1. Sets render distance to 2 (minimum) and waits for unloading
 * 2. Jumps render distance to 24 and records frames during mass loading
 * 3. Captures the frame time impact of sudden chunk loading pressure
 *
 * This simulates what happens when a player teleports or when a server
 * changes the render distance.
 */
public class ChunkLoadingPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkLoadingPhase.class);
    private static final String PHASE_ID = "phase_12_chunk_loading";
    private static final String PHASE_NAME = "Chunk Loading";
    private static final int FRAMES_PER_TRIAL = BenchmarkConstants.FRAMES_SHORT;
    private static final int NUM_TRIALS = 3;

    private static final int RD_MIN = 2;
    private static final int RD_MAX = 24;

    /** Extra settle frames when shrinking render distance to ensure chunks unload. */
    private static final int UNLOAD_SETTLE_FRAMES = 90;

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
        return "Simulates teleport by rapidly changing render distance from 2 to 24, measuring chunk loading stutter.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_TRIAL * NUM_TRIALS;
    }

    @Override
    public PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                               FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(PHASE_ID, PHASE_NAME, System.currentTimeMillis());

        if (client.world == null) {
            return PhaseResult.skipped(PHASE_ID, PHASE_NAME, "No world loaded");
        }

        // Save original settings
        int originalRd = adapter.getRenderDistance();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int trial = 1; trial <= NUM_TRIALS; trial++) {
                String label = "load_trial_" + trial;
                LOGGER.info("Chunk loading trial {} of {}", trial, NUM_TRIALS);

                // Step 1: Shrink render distance to minimum and let chunks unload
                adapter.setRenderDistance(RD_MIN);
                LOGGER.info("  Set RD to {}, waiting for chunks to unload...", RD_MIN);

                for (int i = 0; i < UNLOAD_SETTLE_FRAMES; i++) {
                    waitOneFrame(client);
                }

                int chunksAtMin = adapter.getLoadedChunkCount();
                LOGGER.info("  Chunks at RD {}: {}", RD_MIN, chunksAtMin);

                // Step 2: Jump render distance to maximum -- this triggers mass chunk loading
                adapter.setRenderDistance(RD_MAX);
                LOGGER.info("  Jumped RD to {}, recording frames during loading...", RD_MAX);

                // Record frames immediately -- no warmup, we want to capture the loading stutter
                sampler.reset();
                long frameStart;
                for (int i = 0; i < FRAMES_PER_TRIAL; i++) {
                    frameStart = System.nanoTime();
                    waitOneFrame(client);
                    long frameTime = System.nanoTime() - frameStart;
                    sampler.record(frameTime);

                    // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                    if (i % 60 == 0 || i == FRAMES_PER_TRIAL - 1) {
                        callback.onProgress(label, completedFrames + i, totalFrames);
                    }
                }

                int chunksAtMax = adapter.getLoadedChunkCount();

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                LOGGER.info("  Trial {}: avg={} FPS, 1% low={} FPS, stutters={}, chunks loaded: {} -> {}",
                        trial,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()),
                        stats.stutterCount(),
                        chunksAtMin, chunksAtMax);

                completedFrames += FRAMES_PER_TRIAL;
            }

        } catch (Exception e) {
            LOGGER.error("Error during chunk loading phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setRenderDistance(originalRd);
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
