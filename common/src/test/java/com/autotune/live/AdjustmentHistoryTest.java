package com.autotune.live;

import com.autotune.live.AdjustmentHistory.AdjustmentEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdjustmentHistoryTest {

    private AdjustmentHistory history;

    @BeforeEach
    void setUp() {
        history = new AdjustmentHistory(10);
    }

    // ---------------------------------------------------------------
    // Record and retrieve
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Recording an entry makes it retrievable via getEntries")
    void testRecordAndRetrieve() {
        history.record("vanilla.render_distance", "Render Distance",
                "16", "12", "FPS below target", AdaptiveState.DEGRADING);

        List<AdjustmentEntry> entries = history.getEntries();
        assertEquals(1, entries.size(), "Should have exactly one entry");

        AdjustmentEntry entry = entries.getFirst();
        assertEquals("vanilla.render_distance", entry.settingId());
        assertEquals("Render Distance", entry.settingName());
        assertEquals("16", entry.oldValue());
        assertEquals("12", entry.newValue());
        assertEquals("FPS below target", entry.reason());
        assertEquals(AdaptiveState.DEGRADING, entry.state());
        assertTrue(entry.timestamp() > 0, "Timestamp should be a positive epoch millis value");
    }

    // ---------------------------------------------------------------
    // Max eviction
    // ---------------------------------------------------------------

    @Test
    @DisplayName("When maxEntries is exceeded the oldest entries are evicted")
    void testMaxEviction() {
        AdjustmentHistory smallHistory = new AdjustmentHistory(3);

        for (int i = 1; i <= 5; i++) {
            smallHistory.record("setting_" + i, "Setting " + i,
                    String.valueOf(i), String.valueOf(i + 1),
                    "reason " + i, AdaptiveState.STABLE);
        }

        List<AdjustmentEntry> entries = smallHistory.getEntries();
        assertEquals(3, entries.size(), "Only the last 3 entries should remain");
        assertEquals("setting_3", entries.get(0).settingId(), "Oldest surviving entry should be setting_3");
        assertEquals("setting_4", entries.get(1).settingId());
        assertEquals("setting_5", entries.get(2).settingId());
    }

    // ---------------------------------------------------------------
    // getLastAdjustment
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getLastAdjustment returns the most recently recorded entry")
    void testGetLastAdjustment() {
        history.record("a", "A", "1", "2", "r1", AdaptiveState.STABLE);
        history.record("b", "B", "3", "4", "r2", AdaptiveState.BOOSTING);

        AdjustmentEntry last = history.getLastAdjustment();
        assertNotNull(last);
        assertEquals("b", last.settingId(), "Last adjustment should be the most recently recorded");
        assertEquals(AdaptiveState.BOOSTING, last.state());
    }

    @Test
    @DisplayName("getLastAdjustment returns null when history is empty")
    void testGetLastAdjustmentEmpty() {
        assertNull(history.getLastAdjustment(),
                "getLastAdjustment on empty history should return null");
    }

    // ---------------------------------------------------------------
    // totalAdjustments
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getTotalAdjustments includes evicted entries in the count")
    void testTotalAdjustments() {
        AdjustmentHistory smallHistory = new AdjustmentHistory(2);

        smallHistory.record("a", "A", "1", "2", "r", AdaptiveState.STABLE);
        smallHistory.record("b", "B", "3", "4", "r", AdaptiveState.STABLE);
        smallHistory.record("c", "C", "5", "6", "r", AdaptiveState.STABLE);
        smallHistory.record("d", "D", "7", "8", "r", AdaptiveState.STABLE);

        // Only 2 entries should remain in the deque
        assertEquals(2, smallHistory.getEntries().size());
        // But totalAdjustments counts all recordings including evicted ones
        assertEquals(4, smallHistory.getTotalAdjustments(),
                "Total adjustments should count all recordings, not just surviving entries");
    }

    @Test
    @DisplayName("getTotalAdjustments starts at 0")
    void testTotalAdjustmentsInitiallyZero() {
        assertEquals(0, history.getTotalAdjustments(),
                "Total adjustments should be 0 for a fresh history");
    }

    // ---------------------------------------------------------------
    // clear
    // ---------------------------------------------------------------

    @Test
    @DisplayName("clear empties entries and resets totalCount to 0")
    void testClear() {
        history.record("x", "X", "1", "2", "r", AdaptiveState.RECOVERING);
        history.record("y", "Y", "3", "4", "r", AdaptiveState.EMERGENCY);

        assertEquals(2, history.getTotalAdjustments());
        assertEquals(2, history.getEntries().size());

        history.clear();

        assertEquals(0, history.getEntries().size(), "Entries should be empty after clear");
        assertEquals(0, history.getTotalAdjustments(), "Total count should be 0 after clear");
        assertNull(history.getLastAdjustment(), "getLastAdjustment should return null after clear");
    }

    // ---------------------------------------------------------------
    // Direction symbol
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Direction symbol is UP arrow when numeric value increases")
    void testDirectionSymbolUp() {
        AdjustmentEntry entry = new AdjustmentEntry(
                System.currentTimeMillis(), "rd", "Render Distance",
                "8", "12", "reason", AdaptiveState.BOOSTING);

        assertEquals("\u2191", entry.getDirectionSymbol(),
                "Increasing numeric value should produce an up arrow");
    }

    @Test
    @DisplayName("Direction symbol is DOWN arrow when numeric value decreases")
    void testDirectionSymbolDown() {
        AdjustmentEntry entry = new AdjustmentEntry(
                System.currentTimeMillis(), "rd", "Render Distance",
                "16", "12", "reason", AdaptiveState.DEGRADING);

        assertEquals("\u2193", entry.getDirectionSymbol(),
                "Decreasing numeric value should produce a down arrow");
    }

    @Test
    @DisplayName("Direction symbol is SWAP arrow for non-numeric values")
    void testDirectionSymbolSwap() {
        AdjustmentEntry entry = new AdjustmentEntry(
                System.currentTimeMillis(), "gfx", "Graphics",
                "fancy", "fast", "reason", AdaptiveState.DEGRADING);

        assertEquals("\u21C4", entry.getDirectionSymbol(),
                "Non-numeric values should produce a swap/exchange arrow");
    }

    @Test
    @DisplayName("Direction symbol handles decimal numeric strings")
    void testDirectionSymbolDecimal() {
        AdjustmentEntry up = new AdjustmentEntry(
                System.currentTimeMillis(), "ed", "Entity Distance",
                "0.75", "1.0", "reason", AdaptiveState.BOOSTING);
        assertEquals("\u2191", up.getDirectionSymbol());

        AdjustmentEntry down = new AdjustmentEntry(
                System.currentTimeMillis(), "ed", "Entity Distance",
                "1.0", "0.9", "reason", AdaptiveState.DEGRADING);
        assertEquals("\u2193", down.getDirectionSymbol());
    }

    @Test
    @DisplayName("Direction symbol treats mixed numeric/non-numeric as swap")
    void testDirectionSymbolMixed() {
        AdjustmentEntry entry = new AdjustmentEntry(
                System.currentTimeMillis(), "p", "Particles",
                "ALL", "3", "reason", AdaptiveState.DEGRADING);

        assertEquals("\u21C4", entry.getDirectionSymbol(),
                "Mixed numeric and non-numeric should fall back to swap arrow");
    }

    // ---------------------------------------------------------------
    // toDisplayString
    // ---------------------------------------------------------------

    @Test
    @DisplayName("toDisplayString formats as 'symbol settingName: old -> new'")
    void testToDisplayString() {
        AdjustmentEntry entry = new AdjustmentEntry(
                System.currentTimeMillis(), "vanilla.render_distance", "Render Distance",
                "16", "12", "fps drop", AdaptiveState.DEGRADING);

        String display = entry.toDisplayString();
        // Down arrow because 16 -> 12
        assertEquals("\u2193 Render Distance: 16 \u2192 12", display);
    }

    @Test
    @DisplayName("toDisplayString for non-numeric uses swap arrow")
    void testToDisplayStringNonNumeric() {
        AdjustmentEntry entry = new AdjustmentEntry(
                System.currentTimeMillis(), "vanilla.particles", "Particles",
                "ALL", "DECREASED", "fps drop", AdaptiveState.DEGRADING);

        String display = entry.toDisplayString();
        assertEquals("\u21C4 Particles: ALL \u2192 DECREASED", display);
    }

    @Test
    @DisplayName("toDisplayString for increasing value uses up arrow")
    void testToDisplayStringUp() {
        AdjustmentEntry entry = new AdjustmentEntry(
                System.currentTimeMillis(), "vanilla.render_distance", "Render Distance",
                "8", "16", "fps recovered", AdaptiveState.RECOVERING);

        String display = entry.toDisplayString();
        assertEquals("\u2191 Render Distance: 8 \u2192 16", display);
    }

    // ---------------------------------------------------------------
    // Immutability of returned list
    // ---------------------------------------------------------------

    @Test
    @DisplayName("getEntries returns an unmodifiable list")
    void testGetEntriesImmutable() {
        history.record("x", "X", "1", "2", "r", AdaptiveState.STABLE);
        List<AdjustmentEntry> entries = history.getEntries();

        assertThrows(UnsupportedOperationException.class,
                () -> entries.add(new AdjustmentEntry(0, "y", "Y", "3", "4", "r", AdaptiveState.STABLE)),
                "Returned entries list should be unmodifiable");
    }
}
