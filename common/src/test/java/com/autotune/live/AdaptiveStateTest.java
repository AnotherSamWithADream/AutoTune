package com.autotune.live;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveStateTest {

    // ---------------------------------------------------------------
    // All states exist
    // ---------------------------------------------------------------

    @Test
    @DisplayName("All 6 expected AdaptiveState values exist")
    void testAllStatesExist() {
        AdaptiveState[] values = AdaptiveState.values();
        assertEquals(6, values.length, "AdaptiveState should have exactly 6 enum constants");

        // Verify each expected constant is present
        assertNotNull(AdaptiveState.STABLE);
        assertNotNull(AdaptiveState.DEGRADING);
        assertNotNull(AdaptiveState.RECOVERING);
        assertNotNull(AdaptiveState.BOOSTING);
        assertNotNull(AdaptiveState.EMERGENCY);
        assertNotNull(AdaptiveState.LOCKED);
    }

    // ---------------------------------------------------------------
    // Display names
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "{0} should have a non-null display name")
    @EnumSource(AdaptiveState.class)
    @DisplayName("Every AdaptiveState has a non-null, non-empty display name")
    void testDisplayNames(AdaptiveState state) {
        assertNotNull(state.getDisplayName(),
                state.name() + " should have a non-null display name");
        assertFalse(state.getDisplayName().isBlank(),
                state.name() + " display name should not be blank");
    }

    @Test
    @DisplayName("Display names match expected values")
    void testDisplayNameValues() {
        assertEquals("Stable", AdaptiveState.STABLE.getDisplayName());
        assertEquals("Degrading", AdaptiveState.DEGRADING.getDisplayName());
        assertEquals("Recovering", AdaptiveState.RECOVERING.getDisplayName());
        assertEquals("Boosting", AdaptiveState.BOOSTING.getDisplayName());
        assertEquals("Emergency", AdaptiveState.EMERGENCY.getDisplayName());
        assertEquals("Locked", AdaptiveState.LOCKED.getDisplayName());
    }

    // ---------------------------------------------------------------
    // Icons
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "{0} should have a non-null icon")
    @EnumSource(AdaptiveState.class)
    @DisplayName("Every AdaptiveState has a non-null, non-empty icon")
    void testIcons(AdaptiveState state) {
        assertNotNull(state.getIcon(),
                state.name() + " should have a non-null icon");
        assertFalse(state.getIcon().isEmpty(),
                state.name() + " icon should not be empty");
    }

    @Test
    @DisplayName("STABLE icon is a green checkmark")
    void testStableIcon() {
        assertEquals("\u2705", AdaptiveState.STABLE.getIcon());
    }

    @Test
    @DisplayName("BOOSTING icon is an up arrow")
    void testBoostingIcon() {
        assertEquals("\u2191", AdaptiveState.BOOSTING.getIcon());
    }

    @Test
    @DisplayName("RECOVERING icon is a cycle arrow")
    void testRecoveringIcon() {
        assertEquals("\u21BB", AdaptiveState.RECOVERING.getIcon());
    }

    // ---------------------------------------------------------------
    // valueOf
    // ---------------------------------------------------------------

    @Test
    @DisplayName("valueOf works for all state names")
    void testValueOf() {
        assertEquals(AdaptiveState.STABLE, AdaptiveState.valueOf("STABLE"));
        assertEquals(AdaptiveState.DEGRADING, AdaptiveState.valueOf("DEGRADING"));
        assertEquals(AdaptiveState.RECOVERING, AdaptiveState.valueOf("RECOVERING"));
        assertEquals(AdaptiveState.BOOSTING, AdaptiveState.valueOf("BOOSTING"));
        assertEquals(AdaptiveState.EMERGENCY, AdaptiveState.valueOf("EMERGENCY"));
        assertEquals(AdaptiveState.LOCKED, AdaptiveState.valueOf("LOCKED"));
    }

    @Test
    @DisplayName("valueOf throws IllegalArgumentException for unknown state")
    void testValueOfInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> AdaptiveState.valueOf("NONEXISTENT"),
                "valueOf with an unknown name should throw IllegalArgumentException");
    }

    // ---------------------------------------------------------------
    // Ordinal consistency
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Enum ordinals are in declaration order")
    void testOrdinals() {
        assertEquals(0, AdaptiveState.STABLE.ordinal());
        assertEquals(1, AdaptiveState.DEGRADING.ordinal());
        assertEquals(2, AdaptiveState.RECOVERING.ordinal());
        assertEquals(3, AdaptiveState.BOOSTING.ordinal());
        assertEquals(4, AdaptiveState.EMERGENCY.ordinal());
        assertEquals(5, AdaptiveState.LOCKED.ordinal());
    }

    // ---------------------------------------------------------------
    // toString
    // ---------------------------------------------------------------

    @ParameterizedTest(name = "{0}.toString() equals its name")
    @EnumSource(AdaptiveState.class)
    @DisplayName("Default toString returns the enum constant name")
    void testToString(AdaptiveState state) {
        assertEquals(state.name(), state.toString(),
                "Default enum toString should match name()");
    }
}
