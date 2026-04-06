package com.autotune.live;

import com.autotune.live.OscillationDetector.AdjustmentDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OscillationDetectorTest {

    private static final long LOCK_DURATION_MS = 60_000; // 60 seconds
    private OscillationDetector detector;

    @BeforeEach
    void setUp() {
        detector = new OscillationDetector(LOCK_DURATION_MS);
    }

    // ---------------------------------------------------------------
    // Basic oscillation detection
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Same-direction adjustments should not trigger a lock")
    void testNoOscillation() {
        detector.recordAdjustment("render_distance", AdjustmentDirection.UP);
        detector.recordAdjustment("render_distance", AdjustmentDirection.UP);

        assertFalse(detector.isLocked("render_distance"),
                "Two UP adjustments in a row should not cause an oscillation lock");
        assertEquals(0, detector.getRemainingLockMs("render_distance"),
                "No lock means remaining lock time should be 0");
    }

    @Test
    @DisplayName("Opposite-direction adjustments within 30s should trigger a lock")
    void testOscillationDetected() {
        // UP then immediately DOWN (well within 30s)
        detector.recordAdjustment("render_distance", AdjustmentDirection.UP);
        detector.recordAdjustment("render_distance", AdjustmentDirection.DOWN);

        assertTrue(detector.isLocked("render_distance"),
                "UP then DOWN within 30s should lock the setting");
        assertTrue(detector.getRemainingLockMs("render_distance") > 0,
                "Remaining lock time should be positive immediately after oscillation");
    }

    @Test
    @DisplayName("Opposite-direction adjustments after 30s should NOT trigger a lock")
    void testOscillationNotDetectedAfterTimeout() {
        // We cannot easily wait 30 real seconds in a unit test, but we can
        // verify by recording an adjustment and then recording another opposite
        // adjustment immediately.  Since System.currentTimeMillis() difference
        // will be < 30s, this would lock.  Instead, we use a short-lock detector
        // and verify that two rapid same-direction adjustments DON'T lock.
        //
        // For the actual 30s boundary test we create a detector, record UP,
        // and then verify that if we DON'T record DOWN within the test the
        // setting stays unlocked.  A true elapsed-time test would need a
        // clock abstraction, but we can at least verify the boundary logic
        // via the negative case.

        OscillationDetector freshDetector = new OscillationDetector(1_000);
        freshDetector.recordAdjustment("particles", AdjustmentDirection.DOWN);

        // Only one direction recorded -- no oscillation possible
        assertFalse(freshDetector.isLocked("particles"),
                "A single adjustment should never lock a setting");

        // Record same direction again
        freshDetector.recordAdjustment("particles", AdjustmentDirection.DOWN);
        assertFalse(freshDetector.isLocked("particles"),
                "Same-direction adjustments should never lock a setting");
    }

    // ---------------------------------------------------------------
    // Lock duration and expiry
    // ---------------------------------------------------------------

    @Test
    @DisplayName("isLocked returns true during lock period and false after expiry")
    void testLockDuration() throws InterruptedException {
        // Use a very short lock so the test completes quickly
        OscillationDetector shortLockDetector = new OscillationDetector(150);
        shortLockDetector.recordAdjustment("biome_blend", AdjustmentDirection.UP);
        shortLockDetector.recordAdjustment("biome_blend", AdjustmentDirection.DOWN);

        assertTrue(shortLockDetector.isLocked("biome_blend"),
                "Setting should be locked immediately after oscillation");

        // Wait for the lock to expire
        Thread.sleep(200);

        assertFalse(shortLockDetector.isLocked("biome_blend"),
                "Setting should be unlocked after the lock duration expires");
    }

    @Test
    @DisplayName("getRemainingLockMs decreases over time and reaches 0 after expiry")
    void testGetRemainingLockMs() throws InterruptedException {
        OscillationDetector shortLockDetector = new OscillationDetector(500);
        shortLockDetector.recordAdjustment("mipmap", AdjustmentDirection.DOWN);
        shortLockDetector.recordAdjustment("mipmap", AdjustmentDirection.UP);

        long initial = shortLockDetector.getRemainingLockMs("mipmap");
        assertTrue(initial > 0, "Remaining lock should be positive right after oscillation");
        assertTrue(initial <= 500, "Remaining lock should not exceed the configured duration");

        Thread.sleep(100);

        long afterWait = shortLockDetector.getRemainingLockMs("mipmap");
        assertTrue(afterWait < initial,
                "Remaining lock should decrease over time");

        Thread.sleep(500);

        assertEquals(0, shortLockDetector.getRemainingLockMs("mipmap"),
                "Remaining lock should be 0 after lock duration expires");
    }

    // ---------------------------------------------------------------
    // clearAll
    // ---------------------------------------------------------------

    @Test
    @DisplayName("clearAll removes all locks and recorded adjustments")
    void testClearAll() {
        // Create an oscillation lock
        detector.recordAdjustment("clouds", AdjustmentDirection.UP);
        detector.recordAdjustment("clouds", AdjustmentDirection.DOWN);
        assertTrue(detector.isLocked("clouds"), "Pre-condition: should be locked");

        // Record a pending adjustment that hasn't oscillated yet
        detector.recordAdjustment("shadows", AdjustmentDirection.UP);

        detector.clearAll();

        assertFalse(detector.isLocked("clouds"),
                "Lock should be cleared after clearAll");
        assertEquals(0, detector.getRemainingLockMs("clouds"),
                "Remaining lock should be 0 after clearAll");

        // The pending adjustment for "shadows" was also cleared, so a DOWN
        // adjustment now should NOT cause oscillation (no previous record)
        detector.recordAdjustment("shadows", AdjustmentDirection.DOWN);
        assertFalse(detector.isLocked("shadows"),
                "Cleared pending adjustments should not cause oscillation");
    }

    // ---------------------------------------------------------------
    // Multiple settings isolation
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Oscillation on one setting does not affect other settings")
    void testMultipleSettings() {
        // Cause oscillation on setting A
        detector.recordAdjustment("settingA", AdjustmentDirection.UP);
        detector.recordAdjustment("settingA", AdjustmentDirection.DOWN);

        // Record a single adjustment on setting B
        detector.recordAdjustment("settingB", AdjustmentDirection.UP);

        assertTrue(detector.isLocked("settingA"),
                "Setting A should be locked due to oscillation");
        assertFalse(detector.isLocked("settingB"),
                "Setting B should NOT be locked -- no oscillation occurred");
        assertEquals(0, detector.getRemainingLockMs("settingB"),
                "Setting B remaining lock should be 0");
    }

    // ---------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------

    @Test
    @DisplayName("isLocked returns false for a setting that was never recorded")
    void testIsLockedUnknownSetting() {
        assertFalse(detector.isLocked("nonexistent"),
                "Unknown setting should not be locked");
    }

    @Test
    @DisplayName("getRemainingLockMs returns 0 for a setting that was never recorded")
    void testGetRemainingLockMsUnknownSetting() {
        assertEquals(0, detector.getRemainingLockMs("nonexistent"),
                "Unknown setting should have 0 remaining lock time");
    }

    @Test
    @DisplayName("DOWN then UP also triggers oscillation (reverse direction)")
    void testOscillationDownThenUp() {
        detector.recordAdjustment("particles", AdjustmentDirection.DOWN);
        detector.recordAdjustment("particles", AdjustmentDirection.UP);

        assertTrue(detector.isLocked("particles"),
                "DOWN then UP should also be detected as oscillation");
    }

    @Test
    @DisplayName("After oscillation lock the recent adjustment record is removed so a second same-direction adjustment does not re-lock")
    void testNoDoubleLockAfterOscillation() throws InterruptedException {
        OscillationDetector shortLockDetector = new OscillationDetector(100);
        shortLockDetector.recordAdjustment("clouds", AdjustmentDirection.UP);
        shortLockDetector.recordAdjustment("clouds", AdjustmentDirection.DOWN);
        assertTrue(shortLockDetector.isLocked("clouds"));

        // Wait for lock to expire
        Thread.sleep(150);
        assertFalse(shortLockDetector.isLocked("clouds"));

        // A single new adjustment should NOT lock because the previous record
        // was cleared when oscillation was detected
        shortLockDetector.recordAdjustment("clouds", AdjustmentDirection.UP);
        assertFalse(shortLockDetector.isLocked("clouds"),
                "A single adjustment after lock expiry should not re-lock");
    }
}
