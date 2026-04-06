package com.autotune.benchmark;

import com.autotune.benchmark.hardware.HardwareProfile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the complete results of a benchmark run, including all phase results,
 * hardware profile, and metadata about the benchmark session.
 */
public class BenchmarkResult {

    private final Map<String, PhaseResult> phaseResults;
    private HardwareProfile hardwareProfile;
    private final long benchmarkTimestamp;
    private long totalDurationMs;
    private boolean isComplete;
    private final String benchmarkMode;

    /**
     * Creates a new BenchmarkResult.
     *
     * @param benchmarkMode "full" for all 30 phases, "quick" for the subset
     */
    public BenchmarkResult(String benchmarkMode) {
        this.phaseResults = new LinkedHashMap<>();
        this.benchmarkTimestamp = System.currentTimeMillis();
        this.totalDurationMs = 0;
        this.isComplete = false;
        this.benchmarkMode = benchmarkMode;
    }

    /**
     * Adds or replaces a phase result.
     */
    public void addPhaseResult(String phaseId, PhaseResult result) {
        phaseResults.put(phaseId, result);
    }

    public Map<String, PhaseResult> getPhaseResults() {
        return Collections.unmodifiableMap(phaseResults);
    }

    public PhaseResult getPhaseResult(String phaseId) {
        return phaseResults.get(phaseId);
    }

    public long getBenchmarkTimestamp() {
        return benchmarkTimestamp;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public void setComplete(boolean complete) {
        this.isComplete = complete;
    }

    public String getBenchmarkMode() {
        return benchmarkMode;
    }

    /**
     * Returns the number of phases that have completed (not skipped).
     */
    public int getCompletedPhaseCount() {
        int count = 0;
        for (PhaseResult result : phaseResults.values()) {
            if (!result.isSkipped()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the total number of phase results recorded (including skipped).
     */
    public int getTotalPhaseResultCount() {
        return phaseResults.size();
    }

    @Override
    public String toString() {
        return "BenchmarkResult{mode=" + benchmarkMode
                + ", phases=" + phaseResults.size()
                + ", complete=" + isComplete
                + ", duration=" + totalDurationMs + "ms}";
    }
}
