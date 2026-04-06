package com.autotune.live;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RollingFrameBufferTest {

    private static final long NS_16_6MS = 16_666_666L;  // ~60 FPS
    private static final long NS_1MS = 1_000_000L;       // ~1000 FPS
    private static final double TOLERANCE = 0.5;

    private RollingFrameBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new RollingFrameBuffer(300);
    }

    // -----------------------------------------------------------------------
    // Empty buffer
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getAverageFps returns 0 on empty buffer")
    void testEmptyBufferAverageFps() {
        assertEquals(0.0, buffer.getAverageFps());
    }

    @Test
    @DisplayName("get1PercentLowFps returns 0 on empty buffer")
    void testEmptyBuffer1PercentLow() {
        assertEquals(0.0, buffer.get1PercentLowFps());
    }

    @Test
    @DisplayName("get99thPercentileFrameTimeMs returns 0 on empty buffer")
    void testEmptyBuffer99thPercentile() {
        assertEquals(0.0, buffer.get99thPercentileFrameTimeMs());
    }

    @Test
    @DisplayName("getCount returns 0 on empty buffer")
    void testEmptyBufferCount() {
        assertEquals(0, buffer.getCount());
    }

    @Test
    @DisplayName("isFull returns false on empty buffer")
    void testEmptyBufferIsNotFull() {
        assertFalse(buffer.isFull());
    }

    // -----------------------------------------------------------------------
    // Single frame
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Single 16.6ms frame yields approximately 60 FPS")
    void testSingleFrame() {
        buffer.record(NS_16_6MS);

        double avgFps = buffer.getAverageFps();
        assertEquals(60.0, avgFps, TOLERANCE,
                "Average FPS should be ~60 for a single 16.6ms frame");
        assertEquals(1, buffer.getCount());
    }

    // -----------------------------------------------------------------------
    // Steady 60 FPS
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("300 frames at exactly 16666666ns yields ~60 average FPS")
    void testSteadyFps() {
        for (int i = 0; i < 300; i++) {
            buffer.record(NS_16_6MS);
        }

        assertEquals(300, buffer.getCount());
        assertEquals(60.0, buffer.getAverageFps(), TOLERANCE);
        // 1% low should also be ~60 when all frames are identical
        assertEquals(60.0, buffer.get1PercentLowFps(), TOLERANCE);
    }

    // -----------------------------------------------------------------------
    // High FPS (~1000)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Frames at 1ms yield approximately 1000 FPS")
    void testHighFps() {
        for (int i = 0; i < 200; i++) {
            buffer.record(NS_1MS);
        }

        assertEquals(1000.0, buffer.getAverageFps(), 1.0);
    }

    // -----------------------------------------------------------------------
    // Circular buffer wrap
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Recording more than capacity overwrites old data correctly")
    void testCircularBufferWrap() {
        // Fill with 300 frames of 16.6ms (60 fps), then overwrite with 300 frames of 1ms (1000 fps)
        for (int i = 0; i < 300; i++) {
            buffer.record(NS_16_6MS);
        }
        for (int i = 0; i < 300; i++) {
            buffer.record(NS_1MS);
        }

        // Count should cap at capacity (300)
        assertEquals(300, buffer.getCount());
        // All 300 frames should now be 1ms, so average should be ~1000 FPS
        assertEquals(1000.0, buffer.getAverageFps(), 1.0,
                "After full overwrite, average FPS should reflect only new data");
    }

    // -----------------------------------------------------------------------
    // Trend slope
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Constant frame times produce a trend slope near zero")
    void testTrendSlopeStable() {
        for (int i = 0; i < 100; i++) {
            buffer.record(NS_16_6MS);
        }

        double slope = buffer.getFrameTimeTrendSlope();
        assertEquals(0.0, slope, 1.0,
                "Constant frame times should yield slope ~0");
    }

    @Test
    @DisplayName("Increasing frame times produce a positive trend slope")
    void testTrendSlopeDegrading() {
        // Frame times increase linearly: 10ms, 10.1ms, 10.2ms, ...
        for (int i = 0; i < 100; i++) {
            long frameTime = 10_000_000L + (i * 100_000L);
            buffer.record(frameTime);
        }

        double slope = buffer.getFrameTimeTrendSlope();
        assertTrue(slope > 0,
                "Increasing frame times should produce positive slope, got: " + slope);
    }

    @Test
    @DisplayName("Trend slope returns 0 when fewer than 10 samples exist")
    void testTrendSlopeInsufficientSamples() {
        for (int i = 0; i < 9; i++) {
            buffer.record(NS_16_6MS + i * 1_000_000L);
        }

        assertEquals(0.0, buffer.getFrameTimeTrendSlope(),
                "Trend slope should be 0 with fewer than 10 samples");
    }

    // -----------------------------------------------------------------------
    // Stutter detection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("One frame at 10x average among 299 normal frames triggers stutter")
    void testStutterDetection() {
        // Record 299 normal frames at 16.6ms
        for (int i = 0; i < 299; i++) {
            buffer.record(NS_16_6MS);
        }
        // Record 1 extremely slow frame (10x average)
        buffer.record(NS_16_6MS * 10);

        assertTrue(buffer.hasStutter(),
                "A single frame at 10x the average should be detected as stutter");
    }

    @Test
    @DisplayName("All consistent frames produce no stutter")
    void testNoStutter() {
        for (int i = 0; i < 300; i++) {
            buffer.record(NS_16_6MS);
        }

        assertFalse(buffer.hasStutter(),
                "Uniform frame times should not register as stutter");
    }

    @Test
    @DisplayName("hasStutter returns false with fewer than 10 samples")
    void testStutterInsufficientSamples() {
        for (int i = 0; i < 5; i++) {
            buffer.record(NS_16_6MS);
        }
        buffer.record(NS_16_6MS * 100); // extreme outlier

        assertFalse(buffer.hasStutter(),
                "Stutter detection should require at least 10 samples");
    }

    // -----------------------------------------------------------------------
    // 1% low FPS
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("1% low FPS correctly reflects the slowest frames in a known distribution")
    void test1PercentLow() {
        // 297 frames at 16.6ms (60fps) and 3 frames at 33.3ms (30fps)
        // 1% of 300 = 3 frames -> the worst 3 should average ~30 FPS
        for (int i = 0; i < 297; i++) {
            buffer.record(NS_16_6MS);
        }
        long slowFrame = 33_333_333L; // ~30 FPS
        for (int i = 0; i < 3; i++) {
            buffer.record(slowFrame);
        }

        double p1Low = buffer.get1PercentLowFps();
        assertEquals(30.0, p1Low, 1.0,
                "1% low should reflect the average of the 3 slowest frames (~30 FPS)");
    }

    // -----------------------------------------------------------------------
    // 99th percentile
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("99th percentile frame time reflects the tail of the distribution")
    void test99thPercentile() {
        // 297 frames at 16.6ms, 3 frames at 50ms
        for (int i = 0; i < 297; i++) {
            buffer.record(NS_16_6MS);
        }
        long slowFrame = 50_000_000L; // 50ms
        for (int i = 0; i < 3; i++) {
            buffer.record(slowFrame);
        }

        double p99Ms = buffer.get99thPercentileFrameTimeMs();
        // The 99th percentile index (300 * 0.99 = 297) should land on the slow frames
        assertTrue(p99Ms >= 33.0,
                "99th percentile frame time should be in the slow frame region, got: " + p99Ms + "ms");
    }

    // -----------------------------------------------------------------------
    // Reset
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Reset clears all data and returns buffer to empty state")
    void testReset() {
        for (int i = 0; i < 100; i++) {
            buffer.record(NS_16_6MS);
        }
        assertEquals(100, buffer.getCount());

        buffer.reset();

        assertEquals(0, buffer.getCount());
        assertEquals(0.0, buffer.getAverageFps());
        assertEquals(0.0, buffer.get1PercentLowFps());
        assertEquals(0.0, buffer.get99thPercentileFrameTimeMs());
        assertFalse(buffer.isFull());
    }

    // -----------------------------------------------------------------------
    // isFull
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isFull returns false before reaching capacity and true after")
    void testIsFull() {
        for (int i = 0; i < 299; i++) {
            buffer.record(NS_16_6MS);
        }
        assertFalse(buffer.isFull(), "Buffer should not be full at 299/300");

        buffer.record(NS_16_6MS);
        assertTrue(buffer.isFull(), "Buffer should be full at 300/300");
    }

    @Test
    @DisplayName("isFull remains true after exceeding capacity")
    void testIsFullAfterOverflow() {
        for (int i = 0; i < 500; i++) {
            buffer.record(NS_16_6MS);
        }
        assertTrue(buffer.isFull());
        assertEquals(300, buffer.getCount(), "Count should cap at capacity");
    }

    // -----------------------------------------------------------------------
    // Average frame time helpers
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getAverageFrameTimeMs returns correct millisecond value")
    void testAverageFrameTimeMs() {
        for (int i = 0; i < 100; i++) {
            buffer.record(NS_16_6MS);
        }
        double avgMs = buffer.getAverageFrameTimeMs();
        assertEquals(16.666, avgMs, 0.01);
    }

    @Test
    @DisplayName("getAverageFrameTimeNanos returns correct nanosecond value")
    void testAverageFrameTimeNanos() {
        for (int i = 0; i < 100; i++) {
            buffer.record(NS_16_6MS);
        }
        double avgNanos = buffer.getAverageFrameTimeNanos();
        assertEquals(NS_16_6MS, avgNanos, 1.0);
    }
}
