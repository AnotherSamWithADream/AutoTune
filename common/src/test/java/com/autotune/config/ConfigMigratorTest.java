package com.autotune.config;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigMigrator")
class ConfigMigratorTest {

    private ConfigMigrator migrator;

    @BeforeEach
    void setUp() {
        migrator = new ConfigMigrator();
    }

    // -----------------------------------------------------------------------
    // V0 -> V1 migration
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("V0 to V1 migration")
    class V0ToV1 {

        @Test
        @DisplayName("V0 config missing all new fields should gain them after migration to V1")
        void testMigrateV0ToV1() {
            JsonObject v0Config = new JsonObject();
            v0Config.addProperty("configSchemaVersion", 0);
            // Deliberately has no liveModeConfig, no showLiveModeHud, etc.

            JsonObject migrated = migrator.migrate(v0Config, 0, 1);

            assertAll("V0 -> V1 migration should add all required new fields",
                    () -> assertTrue(migrated.has("liveModeConfig"),
                            "Should add liveModeConfig object"),
                    () -> assertTrue(migrated.has("showLiveModeHud"),
                            "Should add showLiveModeHud"),
                    () -> assertTrue(migrated.has("showToastNotifications"),
                            "Should add showToastNotifications"),
                    () -> assertTrue(migrated.has("showFpsOverlay"),
                            "Should add showFpsOverlay"),
                    () -> assertTrue(migrated.has("activeProfileName"),
                            "Should add activeProfileName"),
                    () -> assertTrue(migrated.has("targetFps"),
                            "Should add targetFps"),
                    () -> assertTrue(migrated.has("floorFps"),
                            "Should add floorFps"),
                    () -> assertEquals(1, migrated.get("configSchemaVersion").getAsInt(),
                            "Should update configSchemaVersion to 1")
            );
        }

        @Test
        @DisplayName("Migrated liveModeConfig should have all expected sub-fields")
        void testLiveModeConfigFields() {
            JsonObject v0Config = new JsonObject();

            JsonObject migrated = migrator.migrate(v0Config, 0, 1);

            assertTrue(migrated.has("liveModeConfig"), "Should have liveModeConfig");
            JsonObject lmc = migrated.getAsJsonObject("liveModeConfig");

            assertAll("liveModeConfig sub-fields",
                    () -> assertFalse(lmc.get("enabled").getAsBoolean(),
                            "enabled should default to false"),
                    () -> assertEquals("full", lmc.get("mode").getAsString(),
                            "mode should default to 'full'"),
                    () -> assertEquals(500, lmc.get("evaluationIntervalMs").getAsInt(),
                            "evaluationIntervalMs should default to 500"),
                    () -> assertEquals(3000, lmc.get("adjustmentCooldownMs").getAsInt(),
                            "adjustmentCooldownMs should default to 3000"),
                    () -> assertEquals(2000, lmc.get("measurementWindowMs").getAsInt(),
                            "measurementWindowMs should default to 2000"),
                    () -> assertEquals(5.0f, lmc.get("hysteresisPercent").getAsFloat(), 0.01,
                            "hysteresisPercent should default to 5.0"),
                    () -> assertEquals(20.0f, lmc.get("boostThresholdPercent").getAsFloat(), 0.01,
                            "boostThresholdPercent should default to 20.0"),
                    () -> assertEquals(15, lmc.get("boostSustainSeconds").getAsInt(),
                            "boostSustainSeconds should default to 15"),
                    () -> assertEquals(2000, lmc.get("emergencyDurationMs").getAsInt(),
                            "emergencyDurationMs should default to 2000"),
                    () -> assertEquals(5, lmc.get("oscillationLockMinutes").getAsInt(),
                            "oscillationLockMinutes should default to 5")
            );
        }

        @Test
        @DisplayName("Default values for top-level fields should be correct")
        void testDefaultFieldValues() {
            JsonObject v0Config = new JsonObject();

            JsonObject migrated = migrator.migrate(v0Config, 0, 1);

            assertAll("Default top-level field values",
                    () -> assertTrue(migrated.get("showLiveModeHud").getAsBoolean(),
                            "showLiveModeHud should default to true"),
                    () -> assertTrue(migrated.get("showToastNotifications").getAsBoolean(),
                            "showToastNotifications should default to true"),
                    () -> assertTrue(migrated.get("showFpsOverlay").getAsBoolean(),
                            "showFpsOverlay should default to true"),
                    () -> assertEquals("default", migrated.get("activeProfileName").getAsString(),
                            "activeProfileName should default to 'default'"),
                    () -> assertEquals(60, migrated.get("targetFps").getAsInt(),
                            "targetFps should default to 60"),
                    () -> assertEquals(30, migrated.get("floorFps").getAsInt(),
                            "floorFps should default to 30")
            );
        }
    }

