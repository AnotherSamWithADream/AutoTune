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
 * Phase 6: Particle Stress
 * Tests ALL (0), DECREASED (1), and MINIMAL (2) particle modes at 300 frames each.
 * Particle rendering can be significant in areas with water, lava, torches,
 * or during weather effects.
 */
public class ParticleStressPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParticleStressPhase.class);
    private static final String PHASE_ID = "phase_06_particle_stress";
    private static final String PHASE_NAME = "Particle Stress";
    private static final int FRAMES_PER_LEVEL = BenchmarkConstants.FRAMES_SHORT;

    /** Particle levels: 0=ALL, 1=DECREASED, 2=MINIMAL. */
    private static final int[] PARTICLE_LEVELS = {0, 1, 2};
    private static final String[] LEVEL_LABELS = {"ALL", "DECREASED", "MINIMAL"};

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
        return "Tests All, Decreased, and Minimal particle modes to measure particle rendering overhead.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_LEVEL * PARTICLE_LEVELS.length;
    }

    @Override
    public PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                               FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(PHASE_ID, PHASE_NAME, System.currentTimeMillis());

        // Save original settings
        int originalParticles = adapter.getParticles();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            for (int p = 0; p < PARTICLE_LEVELS.length; p++) {
                int level = PARTICLE_LEVELS[p];
                String label = LEVEL_LABELS[p];

                LOGGER.info("Testing particles: {} (level={}, {} frames)", label, level, FRAMES_PER_LEVEL);

                // Apply particle setting
                adapter.setParticles(level);

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

                LOGGER.info("  {}: avg={} FPS, 1% low={} FPS", label,
                        String.format("%.1f", stats.avgFps()),
                        String.format("%.1f", stats.p1LowFps()));

                completedFrames += FRAMES_PER_LEVEL;
            }

        } catch (Exception e) {
            LOGGER.error("Error during particle stress phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setParticles(originalParticles);
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
