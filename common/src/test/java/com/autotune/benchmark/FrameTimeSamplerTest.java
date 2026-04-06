package com.autotune.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FrameTimeSamplerTest {

    private static final long NS_16_6MS = 16_666_666L;  // ~60 FPS
    private static final long NS_10MS = 10_000_000L;     // ~100 FPS
    private static final double TOLERANCE_FPS = 0.5;

    private FrameTimeSampler sampler;

    @BeforeEach
    void setUp() {
        sampler = new FrameTimeSampler(100);
    }

    // -----------------------------------------------------------------------
    // Record and count
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Recording N frames sets getCount to N")
    void testRecordAndCount() {
        assertEquals(0, sampler.getCount());

        for (int i = 0; i < 50; i++) {
            sampler.record(NS_16_6MS);
        }

        assertEquals(50, sampler.getCount());
    }

    @Test
    @DisplayName("Count increments with each recorded frame up to capacity")
    void testCountIncrementsCorrectly() {
        for (int i = 1; i <= 100; i++) {
            sampler.record(NS_16_6MS);
            assertEquals(i, sampler.getCount());
        }
    }

    // -----------------------------------------------------------------------
    // Circular overwrite
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Recording more than capacity caps count at capacity")
    void testCircularOverwriteCount() {
        for (int i = 0; i < 250; i++) {
            sampler.record(NS_16_6MS);
        }

        assertEquals(100, sampler.getCount(),
                "Count should cap at capacity (100)");
    }

    @Test
    @DisplayName("Circular overwrite replaces old data with new data")
    void testCircularOverwriteData() {
        // Fill with 100 frames at 16.6ms (60 FPS)
        for (int i = 0; i < 100; i++) {
            sampler.record(NS_16_6MS);
        }
        assertEquals(60.0, sampler.getAverageFps(), TOLERANCE_FPS);

        // Overwrite all 100 frames with 10ms (100 FPS)
        for (int i = 0; i < 100; i++) {
            sampler.record(NS_10MS);
        }

        assertEquals(100, sampler.getCount());
        assertEquals(100.0, sampler.getAverageFps(), TOLERANCE_FPS,
                "After full overwrite, average should reflect only new data");
    }

    // -----------------------------------------------------------------------
    // Average FPS
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Average FPS is computed correctly from known frame times")
    void testAverageFps() {
        // 50 frames at 10ms (100 FPS) + 50 frames at 20ms (50 FPS)
        // Average frame time = 15ms -> 66.67 FPS
        for (int i = 0; i < 50; i++) {
            sampler.record(NS_10MS);
        }
        for (int i = 0; i < 50; i++) {
            sampler.record(20_000_000L);
        }

        double expected = 1_000_000_000.0 / 15_000_000.0; // ~66.67
        assertEquals(expected, sampler.getAverageFps(), TOLERANCE_FPS);
    }

    @Test
    @DisplayName("Average FPS returns 0 on empty sampler")
    void testAverageFpsEmpty() {
        assertEquals(0.0, sampler.getAverageFps());
    }

    // -----------------------------------------------------------------------
    // Reset
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Reset clears all stats to zero")
    void testReset() {
        for (int i = 0; i < 80; i++) {
            sampler.record(NS_16_6MS);
        }
        assertEquals(80, sampler.getCount());

        sampler.reset();

        assertEquals(0, sampler.getCount());
        assertEquals(0.0, sampler.getAverageFps());
        assertEquals(0.0, sampler.get1PercentLowFps());
        assertEquals(0.0, sampler.get99thPercentileFrameTime());
        assertFalse(sampler.hasStutter());
    }

    @Test
    @DisplayName("After reset, new recordings start fresh")
    void testResetThenRecord() {
        for (int i = 0; i < 100; i++) {
            sampler.record(NS_16_6MS);
        }
        sampler.reset();

        for (int i = 0; i < 10; i++) {
            sampler.record(NS_10MS);
        }

        assertEquals(10, sampler.getCount());
        assertEquals(100.0, sampler.getAverageFps(), TOLERANCE_FPS,
                "After reset and re-recording 10ms frames, should be ~100 FPS");
    }

    // -----------------------------------------------------------------------
    // getSamplesSnapshot
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Snapshot returns correct chronological ordering before wrap")
    void testGetSamplesSnapshotBeforeWrap() {
        sampler.record(10_000_000L);
        sampler.record(20_000_000L);
        sampler.record(30_000_000L);

        long[] snapshot = sampler.getSamplesSnapshot();

        assertEquals(3, snapshot.length);
        assertEquals(10_000_000L, snapshot[0]);
        assertEquals(20_000_000L, snapshot[1]);
        assertEquals(30_000_000L, snapshot[2]);
    }

    @Test
    @DisplayName("Snapshot returns correct chronological ordering after circular wrap")
    void testGetSamplesSnapshotAfterWrap() {
        // Use a small sampler for easy verification
        FrameTimeSampler small = new FrameTimeSampler(3);
        small.record(1L);
        small.record(2L);
        small.record(3L);
        // Buffer full: [1, 2, 3], writeIndex = 0
        small.record(4L);
        // Buffer: [4, 2, 3], writeIndex = 1
        small.record(5L);
        // Buffer: [4, 5, 3], writeIndex = 2

        long[] snapshot = small.getSamplesSnapshot();

        assertEquals(3, snapshot.length);
        // Chronological order: 3 (oldest), 4, 5 (newest)
        assertEquals(3L, snapshot[0], "Oldest remaining should be 3");
        assertEquals(4L, snapshot[1]);
        assertEquals(5L, snapshot[2], "Newest should be 5");
    }

    @Test
    @DisplayName("Snapshot returns empty array for empty sampler")
    void testGetSamplesSnapshotEmpty() {
        long[] snapshot = sampler.getSamplesSnapshot();
        assertEquals(0, snapshot.length);
    }

    @Test
    @DisplayName("Snapshot is a copy, not a reference to internal state")
    void testSnapshotIsCopy() {
        sampler.record(NS_16_6MS);
        long[] snapshot = sampler.getSamplesSnapshot();
        snapshot[0] = 0L; // modify the copy

        long[] snapshot2 = sampler.getSamplesSnapshot();
        assertEquals(NS_16_6MS, snapshot2[0],
                "Modifying snapshot should not affect internal buffer");
    }

    // -----------------------------------------------------------------------
    // Thread safety
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Concurrent record and read operations do not throw exceptions")
    void testSynchronizedAccess() throws InterruptedException {
        int iterations = 10_000;
        AtomicBoolean error = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Writer thread
        Thread writer = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    sampler.record(NS_16_6MS + (i % 100));
                }
            } catch (Exception e) {
                error.set(true);
            } finally {
                doneLatch.countDown();
            }
        });

        // Reader thread
        Thread reader = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = 0; i < iterations; i++) {
                    double avgFps = sampler.getAverageFps();
                    double lowFps = sampler.get1PercentLowFps();
                    int count = sampler.getCount();
                    long[] snapshot = sampler.getSamplesSnapshot();
                    boolean stutter = sampler.hasStutter();
                    // Use results to prevent dead-code elimination
                    if (avgFps < 0 || lowFps < 0 || count < 0
                            || snapshot == null || stutter != stutter) {
                        throw new AssertionError("Unreachable");
                    }
                }
            } catch (Exception e) {
                error.set(true);
            } finally {
                doneLatch.countDown();
            }
        });

        writer.start();
        reader.start();
        startLatch.countDown();
        doneLatch.await();

        assertFalse(error.get(),
                "No exceptions should occur during concurrent access");
    }

    // -----------------------------------------------------------------------
    // 1% low and 99th percentile
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("1% low FPS reflects the slowest frames")
    void test1PercentLowFps() {
        // 99 frames at 10ms, 1 frame at 100ms
        for (int i = 0; i < 99; i++) {
            sampler.record(NS_10MS);
        }
        sampler.record(100_000_000L); // 100ms

        double p1Low = sampler.get1PercentLowFps();
        // 1% of 100 = 1 frame -> the slowest at 100ms -> 10 FPS
        assertEquals(10.0, p1Low, TOLERANCE_FPS);
    }

    @Test
    @DisplayName("99th percentile frame time in milliseconds")
    void test99thPercentileFrameTime() {
        // 99 frames at 10ms, 1 frame at 100ms
        for (int i = 0; i < 99; i++) {
            sampler.record(NS_10MS);
        }
        sampler.record(100_000_000L);

        double p99 = sampler.get99thPercentileFrameTime();
        // p99 index = ceil(100 * 0.99) - 1 = 98 -> sorted[98] is 10ms
        assertEquals(10.0, p99, 0.1);
    }

    // -----------------------------------------------------------------------
    // Stutter detection
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Stutter detected when a frame exceeds 3x the average")
    void testHasStutter() {
        for (int i = 0; i < 99; i++) {
            sampler.record(NS_10MS);
        }
        sampler.record(NS_10MS * 10); // 100ms = 10x average

        assertTrue(sampler.hasStutter());
    }

    @Test
    @DisplayName("No stutter when all frames are uniform")
    void testNoStutter() {
        for (int i = 0; i < 100; i++) {
            sampler.record(NS_10MS);
        }

        assertFalse(sampler.hasStutter());
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Constructor rejects non-positive capacity")
    void testInvalidCapacity() {
        assertThrows(IllegalArgumentException.class,
                () -> new FrameTimeSampler(0));
        assertThrows(IllegalArgumentException.class,
                () -> new FrameTimeSampler(-1));
    }

    @Test
    @DisplayName("Trend slope is positive for degrading performance")
    void testTrendSlopeDegrading() {
        for (int i = 0; i < 100; i++) {
            sampler.record(10_000_000L + (i * 100_000L));
        }

        double slope = sampler.getFrameTimeTrendSlope();
        assertTrue(slope > 0,
                "Linearly increasing frame times should yield positive slope");
    }

    @Test
    @DisplayName("Trend slope returns 0 with fewer than 2 samples")
    void testTrendSlopeInsufficientData() {
        sampler.record(NS_10MS);
        assertEquals(0.0, sampler.getFrameTimeTrendSlope());
    }
}
