package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.benchmark.FrameTimeSampler;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;

import java.util.Collections;
import java.util.List;

/**
 * Interface for a single benchmark phase. Each phase tests one or more
 * settings or scenarios by running sub-tests and recording frame times.
 *
 * <p>There are two implementation styles:
 *
 * <h3>Style A: Sub-test driven (override the "phase-specific" group)</h3>
 * Override {@link #getPhaseId()}, {@link #getPhaseName()}, {@link #getPhaseNumber()},
 * {@link #getSubTestLabels()}, {@link #getFramesPerSubTest()},
 * {@link #setupSubTest(String, MinecraftClient, PlatformAdapter)}, and
 * {@link #teardown(MinecraftClient, PlatformAdapter)}. The default
 * {@link #execute} loops over sub-tests automatically.
 *
 * <h3>Style B: Custom execute (override the "high-level" group)</h3>
 * Override {@link #getId()}, {@link #getName()}, {@link #getDescription()},
 * {@link #getEstimatedFrames()}, {@link #isQuickModePhase()}, and
 * {@link #execute(MinecraftClient, PlatformAdapter, FrameTimeSampler, ProgressCallback)}.
 */
public interface BenchmarkPhase {

    // ====================================================================
    // Phase-specific methods (Style A). Default to delegating to Style B.
    // ====================================================================

    /**
     * Unique identifier for this phase (e.g., "phase_01_baseline_idle").
     * Defaults to delegating to {@link #getId()}.
     */
    default String getPhaseId() {
        return getId();
    }

    /**
     * Human-readable display name (e.g., "Baseline Idle").
     * Defaults to delegating to {@link #getName()}.
     */
    default String getPhaseName() {
        return getName();
    }

    /**
     * Numeric phase order index. Defaults to 0.
     */
    default int getPhaseNumber() {
        return 0;
    }

    /**
     * Ordered list of sub-test labels for this phase.
     * Defaults to an empty list.
     */
    default List<String> getSubTestLabels() {
        return Collections.emptyList();
    }

    /**
     * Number of measurement frames to record per sub-test.
     * Defaults to {@link BenchmarkConstants#FRAMES_SHORT}.
     */
    default int getFramesPerSubTest() {
        return BenchmarkConstants.FRAMES_SHORT;
    }

    /**
     * Configure the game for a specific sub-test. Called before measurement begins.
     * Default is a no-op.
     */
    default void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        // no-op by default
    }

    /**
     * Restore all settings modified during execution.
     * Called after all sub-tests complete. Default is a no-op.
     */
    default void teardown(MinecraftClient client, PlatformAdapter adapter) {
        // no-op by default
    }

    /**
     * Returns a non-null reason string if this phase should be skipped,
     * or null if the phase should run normally. Checked before execution.
     */
    default String shouldSkip() {
        return null;
    }

    /**
     * Called once per measurement frame during recording.
     * Allows phases to track per-frame data (e.g., memory, GC stats).
     * Default is a no-op.
     *
     * @param subTestLabel the current sub-test label
     * @param frameIndex   the zero-based frame index within the sub-test
     * @param client       the Minecraft client instance
     */
    default void onMeasurementFrame(String subTestLabel, int frameIndex, MinecraftClient client) {
        // no-op by default
    }

    // ====================================================================
    // High-level methods (Style B). Default to delegating to Style A.
    // ====================================================================

    /**
     * Unique identifier for this phase.
     * // [CODE-REVIEW-FIX] Broke mutual recursion with getPhaseId(). Was: return getPhaseId()
     * // which causes StackOverflowError if neither getId() nor getPhaseId() is overridden.
     */
    default String getId() {
        return "unknown_phase";
    }

    /**
     * Human-readable display name.
     * // [CODE-REVIEW-FIX] Broke mutual recursion with getPhaseName(). Was: return getPhaseName()
     * // which causes StackOverflowError if neither getName() nor getPhaseName() is overridden.
     */
    default String getName() {
        return "Unknown Phase";
    }

    /**
     * Brief description of what this phase measures.
     * Defaults to the phase name.
     */
    default String getDescription() {
        return getName();
    }

    /**
     * Estimated total number of measurement frames across all sub-tests.
     */
    default int getEstimatedFrames() {
        List<String> subTests = getSubTestLabels();
        if (subTests.isEmpty()) {
            return 0;
        }
        return subTests.size() * getFramesPerSubTest();
    }

    /**
     * Whether this phase should be included in quick mode benchmarks.
     * Defaults to false.
     */
    default boolean isQuickModePhase() {
        return false;
    }

    /**
     * Executes the phase: configures settings, records frame times, and
     * returns a PhaseResult containing all measurements.
     *
     * <p>The default implementation iterates over sub-tests from
     * {@link #getSubTestLabels()}, calling {@link #setupSubTest} for each,
     * measuring frames, and finally calling {@link #teardown}.
     */
    default PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                                FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(getId(), getName(), System.currentTimeMillis());
        List<String> subTests = getSubTestLabels();
        int framesPerSubTest = getFramesPerSubTest();
        int totalFrames = getEstimatedFrames();
        int completedFrames = 0;

        // [CODE-REVIEW-FIX] Uncap FPS and disable VSync before measurement so Style A phases
        // produce accurate data instead of being capped by VSync (~60 FPS ceiling).
        // Affects ~14 phases: GCPressure, Fullscreen, Sustained, Simulation, FOV, GUI,
        // EntityDistance, Brightness, HUD, CombinedVerification, IrisShader, ResolutionScale, etc.
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            for (String subTest : subTests) {
                setupSubTest(subTest, client, adapter);

                // Warmup frames (discarded)
                for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                    // [CODE-REVIEW-FIX] Restore interrupt flag so callers can detect cancellation
                    try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }

                // Measurement frames
                sampler.reset();
                for (int i = 0; i < framesPerSubTest; i++) {
                    long frameStart = System.nanoTime();
                    // [CODE-REVIEW-FIX] Restore interrupt flag so callers can detect cancellation
                    try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    long frameEnd = System.nanoTime();
                    sampler.record(frameEnd - frameStart);
                    completedFrames++;
                    if (callback != null && (i % 30 == 0 || i == framesPerSubTest - 1)) {
                        callback.onProgress(subTest, completedFrames, totalFrames);
                    }
                }

                // Capture statistics for this sub-test
                long[] snapshot = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(snapshot);
                result.addMeasurement(subTest, stats);

                // Settle frames between sub-tests
                for (int i = 0; i < BenchmarkConstants.SETTLE_FRAMES; i++) {
                    // [CODE-REVIEW-FIX] Restore interrupt flag so callers can detect cancellation
                    try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
        } finally {
            teardown(client, adapter);
            // [CODE-REVIEW-FIX] Restore original FPS cap and VSync after measurement
            adapter.setMaxFps(originalMaxFps);
            adapter.setVsync(originalVsync);
        }

        result.setEndTime(System.currentTimeMillis());
        return result;
    }

    /**
     * Callback interface for reporting phase progress.
     */
    @FunctionalInterface
    interface ProgressCallback {
        /**
         * Called periodically during phase execution to report progress.
         *
         * @param subTest         the current sub-test label
         * @param completedFrames total measurement frames completed so far in this phase
         * @param totalFrames     estimated total measurement frames for the phase
         */
        void onProgress(String subTest, int completedFrames, int totalFrames);
    }
}
