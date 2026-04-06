package com.autotune.benchmark.hardware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;

public final class MemoryDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryDetector.class);

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private MemoryDetector() {}

    /**
     * Detects total physical RAM in megabytes using com.sun.management.OperatingSystemMXBean.
     * Returns -1 if detection fails.
     */
    public static int detectTotalRam() {
        try {
            java.lang.management.OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
            if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                long totalBytes = sunBean.getTotalMemorySize();
                return (int) (totalBytes / BYTES_PER_MB);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to detect total RAM via com.sun.management", e);
        }
        return -1;
    }

    /**
     * Detects available (free) physical RAM in megabytes.
     * Returns -1 if detection fails.
     */
    public static int detectAvailableRam() {
        try {
            java.lang.management.OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
            if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                long freeBytes = sunBean.getFreeMemorySize();
                return (int) (freeBytes / BYTES_PER_MB);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to detect available RAM via com.sun.management", e);
        }
        return -1;
    }

    /**
     * Returns the maximum heap size in megabytes that the JVM will attempt to use.
     */
    public static long detectMaxHeap() {
        return Runtime.getRuntime().maxMemory() / BYTES_PER_MB;
    }

    /**
     * Returns the current total memory allocated to the JVM heap in megabytes.
     * This is the amount currently reserved from the OS, not the max.
     */
    public static long detectAllocatedHeap() {
        return Runtime.getRuntime().totalMemory() / BYTES_PER_MB;
    }

}
