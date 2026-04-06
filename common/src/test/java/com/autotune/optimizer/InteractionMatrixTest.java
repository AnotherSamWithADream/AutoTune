package com.autotune.optimizer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InteractionMatrix")
class InteractionMatrixTest {

    private InteractionMatrix matrix;

    @BeforeEach
    void setUp() {
        matrix = new InteractionMatrix();
    }

    // -----------------------------------------------------------------------
    // Known interaction tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Known interaction pairs")
    class KnownInteractions {

        @Test
        @DisplayName("render_distance + graphics_mode should return >1.0 multiplier when both values are high")
        void testKnownInteraction() {
            // render_distance >= 16 and graphics_mode >= 2 triggers the 1.35 multiplier
            // Values must be passed in register order (render_distance value, graphics_mode value)
            // when settings are given in alphabetical order
            double mult = matrix.getMultiplier(
                    "vanilla.graphics_mode", "vanilla.render_distance", 16, 2);
            assertTrue(mult > 1.0,
                    "Expected multiplier > 1.0 for high render_distance + fancy graphics, got " + mult);
            assertEquals(1.35, mult, 0.001,
                    "Expected exactly 1.35 for render_distance=16, graphics_mode=2");
        }

        @Test
        @DisplayName("render_distance + graphics_mode returns 1.0 when values do not meet threshold")
        void testKnownInteractionBelowThreshold() {
            // render_distance < 16 should not trigger the interaction
            double mult = matrix.getMultiplier(
                    "vanilla.render_distance", "vanilla.graphics_mode", 8, 2);
            assertEquals(1.0, mult, 0.001,
                    "Expected 1.0 when render_distance is below threshold");
        }

        @Test
        @DisplayName("render_distance + simulation_distance returns 1.25 when both are high")
        void testRenderDistanceSimulationDistanceInteraction() {
            double mult = matrix.getMultiplier(
                    "vanilla.render_distance", "vanilla.simulation_distance", 16, 12);
            assertEquals(1.25, mult, 0.001);
        }

        @Test
        @DisplayName("iris shaders + render_distance returns 1.50 when both active")
        void testIrisShadersRenderDistanceInteraction() {
            double mult = matrix.getMultiplier(
                    "iris.shaders_enabled", "vanilla.render_distance", true, 16);
            assertEquals(1.50, mult, 0.001);
        }

        @Test
        @DisplayName("sodium block face culling + render_distance returns <1.0 (beneficial)")
        void testSodiumBlockFaceCullingInteraction() {
            double mult = matrix.getMultiplier(
                    "sodium.use_block_face_culling", "vanilla.render_distance", true, 12);
            assertEquals(0.85, mult, 0.001);
        }

        @Test
        @DisplayName("sodium entity culling + entity_distance_scaling returns 0.80 (beneficial)")
        void testSodiumEntityCullingInteraction() {
            double mult = matrix.getMultiplier(
                    "sodium.use_entity_culling", "vanilla.entity_distance_scaling", true, 1.0f);
            assertEquals(0.80, mult, 0.001);
        }
    }

    // -----------------------------------------------------------------------
    // Unknown / unrelated pair tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Unknown or unrelated pairs")
    class UnknownPairs {

        @Test
        @DisplayName("Two completely unrelated settings return 1.0")
        void testUnknownPairReturns1() {
            double mult = matrix.getMultiplier(
                    "some.random.setting", "another.unrelated.setting", 42, 99);
            assertEquals(1.0, mult, 0.001,
                    "Expected 1.0 for settings with no known interaction");
        }

        @Test
        @DisplayName("A known setting paired with an unknown setting returns 1.0")
        void testOneKnownOneUnknownReturns1() {
            double mult = matrix.getMultiplier(
                    "vanilla.render_distance", "some.unknown.setting", 16, 5);
            assertEquals(1.0, mult, 0.001,
                    "Expected 1.0 when one setting is not in any known pair");
        }
    }

    // -----------------------------------------------------------------------
    // Symmetric lookup tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Symmetric lookup")
    class SymmetricLookup {

