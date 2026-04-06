package com.autotune.benchmark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of a single benchmark phase execution.
 * Contains measurements for each sub-test within the phase,
 * timing information, and metadata about whether the phase was skipped.
 */
public class PhaseResult {

    private final String phaseId;
    private final String phaseName;
    private final Map<String, FrameTimeStatistics> measurements;
    private final long startTime;
    private long endTime;
    private boolean skipped;
    private String skipReason;
    private String notes;

    public PhaseResult(String phaseId, String phaseName, long startTime) {
        this.phaseId = phaseId;
        this.phaseName = phaseName;
        this.measurements = new LinkedHashMap<>();
        this.startTime = startTime;
        this.endTime = startTime;
        this.skipped = false;
        this.skipReason = null;
        this.notes = null;
    }

    /**
     * Creates a PhaseResult representing a skipped phase.
     */
    public static PhaseResult skipped(String phaseId, String phaseName, String reason) {
        PhaseResult result = new PhaseResult(phaseId, phaseName, System.currentTimeMillis());
        result.skipped = true;
        result.skipReason = reason;
        result.endTime = result.startTime;
        return result;
    }

    /**
     * Adds a measurement for a sub-test within this phase.
     *
     * @param label the sub-test label (e.g., "rd_12", "FANCY")
     * @param stats the computed frame time statistics
     */
    public void addMeasurement(String label, FrameTimeStatistics stats) {
        measurements.put(label, stats);
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public Map<String, FrameTimeStatistics> getMeasurements() {
        return Collections.unmodifiableMap(measurements);
    }

    public long getDurationMs() {
        return endTime - startTime;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        if (skipped) {
            return "PhaseResult{" + phaseId + " SKIPPED: " + skipReason + "}";
        }
        return "PhaseResult{" + phaseId + ", measurements=" + measurements.size()
                + ", duration=" + getDurationMs() + "ms}";
    }
}
