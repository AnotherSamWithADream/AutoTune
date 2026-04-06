package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.benchmark.FrameTimeSampler;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 8: Block Entity Stress
 * Measures frame times with the current world's block entities (chests, signs,
 * banners, beacons, etc.) in view. Tests at two render distances -- a short
 * distance that culls most block entities and a longer distance that includes
 * more of them -- to isolate the block entity rendering cost.
 *
 * This phase uses the existing world state rather than placing new blocks,
 * since block entity density varies naturally between worlds and locations.
 */
public class BlockEntityStressPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockEntityStressPhase.class);
    private static final String PHASE_ID = "phase_08_block_entity_stress";
    private static final String PHASE_NAME = "Block Entity Stress";
    private static final int FRAMES_PER_TEST = BenchmarkConstants.FRAMES_SHORT;

    /** Render distances for the two sub-tests: short culls most BEs, long includes them. */
    private static final int RD_SHORT = 4;
    private static final int RD_LONG = 16;

    /** Camera rotation speed to sweep across block entities. */
    private static final float ROTATION_SPEED = 3.0f;

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
        return "Measures block entity rendering cost using existing world state at short and long render distances.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_TEST * 2;
    }

    @Override
    public PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                               FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(PHASE_ID, PHASE_NAME, System.currentTimeMillis());

        if (client.player == null || client.world == null) {
            return PhaseResult.skipped(PHASE_ID, PHASE_NAME, "No player or world available");
        }

        // Save original settings
        int originalRd = adapter.getRenderDistance();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        // [CODE-REVIEW-FIX] Moved originalYaw declaration outside try so it can be restored in finally
        float originalYaw = Float.NaN;

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            ClientPlayerEntity player = client.player;
            originalYaw = player.getYaw();

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            // Sub-test 1: Short render distance (fewer block entities visible)
            {
                String label = "be_rd_" + RD_SHORT;
                LOGGER.info("Testing block entities at RD {} ({} frames)", RD_SHORT, FRAMES_PER_TEST);

                adapter.setRenderDistance(RD_SHORT);

                // Settle for chunk changes
                for (int i = 0; i < BenchmarkConstants.SETTLE_FRAMES; i++) {
                    waitOneFrame(client);
                }

                // Warmup with rotation
                for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                    player.setYaw(player.getYaw() + ROTATION_SPEED);
                    waitOneFrame(client);
                }

                // Measurement with rotation
                sampler.reset();
                long frameStart;
                for (int i = 0; i < FRAMES_PER_TEST; i++) {
                    frameStart = System.nanoTime();
                    player.setYaw(player.getYaw() + ROTATION_SPEED);
                    waitOneFrame(client);
                    long frameTime = System.nanoTime() - frameStart;
                    sampler.record(frameTime);

                    // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                    if (i % 60 == 0 || i == FRAMES_PER_TEST - 1) {
                        callback.onProgress(label, completedFrames + i, totalFrames);
                    }
                }

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                LOGGER.info("  RD {}: avg={} FPS", RD_SHORT, String.format("%.1f", stats.avgFps()));
                completedFrames += FRAMES_PER_TEST;
            }

            // Sub-test 2: Long render distance (more block entities visible)
            {
                String label = "be_rd_" + RD_LONG;
                LOGGER.info("Testing block entities at RD {} ({} frames)", RD_LONG, FRAMES_PER_TEST);

                adapter.setRenderDistance(RD_LONG);

                for (int i = 0; i < BenchmarkConstants.SETTLE_FRAMES; i++) {
                    waitOneFrame(client);
                }

                for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                    player.setYaw(player.getYaw() + ROTATION_SPEED);
                    waitOneFrame(client);
                }

                sampler.reset();
                long frameStart;
                for (int i = 0; i < FRAMES_PER_TEST; i++) {
                    frameStart = System.nanoTime();
                    player.setYaw(player.getYaw() + ROTATION_SPEED);
                    waitOneFrame(client);
                    long frameTime = System.nanoTime() - frameStart;
                    sampler.record(frameTime);

                    // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                    if (i % 60 == 0 || i == FRAMES_PER_TEST - 1) {
                        callback.onProgress(label, completedFrames + i, totalFrames);
                    }
                }

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                LOGGER.info("  RD {}: avg={} FPS", RD_LONG, String.format("%.1f", stats.avgFps()));
            }

        } catch (Exception e) {
            LOGGER.error("Error during block entity stress phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            // [CODE-REVIEW-FIX] Yaw restore moved to finally block to ensure restoration on exception
            if (!Float.isNaN(originalYaw) && client.player != null) {
                client.player.setYaw(originalYaw);
            }
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
