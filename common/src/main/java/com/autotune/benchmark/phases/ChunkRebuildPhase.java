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
 * Phase 11: Chunk Rebuild
 * Forces chunk invalidation via PlatformAdapter.invalidateChunks() and measures
 * the time for chunks to rebuild. Runs 3 trials to account for variance.
 *
 * Each trial:
 * 1. Invalidates all loaded chunks
 * 2. Records frame times during the rebuild process (300 frames)
 * 3. Captures the stutter and frame time spike caused by chunk rebuilding
 *
 * This measures the chunk meshing pipeline performance, which is critical
 * for smooth gameplay when moving through the world.
 */
public class ChunkRebuildPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChunkRebuildPhase.class);
    private static final String PHASE_ID = "phase_11_chunk_rebuild";
    private static final String PHASE_NAME = "Chunk Rebuild";
    private static final int FRAMES_PER_TRIAL = BenchmarkConstants.FRAMES_SHORT;
    private static final int NUM_TRIALS = 3;

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
        return "Forces chunk invalidation and measures rebuild time across 3 trials.";
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
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int trial = 1; trial <= NUM_TRIALS; trial++) {
                String label = "rebuild_trial_" + trial;
                LOGGER.info("Chunk rebuild trial {} of {} ({} frames)", trial, NUM_TRIALS, FRAMES_PER_TRIAL);

                // Wait for any previous rebuild to complete before starting next trial
                for (int i = 0; i < BenchmarkConstants.SETTLE_FRAMES; i++) {
                    waitOneFrame(client);
                }

                // Record the chunk count before invalidation
                int chunksBefore = adapter.getLoadedChunkCount();

                // Force invalidation -- this causes all chunks to be re-meshed
                long invalidateTime = System.nanoTime();
                adapter.invalidateChunks();
                long invalidateDuration = System.nanoTime() - invalidateTime;

                LOGGER.info("  Invalidation took {} us, chunks before: {}",
                        invalidateDuration / 1000, chunksBefore);

                // Immediately start recording frames during the rebuild
                // No warmup here -- we want to capture the rebuild stutter
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

                int chunksAfter = adapter.getLoadedChunkCount();

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                LOGGER.info("  Trial {}: avg={} FPS, 1% low={} FPS, stutters={}, chunks after: {}",
                        trial,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()),
                        stats.stutterCount(),
                        chunksAfter);

                completedFrames += FRAMES_PER_TRIAL;
            }

        } catch (Exception e) {
            LOGGER.error("Error during chunk rebuild phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
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
