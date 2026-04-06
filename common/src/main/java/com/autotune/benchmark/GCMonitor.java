package com.autotune.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * Monitors garbage collection activity during benchmark phases.
 * Tracks GC pause counts and total pause time to detect GC pressure
 * that may affect benchmark measurements.
 */
public class GCMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GCMonitor.class);

    private long baselineGcCount;
    private long baselineGcTimeMs;

    public GCMonitor() {
        reset();
    }

    /**
     * Resets the monitor by capturing the current GC state as the baseline.
     * All subsequent measurements will be relative to this baseline.
     */
    public void reset() {
        long totalCount = 0;
        long totalTimeMs = 0;

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean bean : gcBeans) {
            long count = bean.getCollectionCount();
            long time = bean.getCollectionTime();
            if (count >= 0) totalCount += count;
            if (time >= 0) totalTimeMs += time;
        }

        this.baselineGcCount = totalCount;
        this.baselineGcTimeMs = totalTimeMs;

        LOGGER.debug("GCMonitor reset: baseline count={}, time={}ms", totalCount, totalTimeMs);
    }

    /**
     * Returns the number of GC pauses since the last reset.
     */
    public long getGcPauseCount() {
        long totalCount = 0;
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean bean : gcBeans) {
            long count = bean.getCollectionCount();
            if (count >= 0) totalCount += count;
        }
        return totalCount - baselineGcCount;
    }

    /**
     * Returns the total GC pause time in milliseconds since the last reset.
     */
    public long getGcPauseTimeMs() {
        long totalTimeMs = 0;
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean bean : gcBeans) {
            long time = bean.getCollectionTime();
            if (time >= 0) totalTimeMs += time;
        }
        return totalTimeMs - baselineGcTimeMs;
    }

    /**
     * Returns a snapshot of the current GC state relative to the baseline.
     */
    public GcSnapshot snapshot() {
        return new GcSnapshot(getGcPauseCount(), getGcPauseTimeMs());
    }

    /**
     * Immutable snapshot of GC metrics at a point in time.
     */
    public record GcSnapshot(long pauseCount, long pauseTimeMs) {
        public double averagePauseMs() {
            if (pauseCount <= 0) return 0.0;
            return (double) pauseTimeMs / pauseCount;
        }
    }
}
