package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 23: Memory Leak Detection Phase.
 * Records 3000 frames while sampling heap memory usage at three checkpoints:
 * start (frame 0), midpoint (frame 1500), and end (frame 2999).
 * If heap usage grows roughly linearly between checkpoints, it flags a potential
 * memory leak or heap regression. This helps detect settings configurations
 * that cause unbounded memory growth.
 */
public class MemoryLeakPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryLeakPhase.class);

    /**
     * If memory grows by more than this fraction (relative to start usage) between
     * start and end, flag it as suspicious.
     */
    private static final double LEAK_THRESHOLD = 0.20;

    private long heapUsedAtStart;
    private long heapUsedAtMid;
    private long heapUsedAtEnd;
    @Override
    public String getPhaseId() {
        return "phase_23_memory_leak";
    }

    @Override
    public String getPhaseName() {
        return "Memory Leak Detection";
    }

    @Override
    public int getPhaseNumber() {
        return 23;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of("memory_monitor");
    }

    @Override
    public int getFramesPerSubTest() {
        return BenchmarkConstants.FRAMES_STRESS;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        // Force a GC before starting to get a cleaner baseline
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Runtime runtime = Runtime.getRuntime();
        heapUsedAtStart = runtime.totalMemory() - runtime.freeMemory();
        long heapMaxAtStart = runtime.maxMemory();
        heapUsedAtMid = 0;
        heapUsedAtEnd = 0;

        LOGGER.info("Memory leak detection starting: heapUsed={}MB, heapMax={}MB",
                heapUsedAtStart / (1024 * 1024), heapMaxAtStart / (1024 * 1024));
    }

    @Override
    public void onMeasurementFrame(String subTestLabel, int frameIndex, MinecraftClient client) {
        int totalFrames = BenchmarkConstants.FRAMES_STRESS;
        int midpoint = totalFrames / 2;

        if (frameIndex == midpoint) {
            Runtime runtime = Runtime.getRuntime();
            heapUsedAtMid = runtime.totalMemory() - runtime.freeMemory();
            LOGGER.debug("Memory at midpoint (frame {}): heapUsed={}MB",
                    frameIndex, heapUsedAtMid / (1024 * 1024));
        }

        if (frameIndex == totalFrames - 1) {
            Runtime runtime = Runtime.getRuntime();
            heapUsedAtEnd = runtime.totalMemory() - runtime.freeMemory();
            LOGGER.debug("Memory at end (frame {}): heapUsed={}MB",
                    frameIndex, heapUsedAtEnd / (1024 * 1024));
        }
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        // Ensure we have end measurement
        if (heapUsedAtEnd == 0) {
            Runtime runtime = Runtime.getRuntime();
            heapUsedAtEnd = runtime.totalMemory() - runtime.freeMemory();
        }

        long growthStartToMid = heapUsedAtMid - heapUsedAtStart;
        long growthMidToEnd = heapUsedAtEnd - heapUsedAtMid;
        long totalGrowth = heapUsedAtEnd - heapUsedAtStart;

        LOGGER.info("Memory profile: start={}MB, mid={}MB, end={}MB",
                heapUsedAtStart / (1024 * 1024),
                heapUsedAtMid / (1024 * 1024),
                heapUsedAtEnd / (1024 * 1024));
        LOGGER.info("Growth: start->mid={}MB, mid->end={}MB, total={}MB",
                growthStartToMid / (1024 * 1024),
                growthMidToEnd / (1024 * 1024),
                totalGrowth / (1024 * 1024));

        // Check for linear growth pattern (both halves growing similarly)
        boolean linearGrowth = false;
        if (heapUsedAtStart > 0) {
            double growthFraction = (double) totalGrowth / heapUsedAtStart;
            if (growthFraction > LEAK_THRESHOLD) {
                // Check if growth is roughly linear (both halves contribute)
                if (growthStartToMid > 0 && growthMidToEnd > 0) {
                    double ratio = (double) growthMidToEnd / growthStartToMid;
                    // If growth in second half is within 50%-200% of first half, it is linear
                    if (ratio > 0.5 && ratio < 2.0) {
                        linearGrowth = true;
                    }
                }
            }

            // [CODE-REVIEW-FIX] SLF4J does not support {:.1f} format specifiers; use String.format
            if (linearGrowth) {
                LOGGER.warn("Potential memory leak detected: heap grew {}% linearly over {} frames",
                        String.format("%.1f", growthFraction * 100), BenchmarkConstants.FRAMES_STRESS);
            } else if (growthFraction > LEAK_THRESHOLD) {
                LOGGER.warn("Heap grew {}% but growth pattern is not linear (may be one-time allocation)",
                        String.format("%.1f", growthFraction * 100));
            } else {
                LOGGER.info("No significant memory growth detected ({}%)", String.format("%.1f", growthFraction * 100));
            }
        }
    }
}
