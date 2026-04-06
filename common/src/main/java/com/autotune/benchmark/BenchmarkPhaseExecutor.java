package com.autotune.benchmark;

import com.autotune.benchmark.phases.BenchmarkPhase;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes a single benchmark phase with error handling, timing,
 * and progress reporting. Delegates to the phase's own execute() method
 * which handles warmup, settle time, and measurement recording internally.
 */
public class BenchmarkPhaseExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkPhaseExecutor.class);

    /**
     * Context passed to the executor containing all necessary dependencies.
     */
    public record BenchmarkContext(MinecraftClient client, PlatformAdapter adapter,
                                    FrameTimeSampler sampler, PhaseProgressCallback callback) {
    }

    /**
     * Callback for reporting progress across the entire benchmark run.
     */
    @FunctionalInterface
    public interface PhaseProgressCallback {
        /**
         * Called to report progress during phase execution.
         *
         * @param phaseId         the phase identifier
         * @param phaseName       the human-readable phase name
         * @param subTest         the current sub-test label
         * @param completedFrames frames completed in this phase
         * @param totalFrames     estimated total frames for this phase
         */
        void onProgress(String phaseId, String phaseName, String subTest,
                         int completedFrames, int totalFrames);
    }

    /**
     * Executes a single benchmark phase using the provided context.
     * Handles error recovery and timing.
     *
     * @param phase   the phase to execute
     * @param context the benchmark context with dependencies
     * @return the phase result, possibly a skipped result if an error occurred
     */
    public PhaseResult execute(BenchmarkPhase phase, BenchmarkContext context) {
        LOGGER.info("Executing phase: {} ({})", phase.getName(), phase.getId());
        long startTime = System.currentTimeMillis();

        context.sampler().reset();

        // Create a bridge callback from the phase's callback to ours
        BenchmarkPhase.ProgressCallback phaseCallback = (subTest, completedFrames, totalFrames) -> {
            if (context.callback() != null) {
                try {
                    context.callback().onProgress(
                            phase.getId(), phase.getName(),
                            subTest, completedFrames, totalFrames);
                } catch (Exception e) {
                    LOGGER.debug("Progress callback error", e);
                }
            }
        };

        PhaseResult result;
        try {
            result = phase.execute(
                    context.client(),
                    context.adapter(),
                    context.sampler(),
                    phaseCallback);
        } catch (Exception e) {
            LOGGER.error("Phase {} failed with exception", phase.getId(), e);
            result = PhaseResult.skipped(phase.getId(), phase.getName(),
                    "Error: " + e.toString());
        }

        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Phase {} completed in {}ms ({} measurements)",
                phase.getId(), elapsed, result.getMeasurements().size());

        return result;
    }
}
