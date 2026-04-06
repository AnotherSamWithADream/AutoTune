package com.autotune.benchmark;

import com.autotune.AutoTuneLogger;
import com.autotune.benchmark.phases.BenchmarkPhase;
import com.autotune.config.AutoTuneConfig;
import com.autotune.optimizer.SettingsRegistry;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the full benchmark suite by managing a queue of phases,
 * running them via the BenchmarkPhaseExecutor, and tracking overall progress.
 *
 * The runner operates as a tick-based state machine: call {@link #tick()} each
 * client tick to advance the benchmark. The internal states are:
 * IDLE -> RUNNING_PHASE -> SETTLING -> NEXT_PHASE -> COMPLETE
 */
public class BenchmarkRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkRunner.class);

    /** Settle ticks between phases to let the engine stabilize. */
    private static final int INTER_PHASE_SETTLE_TICKS = 40;

    /**
     * Callback for reporting phase-level progress to the UI layer.
     */
    @FunctionalInterface
    public interface PhaseProgressCallback {
        void onPhaseProgress(String phaseId, String phaseName, String subTest,
                              int completedFrames, int totalFrames,
                              int phaseIndex, int totalPhases);
    }

    private enum RunnerState {
        IDLE,
        RUNNING_PHASE,
        SETTLING,
        NEXT_PHASE,
        COMPLETE
    }

    // Dependencies
    private final PlatformAdapter adapter;

    // All registered phases in order (all 30)
    private final List<BenchmarkPhase> allPhases;

    // Active benchmark state
    private final List<BenchmarkPhase> activePhases;
    private RunnerState state;
    private boolean running;
    private int currentPhaseIndex;
    private int settleTickCounter;
    private BenchmarkResult result;
    private PhaseProgressCallback callback;
    private long benchmarkStartTime;

    private final FrameTimeSampler sampler;

    // Per-phase tick state
    private boolean phaseStarted;
    private PhaseResult currentPhaseResult;

    // [CODE-REVIEW-FIX] H-025: Incremental phase execution state.
    // Instead of executing the entire phase synchronously in one tick (blocking the client thread),
    // we drive sub-test phases incrementally: each tick records frames naturally via the mixin
    // onFrameRendered callback, and we advance sub-tests when enough frames are collected.
    private List<String> currentSubTests;
    private int currentSubTestIndex;
    private int warmupFramesRemaining;
    private int settleFramesRemaining;
    private boolean measurementActive;
    private int framesRecordedForSubTest;
    private int totalFramesCompleted;

    // Flag set by background thread when a Style-B phase finishes
    private volatile boolean styleBComplete;

    private enum SubPhaseState {
        SETUP,       // Setting up the sub-test
        WARMUP,      // Discarding warmup frames
        MEASURING,   // Recording measurement frames
        SETTLE,      // Settle frames between sub-tests
        SUB_COMPLETE // Sub-test done, advance to next
    }
    private SubPhaseState subPhaseState;

    // Quick mode phase numbers (1-indexed)
    private static final Set<Integer> QUICK_MODE_PHASES = new LinkedHashSet<>(
            Arrays.asList(1, 2, 3, 4, 5, 13, 14, 19, 30)
    );

    public BenchmarkRunner(PlatformAdapter adapter, SettingsRegistry settingsRegistry,
                           AutoTuneConfig config) {
        this.adapter = adapter;
        this.allPhases = new ArrayList<>();
        this.activePhases = new ArrayList<>();
        this.state = RunnerState.IDLE;
        this.running = false;
        this.currentPhaseIndex = 0;
        this.sampler = new FrameTimeSampler(BenchmarkConstants.FRAMES_EXTREME);

        registerAllPhases();
    }

    /**
     * Registers all 30 benchmark phases in order. Phase implementations are
     * loaded from the phases package. Phases that cannot be instantiated
     * (e.g., due to missing mod dependencies) are logged and skipped.
     */
    private void registerAllPhases() {
        allPhases.add(new com.autotune.benchmark.phases.BaselineIdlePhase());
        allPhases.add(new com.autotune.benchmark.phases.BaselineRotationPhase());
        allPhases.add(new com.autotune.benchmark.phases.RenderDistanceLadderPhase());
        allPhases.add(new com.autotune.benchmark.phases.GraphicsModePhase());
        allPhases.add(new com.autotune.benchmark.phases.LightingShadowPhase());
        allPhases.add(new com.autotune.benchmark.phases.ParticleStressPhase());
        allPhases.add(new com.autotune.benchmark.phases.EntityStressPhase());
        allPhases.add(new com.autotune.benchmark.phases.BlockEntityStressPhase());
        allPhases.add(new com.autotune.benchmark.phases.TranslucencyPhase());
        allPhases.add(new com.autotune.benchmark.phases.BiomeBlendLadderPhase());
        allPhases.add(new com.autotune.benchmark.phases.ChunkRebuildPhase());
        allPhases.add(new com.autotune.benchmark.phases.ChunkLoadingPhase());
        allPhases.add(new com.autotune.benchmark.phases.MipmapPhase());
        allPhases.add(new com.autotune.benchmark.phases.CloudPhase());
        allPhases.add(new com.autotune.benchmark.phases.VRAMPressurePhase());
        allPhases.add(new com.autotune.benchmark.phases.GCPressurePhase());
        allPhases.add(new com.autotune.benchmark.phases.FullscreenPhase());
        allPhases.add(new com.autotune.benchmark.phases.VSyncPhase());
        allPhases.add(new com.autotune.benchmark.phases.SodiumPhase());
        allPhases.add(new com.autotune.benchmark.phases.IrisShaderPhase());
        allPhases.add(new com.autotune.benchmark.phases.ResolutionScalePhase());
        allPhases.add(new com.autotune.benchmark.phases.SustainedLoadPhase());
        allPhases.add(new com.autotune.benchmark.phases.MemoryLeakPhase());
        allPhases.add(new com.autotune.benchmark.phases.SimulationDistancePhase());
        allPhases.add(new com.autotune.benchmark.phases.FOVPhase());
        allPhases.add(new com.autotune.benchmark.phases.GUIScalePhase());
        allPhases.add(new com.autotune.benchmark.phases.EntityDistancePhase());
        allPhases.add(new com.autotune.benchmark.phases.BrightnessPhase());
        allPhases.add(new com.autotune.benchmark.phases.HUDOverheadPhase());
        allPhases.add(new com.autotune.benchmark.phases.CombinedVerificationPhase());

        LOGGER.info("Benchmark runner initialized, {} phases registered", allPhases.size());
    }

    /**
     * Starts a full benchmark run with all 30 phases.
     */
    public void startFull() {
        if (running) {
            LOGGER.warn("Benchmark already running, ignoring startFull()");
            return;
        }

        activePhases.clear();
        activePhases.addAll(allPhases);
        result = new BenchmarkResult("full");
        beginBenchmark();

        AutoTuneLogger.section("BENCHMARK STARTED (FULL)");
        AutoTuneLogger.info("Full benchmark with {} phases", activePhases.size());
        for (int i = 0; i < activePhases.size(); i++) {
            AutoTuneLogger.debug("  Phase {}: {} ({})", i + 1, activePhases.get(i).getName(), activePhases.get(i).getId());
        }
    }

    /**
     * Starts a quick benchmark run with a subset of phases:
     * Phases 1, 2, 3, 4, 5, 13, 14, 19, 30.
     * Also includes any phase that reports isQuickModePhase() = true.
     */
    public void startQuick() {
        if (running) {
            LOGGER.warn("Benchmark already running, ignoring startQuick()");
            return;
        }

        activePhases.clear();
        for (BenchmarkPhase phase : allPhases) {
            if (phase.isQuickModePhase() || isQuickModePhaseByIndex(allPhases.indexOf(phase))) {
                activePhases.add(phase);
            }
        }

        // If no phases matched (e.g., phases not yet registered), fall back to all
        if (activePhases.isEmpty() && !allPhases.isEmpty()) {
            LOGGER.warn("No quick mode phases found, falling back to full benchmark");
            activePhases.addAll(allPhases);
        }

        result = new BenchmarkResult("quick");
        beginBenchmark();

        LOGGER.info("Quick benchmark started with {} phases", activePhases.size());
    }

    /**
     * Returns whether the benchmark is currently running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Records a single frame time from the render mixin.
     * Called each frame when the benchmark is running.
     *
     * [CODE-REVIEW-FIX] H-025: Also tracks per-sub-test frame count when measuring incrementally.
     *
     * @param frameTimeNanos the frame time in nanoseconds
     */
    public void recordFrameTime(long frameTimeNanos) {
        if (running && sampler != null) {
            sampler.record(frameTimeNanos);
            // Track frames during incremental measurement
            if (measurementActive) {
                framesRecordedForSubTest++;
            }
        }
    }

    /**
     * Returns the 0-based index of the current phase.
     */
    public int getCurrentPhaseIndex() {
        return currentPhaseIndex;
    }

    /**
     * Returns the total number of phases in the active benchmark.
     */
    public int getTotalPhases() {
        return activePhases.size();
    }

    /** Returns the count of all registered phases (not just active ones). */
    public int getAllPhaseCount() {
        return allPhases.size();
    }

    /**
     * Returns the name of the currently running phase, or null.
     */
    public String getCurrentPhaseName() {
        if (!running || currentPhaseIndex >= activePhases.size()) return null;
        return activePhases.get(currentPhaseIndex).getName();
    }

    /**
     * Returns overall progress as a percentage (0.0 to 100.0).
     */
    public float getProgressPercent() {
        if (activePhases.isEmpty()) return 0f;
        if (!running && state == RunnerState.COMPLETE) return 100f;

        // Compute based on completed phases + fractional current phase
        float baseProgress = (float) currentPhaseIndex / activePhases.size() * 100f;

        // Add fractional progress within current phase if running
        if (running && currentPhaseIndex < activePhases.size()) {
            BenchmarkPhase current = activePhases.get(currentPhaseIndex);
            int estimatedFrames = current.getEstimatedFrames();
            if (estimatedFrames > 0 && sampler.getCount() > 0) {
                float phaseProgress = Math.min(1.0f, (float) sampler.getCount() / estimatedFrames);
                baseProgress += phaseProgress / activePhases.size() * 100f;
            }
        }

        return Math.min(100f, baseProgress);
    }

    /**
     * Called each client tick to advance the benchmark state machine.
     * Must be called from the render thread.
     */
    private int tickDiagCounter;
    public void tick() {
        if (!running) return;
        tickDiagCounter++;
        if (tickDiagCounter % 100 == 1) {
            AutoTuneLogger.debug("BenchmarkRunner.tick() state={}, phase={}/{}, subPhase={}, framesRecorded={}, styleBComplete={}",
                state, currentPhaseIndex, activePhases.size(),
                subPhaseState, framesRecordedForSubTest, styleBComplete);
        }

        switch (state) {
            case RUNNING_PHASE -> tickRunningPhase();
            case SETTLING -> tickSettling();
            case NEXT_PHASE -> tickNextPhase();
            case COMPLETE -> {
                running = false;
            }
            default -> { }
        }
    }

    // ----- Internal state machine -----

    private void beginBenchmark() {
        benchmarkStartTime = System.currentTimeMillis();
        currentPhaseIndex = 0;
        running = true;
        phaseStarted = false;
        currentPhaseResult = null;

        // Set hardware profile from the mod instance if available
        state = RunnerState.RUNNING_PHASE;
    }

    // [CODE-REVIEW-FIX] H-025: Rewritten to be incremental instead of executing the entire phase
    // synchronously in one tick. The state machine now drives sub-tests one tick at a time:
    // each tick processes warmup/measurement/settle frames naturally via the mixin onFrameRendered
    // callback. This prevents blocking the client tick thread for the duration of an entire phase.
    private void tickRunningPhase() {
        if (currentPhaseIndex >= activePhases.size()) {
            completeBenchmark();
            return;
        }

        BenchmarkPhase phase = activePhases.get(currentPhaseIndex);
        MinecraftClient client = MinecraftClient.getInstance();

        // (Style-B phases now run synchronously — no background thread check needed)

        if (!phaseStarted) {
            // Check if phase should be skipped
            String skipReason = phase.shouldSkip();
            if (skipReason != null) {
                LOGGER.info("Skipping phase {}: {}", phase.getId(), skipReason);
                currentPhaseResult = PhaseResult.skipped(phase.getId(), phase.getName(), skipReason);
                result.addPhaseResult(phase.getId(), currentPhaseResult);
                state = RunnerState.SETTLING;
                settleTickCounter = 0;
                phaseStarted = true;
                return;
            }

            // Initialize phase execution
            AutoTuneLogger.section("PHASE " + (currentPhaseIndex + 1) + "/" + activePhases.size() + ": " + phase.getName());
            AutoTuneLogger.info("Phase ID: {}, estimated frames: {}", phase.getId(), phase.getEstimatedFrames());
            currentSubTests = phase.getSubTestLabels();
            currentSubTestIndex = 0;
            totalFramesCompleted = 0;
            currentPhaseResult = new PhaseResult(phase.getId(), phase.getName(), System.currentTimeMillis());

            // Uncap FPS for measurement
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            phaseStarted = true;

            if (currentSubTests == null || currentSubTests.isEmpty()) {
                // Style B phase: runs synchronously on the render thread.
                // This blocks the game briefly during each phase (a few seconds per phase),
                // which is acceptable since the user explicitly started the benchmark.
                // Running on the render thread is required because phases may:
                // - Call OpenGL functions (VRAMMonitor, FullscreenPhase)
                // - Modify entities (EntityStressPhase)
                // - Access render-thread-only APIs (DebugHud, chunk invalidation)
                AutoTuneLogger.info("Phase {} is Style-B, executing on render thread...", phase.getId());
                measurementActive = true;
                try {
                    PhaseResult execResult = phase.execute(client, adapter, sampler, (subTest, completed, total) -> {
                        AutoTuneLogger.debug("  Phase {}: sub={} {}/{}", phase.getId(), subTest, completed, total);
                    });
                    if (execResult != null) {
                        currentPhaseResult = execResult;
                    }
                } catch (Exception e) {
                    AutoTuneLogger.error("Phase {} failed: {}", phase.getId(), e.toString());
                    currentPhaseResult.setNotes("Error: " + e.toString());
                }
                measurementActive = false;
                finalizeCurrentPhase(phase, client);
                return;
            }

            // Begin first sub-test
            subPhaseState = SubPhaseState.SETUP;
        }

        // Drive incremental sub-test state machine
        if (currentSubTests != null && !currentSubTests.isEmpty()) {
            tickSubTestStateMachine(phase, client);
        }
    }

    /**
     * [CODE-REVIEW-FIX] H-025: Incremental sub-test state machine. Each call to this method
     * processes one tick's worth of work instead of blocking for the entire phase duration.
     */
    private void tickSubTestStateMachine(BenchmarkPhase phase, MinecraftClient client) {
        if (currentSubTestIndex >= currentSubTests.size()) {
            // All sub-tests done
            finalizeCurrentPhase(phase, client);
            return;
        }

        String subTestLabel = currentSubTests.get(currentSubTestIndex);
        int framesPerSubTest = phase.getFramesPerSubTest();
        int totalFrames = phase.getEstimatedFrames();

        switch (subPhaseState) {
            case SETUP -> {
                // Set up the current sub-test
                AutoTuneLogger.debug("  Sub-test {}/{}: '{}' - setup", currentSubTestIndex + 1, currentSubTests.size(), subTestLabel);
                phase.setupSubTest(subTestLabel, client, adapter);
                warmupFramesRemaining = BenchmarkConstants.WARMUP_FRAMES;
                measurementActive = false;
                framesRecordedForSubTest = 0;
                subPhaseState = SubPhaseState.WARMUP;
            }

            case WARMUP -> {
                // Warmup frames are consumed naturally via mixin; count down ticks as proxy
                warmupFramesRemaining--;
                if (warmupFramesRemaining <= 0) {
                    // Start measurement
                    sampler.reset();
                    measurementActive = true;
                    framesRecordedForSubTest = 0;
                    subPhaseState = SubPhaseState.MEASURING;
                }
            }

            case MEASURING -> {
                // Frames are recorded via recordFrameTime() from the mixin callback.
                // Check if we have enough frames.
                if (callback != null && framesRecordedForSubTest % 30 == 0) {
                    callback.onPhaseProgress(phase.getId(), phase.getName(), subTestLabel,
                            totalFramesCompleted + framesRecordedForSubTest, totalFrames,
                            currentPhaseIndex, activePhases.size());
                }

                // Notify the phase of measurement frame
                phase.onMeasurementFrame(subTestLabel, framesRecordedForSubTest, client);

                if (framesRecordedForSubTest >= framesPerSubTest) {
                    // Measurement complete for this sub-test
                    measurementActive = false;
                    long[] snapshot = sampler.getSamplesSnapshot();
                    FrameTimeStatistics stats = FrameTimeStatistics.from(snapshot);
                    currentPhaseResult.addMeasurement(subTestLabel, stats);
                    totalFramesCompleted += framesRecordedForSubTest;

                    AutoTuneLogger.info("  Sub-test '{}' complete: avg={} FPS, 1%low={} FPS, p99={}ms",
                            subTestLabel, String.format("%.1f", stats.avgFps()),
                            String.format("%.1f", stats.p1LowFps()),
                            String.format("%.1f", stats.p99FrameTimeMs()));

                    settleFramesRemaining = BenchmarkConstants.SETTLE_FRAMES;
                    subPhaseState = SubPhaseState.SETTLE;
                }
            }

            case SETTLE -> {
                // Settle between sub-tests
                settleFramesRemaining--;
                if (settleFramesRemaining <= 0) {
                    subPhaseState = SubPhaseState.SUB_COMPLETE;
                }
            }

            case SUB_COMPLETE -> {
                // Advance to next sub-test
                currentSubTestIndex++;
                if (currentSubTestIndex >= currentSubTests.size()) {
                    finalizeCurrentPhase(phase, client);
                } else {
                    subPhaseState = SubPhaseState.SETUP;
                }
            }
        }
    }

    /**
     * [CODE-REVIEW-FIX] H-025: Finalizes the current phase and transitions to settling.
     */
    private void finalizeCurrentPhase(BenchmarkPhase phase, MinecraftClient client) {
        measurementActive = false;
        phase.teardown(client, adapter);
        currentPhaseResult.setEndTime(System.currentTimeMillis());
        result.addPhaseResult(phase.getId(), currentPhaseResult);

        AutoTuneLogger.info("Phase {} complete ({} measurements)", phase.getId(),
                currentPhaseResult.getMeasurements().size());

        state = RunnerState.SETTLING;
        settleTickCounter = 0;
    }

    private void tickSettling() {
        settleTickCounter++;
        if (settleTickCounter >= INTER_PHASE_SETTLE_TICKS) {
            state = RunnerState.NEXT_PHASE;
        }
    }

    private void tickNextPhase() {
        currentPhaseIndex++;
        phaseStarted = false;
        currentPhaseResult = null;

        if (currentPhaseIndex >= activePhases.size()) {
            completeBenchmark();
        } else {
            state = RunnerState.RUNNING_PHASE;
        }
    }

    private void completeBenchmark() {
        long totalDuration = System.currentTimeMillis() - benchmarkStartTime;
        result.setTotalDurationMs(totalDuration);
        result.setComplete(true);

        state = RunnerState.COMPLETE;
        running = false;

        AutoTuneLogger.section("BENCHMARK COMPLETE");
        AutoTuneLogger.info("Total: {} phases in {}ms (mode={})",
                result.getTotalPhaseResultCount(), totalDuration, result.getBenchmarkMode());
        AutoTuneLogger.info("Results saved. Check config/autotune/autotune-debug.log for full details.");
    }

    /**
     * Checks if the given 0-based phase index corresponds to a quick mode phase.
     * Quick mode phases are: 1, 2, 3, 4, 5, 13, 14, 19, 30 (1-indexed).
     */
    private boolean isQuickModePhaseByIndex(int zeroBasedIndex) {
        int oneBasedIndex = zeroBasedIndex + 1;
        return QUICK_MODE_PHASES.contains(oneBasedIndex);
    }

}
