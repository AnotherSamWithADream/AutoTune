package com.autotune.benchmark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GCMonitor")
class GCMonitorTest {

    private GCMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new GCMonitor();
    }

    // -----------------------------------------------------------------------
    // Reset behavior
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Reset")
    class Reset {

        @Test
        @DisplayName("After reset, GC pause count should be 0 (relative to new baseline)")
        void testResetPauseCount() {
            monitor.reset();
            long count = monitor.getGcPauseCount();
            assertEquals(0, count,
                    "Immediately after reset, pause count should be 0 (no GC since baseline)");
        }

        @Test
        @DisplayName("After reset, GC pause time should be 0 (relative to new baseline)")
        void testResetPauseTime() {
            monitor.reset();
            long timeMs = monitor.getGcPauseTimeMs();
            assertEquals(0, timeMs,
                    "Immediately after reset, pause time should be 0ms");
        }

        @Test
        @DisplayName("GC counts should be non-negative after any number of resets")
        void testResetProducesNonNegative() {
            for (int i = 0; i < 5; i++) {
                monitor.reset();
                assertTrue(monitor.getGcPauseCount() >= 0,
                        "Pause count should never be negative after reset (iteration " + i + ")");
                assertTrue(monitor.getGcPauseTimeMs() >= 0,
                        "Pause time should never be negative after reset (iteration " + i + ")");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Snapshot
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Snapshot")
    class Snapshot {

        @Test
        @DisplayName("Snapshot should contain current GC values relative to baseline")
        void testSnapshot() {
            monitor.reset();
            GCMonitor.GcSnapshot snapshot = monitor.snapshot();

            assertNotNull(snapshot, "Snapshot should not be null");
            assertEquals(monitor.getGcPauseCount(), snapshot.pauseCount(),
                    "Snapshot pause count should match getGcPauseCount()");
            assertEquals(monitor.getGcPauseTimeMs(), snapshot.pauseTimeMs(),
                    "Snapshot pause time should match getGcPauseTimeMs()");
        }

        @Test
        @DisplayName("Snapshot immediately after reset should show zero counts")
        void testSnapshotAfterReset() {
            monitor.reset();
            GCMonitor.GcSnapshot snapshot = monitor.snapshot();

            assertEquals(0, snapshot.pauseCount(),
                    "Snapshot pause count should be 0 right after reset");
            assertEquals(0, snapshot.pauseTimeMs(),
                    "Snapshot pause time should be 0 right after reset");
        }

        @Test
        @DisplayName("Snapshot average pause time should be 0 when no pauses occurred")
        void testSnapshotAveragePauseNoPauses() {
            monitor.reset();
            GCMonitor.GcSnapshot snapshot = monitor.snapshot();

            assertEquals(0.0, snapshot.averagePauseMs(), 0.001,
                    "Average pause time should be 0.0 when pause count is 0");
        }

        @Test
        @DisplayName("GcSnapshot record should compute correct averagePauseMs")
        void testGcSnapshotAverageComputation() {
            // Directly construct a snapshot with known values
            GCMonitor.GcSnapshot snapshot = new GCMonitor.GcSnapshot(10, 500);

            assertEquals(50.0, snapshot.averagePauseMs(), 0.001,
                    "500ms / 10 pauses = 50ms average");
        }

        @Test
        @DisplayName("GcSnapshot with zero pause count should return 0 average (no division by zero)")
        void testGcSnapshotZeroCountAverage() {
            GCMonitor.GcSnapshot snapshot = new GCMonitor.GcSnapshot(0, 0);
            assertEquals(0.0, snapshot.averagePauseMs(), 0.001,
                    "Average should be 0.0 when count is 0, not throw ArithmeticException");
        }

        @Test
        @DisplayName("GcSnapshot with negative pause count should return 0 average")
        void testGcSnapshotNegativeCountAverage() {
            GCMonitor.GcSnapshot snapshot = new GCMonitor.GcSnapshot(-1, 100);
            assertEquals(0.0, snapshot.averagePauseMs(), 0.001,
                    "Average should be 0.0 when count is negative");
        }
    }

    // -----------------------------------------------------------------------
    // Multiple resets
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Multiple resets")
    class MultipleResets {

        @Test
        @DisplayName("Each reset should establish a clean baseline with zero counts")
        void testMultipleResets() {
            for (int i = 0; i < 3; i++) {
                monitor.reset();
                GCMonitor.GcSnapshot snapshot = monitor.snapshot();

                assertEquals(0, snapshot.pauseCount(),
                        "After reset #" + (i + 1) + ", pause count should be 0");
                assertEquals(0, snapshot.pauseTimeMs(),
                        "After reset #" + (i + 1) + ", pause time should be 0ms");
            }
        }

        @Test
        @DisplayName("Constructing a new GCMonitor should start with a clean baseline")
        void testConstructorResetsBaseline() {
            GCMonitor fresh = new GCMonitor();
            assertEquals(0, fresh.getGcPauseCount(),
                    "A newly constructed GCMonitor should have 0 pause count");
            assertEquals(0, fresh.getGcPauseTimeMs(),
                    "A newly constructed GCMonitor should have 0 pause time");
        }

        @Test
        @DisplayName("Reset after some time still returns consistent non-negative values")
        void testResetConsistency() {
            // Get initial readings
            monitor.reset();
            long count1 = monitor.getGcPauseCount();
            long time1 = monitor.getGcPauseTimeMs();

            // Reset again - new baseline should capture everything up to now
            monitor.reset();
            long count2 = monitor.getGcPauseCount();
            long time2 = monitor.getGcPauseTimeMs();

            // After the second reset, values should be 0 or very small
            // (only GC that happened between the two calls)
            assertTrue(count2 >= 0, "Count after second reset should be >= 0");
            assertTrue(time2 >= 0, "Time after second reset should be >= 0");
            assertTrue(count2 <= count1 || count1 == 0,
                    "Count after second reset should be <= count from first period (or both 0)");
        }
    }
}