        @Test
        @DisplayName("getMultiplier(A,B) == getMultiplier(B,A) for render_distance + graphics_mode")
        void testSymmetricLookup() {
            double multAB = matrix.getMultiplier(
                    "vanilla.render_distance", "vanilla.graphics_mode", 16, 2);
            double multBA = matrix.getMultiplier(
                    "vanilla.graphics_mode", "vanilla.render_distance", 2, 16);
            assertEquals(multAB, multBA, 0.001,
                    "Interaction multiplier should be symmetric regardless of argument order");
        }

        @Test
        @DisplayName("Symmetric lookup works for iris shaders + render_distance")
        void testSymmetricLookupIris() {
            double multAB = matrix.getMultiplier(
                    "iris.shaders_enabled", "vanilla.render_distance", true, 16);
            double multBA = matrix.getMultiplier(
                    "vanilla.render_distance", "iris.shaders_enabled", 16, true);
            assertEquals(multAB, multBA, 0.001,
                    "Symmetric lookup should work for iris shaders + render_distance");
        }

        @Test
        @DisplayName("Symmetric lookup works for sodium entity culling pair")
        void testSymmetricLookupSodium() {
            double multAB = matrix.getMultiplier(
                    "sodium.use_entity_culling", "vanilla.entity_distance_scaling", true, 1.5f);
            double multBA = matrix.getMultiplier(
                    "vanilla.entity_distance_scaling", "sodium.use_entity_culling", 1.5f, true);
            assertEquals(multAB, multBA, 0.001);
        }
    }

    // -----------------------------------------------------------------------
    // Null handling tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Null handling")
    class NullHandling {

        @Test
        @DisplayName("Null values for a known pair should not crash, returns 1.0")
        void testNullValues() {
            assertDoesNotThrow(() -> {
                double mult = matrix.getMultiplier(
                        "vanilla.render_distance", "vanilla.graphics_mode", null, null);
                assertEquals(1.0, mult, 0.001,
                        "Null values should safely return 1.0 (condition not met)");
            });
        }

        @Test
        @DisplayName("One null value should not crash")
        void testOneNullValue() {
            assertDoesNotThrow(() -> {
                double mult = matrix.getMultiplier(
                        "vanilla.render_distance", "vanilla.graphics_mode", 16, null);
                assertEquals(1.0, mult, 0.001,
                        "One null value should return 1.0");
            });
        }

        @Test
        @DisplayName("Null values for an unknown pair should not crash")
        void testNullValuesUnknownPair() {
            assertDoesNotThrow(() -> {
                double mult = matrix.getMultiplier(
                        "unknown.a", "unknown.b", null, null);
                assertEquals(1.0, mult, 0.001);
            });
        }
    }

    // -----------------------------------------------------------------------
    // getInteractionsFor tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getInteractionsFor")
    class GetInteractionsFor {

        @Test
        @DisplayName("Should return active interactions for render_distance when values are high")
        void testGetInteractionsForRenderDistance() {
            Map<String, Object> values = new HashMap<>();
            values.put("vanilla.render_distance", 20);
            values.put("vanilla.graphics_mode", 2);
            values.put("vanilla.simulation_distance", 14);

            Map<String, Double> interactions = matrix.getInteractionsFor("vanilla.render_distance", values);

            assertFalse(interactions.isEmpty(),
                    "Should find at least one interaction for render_distance with high values");
            assertTrue(interactions.containsKey("vanilla.simulation_distance"),
                    "Should include simulation_distance interaction");
        }

        @Test
        @DisplayName("Should return empty map for a setting with no registered interactions")
        void testGetInteractionsForUnknownSetting() {
            Map<String, Object> values = new HashMap<>();
            values.put("unknown.setting", 5);
            values.put("vanilla.render_distance", 16);

            Map<String, Double> interactions = matrix.getInteractionsFor("unknown.setting", values);
            assertTrue(interactions.isEmpty(),
                    "Unknown setting should have no interactions");
        }
    }
}
