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
 * Phase 2: Baseline Rotation
 * Records 600 frames at default settings while continuously rotating the camera.
 * This stresses chunk face culling, frustum culling, and entity rendering as
 * different parts of the world come into view.
 */
public class BaselineRotationPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaselineRotationPhase.class);
    private static final String PHASE_ID = "phase_02_baseline_rotation";
    private static final String PHASE_NAME = "Baseline Rotation";
    private static final int MEASUREMENT_FRAMES = BenchmarkConstants.FRAMES_MEDIUM;

    /** Degrees of yaw rotation per frame during measurement. */
    private static final float ROTATION_SPEED = 2.0f;

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
        return "Records frame times while rotating the camera 360 degrees to stress culling and chunk loading.";
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

        // [CODE-REVIEW-FIX] Moved originalYaw declaration outside try so it can be restored in finally
        float originalYaw = Float.NaN;

        try {
            ClientPlayerEntity player = client.player;
            if (player == null) {
                return PhaseResult.skipped(PHASE_ID, PHASE_NAME, "No player entity available");
            }

            // Uncap FPS for accurate measurement
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            // Save original yaw so we can restore it
            originalYaw = player.getYaw();

            LOGGER.info("Starting baseline rotation measurement ({} frames, {} deg/frame)",
                    MEASUREMENT_FRAMES, ROTATION_SPEED);

            // Warmup: rotate during warmup too so the initial chunks are loaded
            for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                player.setYaw(player.getYaw() + ROTATION_SPEED);
                waitOneFrame(client);
            }

            // Measurement phase with continuous rotation
            sampler.reset();
            long frameStart;
            for (int i = 0; i < MEASUREMENT_FRAMES; i++) {
                frameStart = System.nanoTime();

                // Rotate the camera
                player.setYaw(player.getYaw() + ROTATION_SPEED);

                waitOneFrame(client);
                long frameTime = System.nanoTime() - frameStart;
                sampler.record(frameTime);

                // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                if (i % 60 == 0 || i == MEASUREMENT_FRAMES - 1) {
                    callback.onProgress("rotation", i, MEASUREMENT_FRAMES);
                }
            }

            // Build statistics
            long[] samples = sampler.getSamplesSnapshot();
            FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
            result.addMeasurement("rotation_default", stats);

            LOGGER.info("Baseline rotation complete: avg={} FPS, 1% low={} FPS",
                    String.format("%.1f", stats.avgFps()),
                    String.format("%.1f", stats.p1LowFps()));

        } catch (Exception e) {
            LOGGER.error("Error during baseline rotation phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            // [CODE-REVIEW-FIX] Yaw restore moved to finally block to ensure restoration on exception
            if (!Float.isNaN(originalYaw) && client.player != null) {
                client.player.setYaw(originalYaw);
            }
            // Restore original settings
            adapter.setMaxFps(originalMaxFps);
            adapter.setVsync(originalVsync);
        }

        result.setEndTime(System.currentTimeMillis());
        // [CODE-REVIEW-FIX] Guard against null callback after try-finally
        if (callback != null) {
            callback.onProgress("rotation", MEASUREMENT_FRAMES, MEASUREMENT_FRAMES);
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
