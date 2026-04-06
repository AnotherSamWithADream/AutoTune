package com.autotune.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigSchema")
class ConfigSchemaTest {

    // -----------------------------------------------------------------------
    // CURRENT_VERSION constant
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("CURRENT_VERSION constant should exist and be a positive integer")
    void testCurrentVersion() {
        int version = ConfigSchema.CURRENT_VERSION;
        assertTrue(version >= 1,
                "CURRENT_VERSION should be >= 1, got " + version);
    }

    // -----------------------------------------------------------------------
    // Valid schema tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Valid schema validation")
    class ValidSchema {

        @Test
        @DisplayName("Complete valid config should pass validation")
        void testValidateValidSchema() {
            JsonObject config = createCompleteValidConfig();
            assertTrue(ConfigSchema.validateSchema(config),
                    "A complete, valid config should pass validation");
        }

        @Test
        @DisplayName("Valid config with 'conservative' mode should pass")
        void testValidConservativeMode() {
            JsonObject config = createCompleteValidConfig();
            config.getAsJsonObject("liveModeConfig").addProperty("mode", "conservative");
            assertTrue(ConfigSchema.validateSchema(config),
                    "Config with 'conservative' mode should pass validation");
        }

        @Test
        @DisplayName("Valid config with 'static' mode should pass")
        void testValidStaticMode() {
            JsonObject config = createCompleteValidConfig();
            config.getAsJsonObject("liveModeConfig").addProperty("mode", "static");
            assertTrue(ConfigSchema.validateSchema(config),
                    "Config with 'static' mode should pass validation");
        }
    }

    // -----------------------------------------------------------------------
    // Missing fields tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Missing fields validation")
    class MissingFields {

        @Test
        @DisplayName("Null config should fail validation")
        void testValidateNullConfig() {
            assertFalse(ConfigSchema.validateSchema(null),
                    "Null config should fail validation");
        }

        @Test
        @DisplayName("Config missing configSchemaVersion should fail")
        void testMissingSchemaVersion() {
            JsonObject config = createCompleteValidConfig();
            config.remove("configSchemaVersion");
            assertFalse(ConfigSchema.validateSchema(config),
                    "Missing configSchemaVersion should fail validation");
        }

        @Test
        @DisplayName("Config missing targetFps should fail")
        void testMissingTargetFps() {
            JsonObject config = createCompleteValidConfig();
            config.remove("targetFps");
            assertFalse(ConfigSchema.validateSchema(config),
                    "Missing targetFps should fail validation");
        }

        @Test
        @DisplayName("Config missing floorFps should fail")
        void testMissingFloorFps() {
            JsonObject config = createCompleteValidConfig();
            config.remove("floorFps");
            assertFalse(ConfigSchema.validateSchema(config),
                    "Missing floorFps should fail validation");
        }

        @Test
        @DisplayName("Config missing activeProfileName should fail")
        void testMissingActiveProfileName() {
            JsonObject config = createCompleteValidConfig();
            config.remove("activeProfileName");
            assertFalse(ConfigSchema.validateSchema(config),
                    "Missing activeProfileName should fail validation");
        }

        @Test
        @DisplayName("Config missing liveModeConfig should fail")
        void testMissingLiveModeConfig() {
            JsonObject config = createCompleteValidConfig();
            config.remove("liveModeConfig");
            assertFalse(ConfigSchema.validateSchema(config),
                    "Missing liveModeConfig should fail validation");
        }

        @Test
        @DisplayName("liveModeConfig missing 'mode' field should fail")
        void testMissingLiveModeConfigMode() {
            JsonObject config = createCompleteValidConfig();
            config.getAsJsonObject("liveModeConfig").remove("mode");
            assertFalse(ConfigSchema.validateSchema(config),
                    "Missing liveModeConfig.mode should fail validation");
        }
    }

    // -----------------------------------------------------------------------
    // Invalid FPS values
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Invalid FPS validation")
    class InvalidFps {

