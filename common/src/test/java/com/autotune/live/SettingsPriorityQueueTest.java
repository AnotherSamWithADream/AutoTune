package com.autotune.live;

import com.autotune.live.SettingsPriorityQueue.AdjustType;
import com.autotune.live.SettingsPriorityQueue.AdjustableEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsPriorityQueueTest {

    private SettingsPriorityQueue queue;

    @BeforeEach
    void setUp() {
        queue = new SettingsPriorityQueue();
    }

    // ---------------------------------------------------------------
    // Queue non-emptiness
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Downgrade queue is not empty")
    void testDowngradeQueueNotEmpty() {
        assertFalse(queue.getDowngradeQueue().isEmpty(),
                "Downgrade queue should contain entries");
    }

    @Test
    @DisplayName("Emergency queue is not empty")
    void testEmergencyQueueNotEmpty() {
        assertFalse(queue.getEmergencyQueue().isEmpty(),
                "Emergency queue should contain entries");
    }

    @Test
    @DisplayName("Upgrade queue is not empty")
    void testUpgradeQueueNotEmpty() {
        assertFalse(queue.getUpgradeQueue().isEmpty(),
                "Upgrade queue should contain entries");
    }

    // ---------------------------------------------------------------
    // Downgrade queue ordering
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Downgrade queue has biome_blend first (least noticeable)")
    void testDowngradeQueueOrder() {
        List<AdjustableEntry> downgrade = queue.getDowngradeQueue();
        AdjustableEntry first = downgrade.getFirst();

        assertEquals("vanilla.biome_blend", first.settingId(),
                "First downgrade entry should be biome_blend (least noticeable)");
        assertEquals("Biome Blend", first.displayName());
        assertEquals(1, first.priority(), "First entry should have priority 1");
    }

    @Test
    @DisplayName("Downgrade queue has render_distance last (most noticeable)")
    void testDowngradeQueueLastEntry() {
        List<AdjustableEntry> downgrade = queue.getDowngradeQueue();
        AdjustableEntry last = downgrade.getLast();

        assertEquals("vanilla.render_distance", last.settingId(),
                "Last downgrade entry should be render_distance (most noticeable)");
    }

    @Test
    @DisplayName("Downgrade queue priorities are in ascending order")
    void testDowngradeQueuePriorityOrder() {
        List<AdjustableEntry> downgrade = queue.getDowngradeQueue();

        for (int i = 1; i < downgrade.size(); i++) {
            assertTrue(downgrade.get(i).priority() > downgrade.get(i - 1).priority(),
                    "Priority at index " + i + " (" + downgrade.get(i).priority()
                            + ") should be greater than at index " + (i - 1)
                            + " (" + downgrade.get(i - 1).priority() + ")");
        }
    }

    // ---------------------------------------------------------------
    // Emergency queue
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Emergency queue contains render distance drop")
    void testEmergencyHasRenderDistance() {
        List<AdjustableEntry> emergency = queue.getEmergencyQueue();
        boolean hasRenderDistance = emergency.stream()
                .anyMatch(e -> "vanilla.render_distance".equals(e.settingId()));

        assertTrue(hasRenderDistance,
                "Emergency queue should contain a render distance adjustment");
    }

    @Test
    @DisplayName("Emergency render distance uses DROP_4 adjust type")
    void testEmergencyRenderDistanceType() {
        List<AdjustableEntry> emergency = queue.getEmergencyQueue();
        AdjustableEntry renderDist = emergency.stream()
                .filter(e -> "vanilla.render_distance".equals(e.settingId()))
                .findFirst()
                .orElseThrow();

        assertEquals(AdjustType.DROP_4, renderDist.type(),
                "Emergency render distance adjustment should be DROP_4");
    }

    @Test
    @DisplayName("Emergency queue contains shaders disable")
    void testEmergencyHasShaders() {
        List<AdjustableEntry> emergency = queue.getEmergencyQueue();
        boolean hasShaders = emergency.stream()
                .anyMatch(e -> "iris.shaders_enabled".equals(e.settingId())
                        && e.type() == AdjustType.DISABLE);

        assertTrue(hasShaders,
                "Emergency queue should contain a shaders DISABLE entry");
    }

    // ---------------------------------------------------------------
    // Upgrade queue is reverse of downgrade
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Upgrade queue re-enables settings in reverse priority order of downgrade")
    void testUpgradeIsReverseOfDowngrade() {
        List<AdjustableEntry> downgrade = queue.getDowngradeQueue();
        List<AdjustableEntry> upgrade = queue.getUpgradeQueue();

        assertEquals(downgrade.size(), upgrade.size(),
                "Upgrade queue should have the same number of entries as downgrade queue");

        // The upgrade queue should contain the same setting IDs as the
        // downgrade queue but in reverse order
        for (int i = 0; i < downgrade.size(); i++) {
            int reverseIndex = downgrade.size() - 1 - i;
            assertEquals(downgrade.get(reverseIndex).settingId(), upgrade.get(i).settingId(),
                    "Upgrade entry at index " + i + " should match downgrade entry at index " + reverseIndex);
        }
    }

    @Test
    @DisplayName("Upgrade queue first entry is render_distance (re-enable most impactful first)")
    void testUpgradeQueueFirstEntry() {
        List<AdjustableEntry> upgrade = queue.getUpgradeQueue();
        AdjustableEntry first = upgrade.getFirst();

        assertEquals("vanilla.render_distance", first.settingId(),
                "First upgrade entry should be render_distance (most impactful to restore)");
    }

    @Test
    @DisplayName("Upgrade queue last entry is biome_blend")
    void testUpgradeQueueLastEntry() {
        List<AdjustableEntry> upgrade = queue.getUpgradeQueue();
        AdjustableEntry last = upgrade.getLast();

        assertEquals("vanilla.biome_blend", last.settingId(),
                "Last upgrade entry should be biome_blend (least impactful to restore)");
    }

    // ---------------------------------------------------------------
    // All entries have IDs
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Every downgrade entry has a non-null settingId")
    void testAllDowngradeEntriesHaveIds() {
        for (AdjustableEntry entry : queue.getDowngradeQueue()) {
            assertNotNull(entry.settingId(),
                    "Downgrade entry at priority " + entry.priority() + " should have a non-null settingId");
            assertFalse(entry.settingId().isBlank(),
                    "Downgrade entry at priority " + entry.priority() + " should have a non-blank settingId");
        }
    }

    @Test
    @DisplayName("Every emergency entry has a non-null settingId")
    void testAllEmergencyEntriesHaveIds() {
        for (AdjustableEntry entry : queue.getEmergencyQueue()) {
            assertNotNull(entry.settingId(),
                    "Emergency entry at priority " + entry.priority() + " should have a non-null settingId");
            assertFalse(entry.settingId().isBlank(),
                    "Emergency entry at priority " + entry.priority() + " should have a non-blank settingId");
        }
    }

    @Test
    @DisplayName("Every upgrade entry has a non-null settingId")
    void testAllUpgradeEntriesHaveIds() {
        for (AdjustableEntry entry : queue.getUpgradeQueue()) {
            assertNotNull(entry.settingId(),
                    "Upgrade entry at priority " + entry.priority() + " should have a non-null settingId");
            assertFalse(entry.settingId().isBlank(),
                    "Upgrade entry at priority " + entry.priority() + " should have a non-blank settingId");
        }
    }

    @Test
    @DisplayName("Every entry across all queues has a non-null displayName")
    void testAllEntriesHaveDisplayNames() {
        for (AdjustableEntry entry : queue.getDowngradeQueue()) {
            assertNotNull(entry.displayName(), "displayName should not be null for " + entry.settingId());
        }
        for (AdjustableEntry entry : queue.getEmergencyQueue()) {
            assertNotNull(entry.displayName(), "displayName should not be null for " + entry.settingId());
        }
        for (AdjustableEntry entry : queue.getUpgradeQueue()) {
            assertNotNull(entry.displayName(), "displayName should not be null for " + entry.settingId());
        }
    }

    // ---------------------------------------------------------------
    // AdjustType.opposite()
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("AdjustType.opposite()")
    class AdjustTypeOppositeTest {

        @Test
        @DisplayName("STEP_DOWN.opposite() is STEP_UP")
        void testStepDownOpposite() {
            assertEquals(AdjustType.STEP_UP, AdjustType.STEP_DOWN.opposite());
        }

        @Test
        @DisplayName("STEP_UP.opposite() is STEP_DOWN")
        void testStepUpOpposite() {
            assertEquals(AdjustType.STEP_DOWN, AdjustType.STEP_UP.opposite());
        }

        @Test
        @DisplayName("REDUCE_10_PERCENT.opposite() is INCREASE_10_PERCENT")
        void testReduce10Opposite() {
            assertEquals(AdjustType.INCREASE_10_PERCENT, AdjustType.REDUCE_10_PERCENT.opposite());
        }

        @Test
        @DisplayName("INCREASE_10_PERCENT.opposite() is REDUCE_10_PERCENT")
        void testIncrease10Opposite() {
            assertEquals(AdjustType.REDUCE_10_PERCENT, AdjustType.INCREASE_10_PERCENT.opposite());
        }

        @Test
        @DisplayName("DISABLE.opposite() is ENABLE")
        void testDisableOpposite() {
            assertEquals(AdjustType.ENABLE, AdjustType.DISABLE.opposite());
        }

        @Test
        @DisplayName("ENABLE.opposite() is DISABLE")
        void testEnableOpposite() {
            assertEquals(AdjustType.DISABLE, AdjustType.ENABLE.opposite());
        }

        @Test
        @DisplayName("DROP_4.opposite() is STEP_UP")
        void testDrop4Opposite() {
            assertEquals(AdjustType.STEP_UP, AdjustType.DROP_4.opposite());
        }

        @ParameterizedTest(name = "{0}.opposite() is not null")
        @EnumSource(AdjustType.class)
        @DisplayName("Every AdjustType has a non-null opposite")
        void testAllOpposites(AdjustType type) {
            assertNotNull(type.opposite(),
                    type.name() + ".opposite() should not be null");
        }

        @Test
        @DisplayName("opposite() is symmetric for paired types: a.opposite().opposite() == a")
        void testOppositeSymmetry() {
            assertEquals(AdjustType.STEP_DOWN, AdjustType.STEP_DOWN.opposite().opposite());
            assertEquals(AdjustType.STEP_UP, AdjustType.STEP_UP.opposite().opposite());
            assertEquals(AdjustType.REDUCE_10_PERCENT, AdjustType.REDUCE_10_PERCENT.opposite().opposite());
            assertEquals(AdjustType.INCREASE_10_PERCENT, AdjustType.INCREASE_10_PERCENT.opposite().opposite());
            assertEquals(AdjustType.DISABLE, AdjustType.DISABLE.opposite().opposite());
            assertEquals(AdjustType.ENABLE, AdjustType.ENABLE.opposite().opposite());
        }

        @Test
        @DisplayName("DROP_4 is asymmetric: DROP_4 -> STEP_UP -> STEP_DOWN (not DROP_4)")
        void testDrop4Asymmetry() {
            // DROP_4 -> STEP_UP -> STEP_DOWN  (not back to DROP_4)
            assertEquals(AdjustType.STEP_UP, AdjustType.DROP_4.opposite());
            assertEquals(AdjustType.STEP_DOWN, AdjustType.DROP_4.opposite().opposite());
            assertNotEquals(AdjustType.DROP_4, AdjustType.DROP_4.opposite().opposite(),
                    "DROP_4 is intentionally asymmetric: recovery uses STEP_UP, not DROP_4's inverse");
        }
    }

    // ---------------------------------------------------------------
    // Upgrade types are the opposite of their downgrade counterparts
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Each upgrade entry type is the opposite of its corresponding downgrade entry type")
    void testUpgradeTypesAreOppositeOfDowngrade() {
        List<AdjustableEntry> downgrade = queue.getDowngradeQueue();
        List<AdjustableEntry> upgrade = queue.getUpgradeQueue();

        for (int i = 0; i < downgrade.size(); i++) {
            int reverseIndex = downgrade.size() - 1 - i;
            AdjustableEntry downgradeEntry = downgrade.get(reverseIndex);
            AdjustableEntry upgradeEntry = upgrade.get(i);

            assertEquals(downgradeEntry.type().opposite(), upgradeEntry.type(),
                    "Upgrade type for " + upgradeEntry.settingId()
                            + " should be the opposite of its downgrade type");
        }
    }

    // ---------------------------------------------------------------
    // Immutability of returned lists
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Downgrade queue is unmodifiable")
    void testDowngradeQueueImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> queue.getDowngradeQueue().clear(),
                "Downgrade queue should be unmodifiable");
    }

    @Test
    @DisplayName("Emergency queue is unmodifiable")
    void testEmergencyQueueImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> queue.getEmergencyQueue().clear(),
                "Emergency queue should be unmodifiable");
    }

    @Test
    @DisplayName("Upgrade queue is unmodifiable")
    void testUpgradeQueueImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> queue.getUpgradeQueue().clear(),
                "Upgrade queue should be unmodifiable");
    }
}