    // -----------------------------------------------------------------------
    // Already current
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Already-current config")
    class AlreadyCurrent {

        @Test
        @DisplayName("V1 config with fromVersion >= toVersion should remain unchanged")
        void testAlreadyCurrent() {
            JsonObject v1Config = createValidV1Config();

            JsonObject result = migrator.migrate(v1Config, 1, 1);

            // Should return the same object (no migration needed)
            assertEquals(v1Config.get("targetFps").getAsInt(), result.get("targetFps").getAsInt(),
                    "targetFps should be unchanged");
            assertEquals(v1Config.get("activeProfileName").getAsString(),
                    result.get("activeProfileName").getAsString(),
                    "activeProfileName should be unchanged");
        }

        @Test
        @DisplayName("Higher fromVersion than toVersion should skip migration")
        void testHigherFromVersion() {
            JsonObject config = new JsonObject();
            config.addProperty("configSchemaVersion", 5);
            config.addProperty("customField", "preserved");

            JsonObject result = migrator.migrate(config, 5, 1);

            assertEquals("preserved", result.get("customField").getAsString(),
                    "Config should pass through unchanged when fromVersion > toVersion");
        }
    }

    // -----------------------------------------------------------------------
    // Preservation of existing values
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Preservation of existing values")
    class PreservesExistingValues {

        @Test
        @DisplayName("Existing fields should not be overwritten during migration")
        void testMigrationPreservesExistingValues() {
            JsonObject v0Config = new JsonObject();
            // Pre-set some fields that the migration would normally add defaults for
            v0Config.addProperty("showLiveModeHud", false);
            v0Config.addProperty("showFpsOverlay", false);
            v0Config.addProperty("activeProfileName", "my-custom-profile");
            v0Config.addProperty("targetFps", 144);
            v0Config.addProperty("floorFps", 60);

            JsonObject migrated = migrator.migrate(v0Config, 0, 1);

            assertAll("Pre-existing values should be preserved, not overwritten",
                    () -> assertFalse(migrated.get("showLiveModeHud").getAsBoolean(),
                            "showLiveModeHud was set to false and should stay false"),
                    () -> assertFalse(migrated.get("showFpsOverlay").getAsBoolean(),
                            "showFpsOverlay was set to false and should stay false"),
                    () -> assertEquals("my-custom-profile",
                            migrated.get("activeProfileName").getAsString(),
                            "activeProfileName should preserve custom value"),
                    () -> assertEquals(144, migrated.get("targetFps").getAsInt(),
                            "targetFps should preserve custom value of 144"),
                    () -> assertEquals(60, migrated.get("floorFps").getAsInt(),
                            "floorFps should preserve custom value of 60")
            );
        }

        @Test
        @DisplayName("Existing liveModeConfig should not be replaced")
        void testPreservesExistingLiveModeConfig() {
            JsonObject v0Config = new JsonObject();
            JsonObject customLmc = new JsonObject();
            customLmc.addProperty("enabled", true);
            customLmc.addProperty("mode", "conservative");
            v0Config.add("liveModeConfig", customLmc);

            JsonObject migrated = migrator.migrate(v0Config, 0, 1);

            JsonObject lmc = migrated.getAsJsonObject("liveModeConfig");
            assertTrue(lmc.get("enabled").getAsBoolean(),
                    "Existing liveModeConfig.enabled=true should be preserved");
            assertEquals("conservative", lmc.get("mode").getAsString(),
                    "Existing liveModeConfig.mode should be preserved");
        }

        @Test
        @DisplayName("Migration should not modify the original config object (uses deepCopy)")
        void testOriginalNotModified() {
            JsonObject original = new JsonObject();
            original.addProperty("configSchemaVersion", 0);

            migrator.migrate(original, 0, 1);

            assertFalse(original.has("liveModeConfig"),
                    "Original config should not be modified; migrator should use deepCopy");
            assertFalse(original.has("showLiveModeHud"),
                    "Original should not have showLiveModeHud added");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static JsonObject createValidV1Config() {
        JsonObject config = new JsonObject();
        config.addProperty("configSchemaVersion", 1);
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