        @Test
        @DisplayName("Negative targetFps should fail validation")
        void testNegativeTargetFps() {
            JsonObject config = createCompleteValidConfig();
            config.addProperty("targetFps", -60);
            assertFalse(ConfigSchema.validateSchema(config),
                    "Negative targetFps should fail validation");
        }

        @Test
        @DisplayName("Zero targetFps should fail validation")
        void testZeroTargetFps() {
            JsonObject config = createCompleteValidConfig();
            config.addProperty("targetFps", 0);
            assertFalse(ConfigSchema.validateSchema(config),
                    "Zero targetFps should fail validation");
        }

        @Test
        @DisplayName("Negative floorFps should fail validation")
        void testNegativeFloorFps() {
            JsonObject config = createCompleteValidConfig();
            config.addProperty("floorFps", -10);
            assertFalse(ConfigSchema.validateSchema(config),
                    "Negative floorFps should fail validation");
        }

        @Test
        @DisplayName("floorFps > targetFps should fail validation")
        void testFloorFpsGreaterThanTargetFps() {
            JsonObject config = createCompleteValidConfig();
            config.addProperty("targetFps", 60);
            config.addProperty("floorFps", 90);
            assertFalse(ConfigSchema.validateSchema(config),
                    "floorFps (90) > targetFps (60) should fail validation");
        }

        @Test
        @DisplayName("floorFps == targetFps should pass validation (edge case)")
        void testFloorFpsEqualsTargetFps() {
            JsonObject config = createCompleteValidConfig();
            config.addProperty("targetFps", 60);
            config.addProperty("floorFps", 60);
            assertTrue(ConfigSchema.validateSchema(config),
                    "floorFps == targetFps should be valid");
        }
    }

    // -----------------------------------------------------------------------
    // Invalid schema version
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Invalid schema version")
    class InvalidVersion {

        @Test
        @DisplayName("Schema version 0 should fail validation")
        void testSchemaVersionZero() {
            JsonObject config = createCompleteValidConfig();
            config.addProperty("configSchemaVersion", 0);
            assertFalse(ConfigSchema.validateSchema(config),
                    "Schema version 0 should fail (must be >= 1)");
        }

        @Test
        @DisplayName("Schema version above CURRENT_VERSION should fail")
        void testSchemaVersionTooHigh() {
            JsonObject config = createCompleteValidConfig();
            config.addProperty("configSchemaVersion", ConfigSchema.CURRENT_VERSION + 1);
            assertFalse(ConfigSchema.validateSchema(config),
                    "Schema version above CURRENT_VERSION should fail");
        }
    }

    // -----------------------------------------------------------------------
    // Invalid liveModeConfig mode
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Invalid liveModeConfig.mode value should fail validation")
    void testInvalidLiveModeConfigMode() {
        JsonObject config = createCompleteValidConfig();
        config.getAsJsonObject("liveModeConfig").addProperty("mode", "turbo");
        assertFalse(ConfigSchema.validateSchema(config),
                "Invalid mode 'turbo' should fail validation");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static JsonObject createCompleteValidConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("configSchemaVersion", ConfigSchema.CURRENT_VERSION);
        config.addProperty("targetFps", 60);
        config.addProperty("floorFps", 30);
        config.addProperty("activeProfileName", "default");
        config.addProperty("showLiveModeHud", true);
        config.addProperty("showToastNotifications", true);
        config.addProperty("showFpsOverlay", true);

        JsonObject lmc = new JsonObject();
        lmc.addProperty("enabled", false);
        lmc.addProperty("mode", "full");
        lmc.addProperty("evaluationIntervalMs", 500);
        lmc.addProperty("adjustmentCooldownMs", 3000);
        lmc.addProperty("measurementWindowMs", 2000);
        lmc.addProperty("hysteresisPercent", 5.0f);
        lmc.addProperty("boostThresholdPercent", 20.0f);
        lmc.addProperty("boostSustainSeconds", 15);
        lmc.addProperty("emergencyDurationMs", 2000);
        lmc.addProperty("oscillationLockMinutes", 5);
        config.add("liveModeConfig", lmc);

        return config;
    }
}
