package com.autotune.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrameTimeStatisticsTest {

    private static final long NS_16_6MS = 16_666_666L;  // ~60 FPS
    private static final double TOLERANCE_FPS = 0.5;
    private static final double TOLERANCE_MS = 0.01;

    // -----------------------------------------------------------------------
    // Single frame
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Single frame at 16.6ms produces ~60 FPS across all metrics")
    void testFromSingleFrame() {
        long[] frames = { NS_16_6MS };
        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);

        assertEquals(1, stats.sampleCount());
        assertEquals(60.0, stats.avgFps(), TOLERANCE_FPS);
        assertEquals(60.0, stats.medianFps(), TOLERANCE_FPS);
        assertEquals(60.0, stats.p1LowFps(), TOLERANCE_FPS,
                "With 1 frame, 1% low is the only frame");
        assertEquals(60.0, stats.p01LowFps(), TOLERANCE_FPS,
                "With 1 frame, 0.1% low is the only frame");
        assertEquals(16.666, stats.minFrameTimeMs(), TOLERANCE_MS);
        assertEquals(16.666, stats.maxFrameTimeMs(), TOLERANCE_MS);
        assertEquals(0.0, stats.stdDevMs(), TOLERANCE_MS,
                "Standard deviation of a single sample should be 0");
        assertEquals(0, stats.stutterCount());
    }

    // -----------------------------------------------------------------------
    // Steady 60 FPS (100 frames)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("100 identical frames at 16.6ms: avgFps=60, stdDev=0")
    void testFromSteady60Fps() {
        long[] frames = new long[100];
        for (int i = 0; i < 100; i++) {
            frames[i] = NS_16_6MS;
        }

        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);

        assertEquals(100, stats.sampleCount());
        assertEquals(60.0, stats.avgFps(), TOLERANCE_FPS);
        assertEquals(60.0, stats.medianFps(), TOLERANCE_FPS);
        assertEquals(0.0, stats.stdDevMs(), TOLERANCE_MS,
                "All frames identical => stdDev = 0");
        assertEquals(0, stats.stutterCount(),
                "No frame exceeds 3x average when all are identical");
        // Trend slope should be ~0 for constant data
        assertEquals(0.0, stats.trendSlope(), 1.0);
    }

    // -----------------------------------------------------------------------
    // Mixed frame times
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Mixed fast/slow frames: verify median, p1Low, p01Low")
    void testFromMixedFrameTimes() {
        // 90 frames at 10ms (100 FPS), 10 frames at 33.3ms (30 FPS)
        long fastFrame = 10_000_000L;   // 10ms -> 100 FPS
        long slowFrame = 33_333_333L;   // 33.3ms -> 30 FPS

        long[] frames = new long[100];
        for (int i = 0; i < 90; i++) {
            frames[i] = fastFrame;
        }
        for (int i = 90; i < 100; i++) {
            frames[i] = slowFrame;
        }

        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);

        assertEquals(100, stats.sampleCount());

        // Median: with 90 fast and 10 slow (sorted), median is average of index 49 and 50 -> both fast
        assertEquals(100.0, stats.medianFps(), TOLERANCE_FPS,
                "Median should be ~100 FPS (fast frame)");

        // 1% low: worst 1% of 100 = 1 frame -> the slowest frame at 33.3ms -> 30 FPS
        assertEquals(30.0, stats.p1LowFps(), TOLERANCE_FPS);

        // 0.1% low: worst 0.1% of 100 = max(1, 0) = 1 frame -> also slowest
        assertEquals(30.0, stats.p01LowFps(), TOLERANCE_FPS);

        // Min and max
        assertEquals(10.0, stats.minFrameTimeMs(), TOLERANCE_MS);
        assertEquals(33.333, stats.maxFrameTimeMs(), TOLERANCE_MS);

        // StdDev should be > 0 since we have different frame times
        assertTrue(stats.stdDevMs() > 0,
                "Standard deviation should be positive for mixed data");
    }

    // -----------------------------------------------------------------------
    // Stutter counting
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Frames exceeding 3x average are counted as stutters")
    void testStutterCounting() {
        // 95 frames at 16.6ms, 5 frames at 100ms (6x average)
        // Average will be approximately (95*16.6 + 5*100) / 100 = 20.77ms
        // Threshold = 20.77 * 3 = 62.3ms -> the 100ms frames are stutters
        long stutterFrame = 100_000_000L; // 100ms

        long[] frames = new long[100];
        for (int i = 0; i < 95; i++) {
            frames[i] = NS_16_6MS;
        }
        for (int i = 95; i < 100; i++) {
            frames[i] = stutterFrame;
        }

        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);

        assertEquals(5, stats.stutterCount(),
                "All 5 frames at 100ms should exceed 3x the average");
    }

    @Test
    @DisplayName("No stutters when all frames are identical")
    void testNoStutters() {
        long[] frames = new long[200];
        for (int i = 0; i < 200; i++) {
            frames[i] = NS_16_6MS;
        }

        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);
        assertEquals(0, stats.stutterCount());
    }

    // -----------------------------------------------------------------------
    // Min/max frame time
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Min and max frame times are correctly detected from mixed data")
    void testMinMaxFrameTime() {
        long[] frames = {
            5_000_000L,   // 5ms  - minimum
            10_000_000L,  // 10ms
            15_000_000L,  // 15ms
            20_000_000L,  // 20ms
            80_000_000L   // 80ms - maximum
        };

        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);

        assertEquals(5.0, stats.minFrameTimeMs(), TOLERANCE_MS);
        assertEquals(80.0, stats.maxFrameTimeMs(), TOLERANCE_MS);
    }

    // -----------------------------------------------------------------------
    // Empty array
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Empty array produces zeroed-out statistics")
    void testEmptyArray() {
        FrameTimeStatistics stats = FrameTimeStatistics.from(new long[0]);

        assertEquals(0, stats.sampleCount());
        assertEquals(0.0, stats.avgFps());
        assertEquals(0.0, stats.medianFps());
        assertEquals(0.0, stats.p1LowFps());
        assertEquals(0.0, stats.p01LowFps());
        assertEquals(0.0, stats.p99FrameTimeMs());
        assertEquals(0.0, stats.maxFrameTimeMs());
        assertEquals(0.0, stats.minFrameTimeMs());
        assertEquals(0.0, stats.stdDevMs());
        assertEquals(0.0, stats.totalDurationMs());
        assertEquals(0, stats.stutterCount());
        assertEquals(0.0, stats.trendSlope());
    }

    @Test
    @DisplayName("Null array produces zeroed-out statistics")
    void testNullArray() {
        FrameTimeStatistics stats = FrameTimeStatistics.from(null);

        assertEquals(0, stats.sampleCount());
        assertEquals(0.0, stats.avgFps());
    }

    // -----------------------------------------------------------------------
    // Total duration
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Total duration is the sum of all frame times in milliseconds")
    void testTotalDuration() {
        // 10 frames at 10ms = 100ms total
        long[] frames = new long[10];
        for (int i = 0; i < 10; i++) {
            frames[i] = 10_000_000L;
        }

        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);
        assertEquals(100.0, stats.totalDurationMs(), TOLERANCE_MS);
    }

    // -----------------------------------------------------------------------
    // 99th percentile
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("99th percentile frame time is correct for a known distribution")
    void testP99FrameTime() {
        // 99 frames at 10ms, 1 frame at 100ms
        long[] frames = new long[100];
        for (int i = 0; i < 99; i++) {
            frames[i] = 10_000_000L;
        }
        frames[99] = 100_000_000L;

        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);

        // p99Index = ceil(100 * 0.99) - 1 = 99 - 1 = 98 (0-indexed)
        // sorted[98] is the last 10ms frame
        assertEquals(10.0, stats.p99FrameTimeMs(), TOLERANCE_MS,
                "99th percentile should be 10ms (the boundary frame)");
    }

    // -----------------------------------------------------------------------
    // Trend slope
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Increasing frame times produce a positive trend slope")
    void testPositiveTrendSlope() {
        // Frame times increase linearly
        long[] frames = new long[50];
        for (int i = 0; i < 50; i++) {
            frames[i] = 10_000_000L + (i * 200_000L);
        }

        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);
        assertTrue(stats.trendSlope() > 0,
                "Linearly increasing frame times should produce positive slope");
    }

    @Test
    @DisplayName("Single frame produces zero trend slope")
    void testTrendSlopeSingleFrame() {
        long[] frames = { NS_16_6MS };
        FrameTimeStatistics stats = FrameTimeStatistics.from(frames);
        assertEquals(0.0, stats.trendSlope());
    }
}
