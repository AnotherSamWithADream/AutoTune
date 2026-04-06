package com.autotune.config;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigMigrator.class);

    public JsonObject migrate(JsonObject config, int fromVersion, int toVersion) {
        if (fromVersion >= toVersion) {
            LOGGER.debug("No migration needed: current version {} >= target version {}", fromVersion, toVersion);
            return config;
        }

        LOGGER.info("Starting config migration from version {} to version {}", fromVersion, toVersion);
        JsonObject migrated = config.deepCopy();

        for (int version = fromVersion; version < toVersion; version++) {
            migrated = applyMigration(migrated, version);
        }

        migrated.addProperty("configSchemaVersion", toVersion);
        LOGGER.info("Config migration complete. Now at version {}", toVersion);
        return migrated;
    }

    private JsonObject applyMigration(JsonObject config, int fromVersion) {
        if (fromVersion == 0) {
            return migrateV0ToV1(config);
        }
        LOGGER.warn("No migration path defined from version {}, returning config as-is", fromVersion);
        return config;
    }

    private JsonObject migrateV0ToV1(JsonObject config) {
        LOGGER.info("Migrating config from v0 to v1");

        if (!config.has("liveModeConfig")) {
            JsonObject liveModeConfig = new JsonObject();
            liveModeConfig.addProperty("enabled", false);
            liveModeConfig.addProperty("mode", "full");
            liveModeConfig.addProperty("evaluationIntervalMs", 500);
            liveModeConfig.addProperty("adjustmentCooldownMs", 3000);
            liveModeConfig.addProperty("measurementWindowMs", 2000);
            liveModeConfig.addProperty("hysteresisPercent", 5.0f);
            liveModeConfig.addProperty("boostThresholdPercent", 20.0f);
            liveModeConfig.addProperty("boostSustainSeconds", 15);
            liveModeConfig.addProperty("emergencyDurationMs", 2000);
            liveModeConfig.addProperty("oscillationLockMinutes", 5);
            config.add("liveModeConfig", liveModeConfig);
        }

        if (!config.has("showLiveModeHud")) {
            config.addProperty("showLiveModeHud", true);
        }

        if (!config.has("showToastNotifications")) {
            config.addProperty("showToastNotifications", true);
        }

        if (!config.has("showFpsOverlay")) {
            config.addProperty("showFpsOverlay", true);
        }

        if (!config.has("activeProfileName")) {
            config.addProperty("activeProfileName", "default");
        }

        if (!config.has("targetFps")) {
            config.addProperty("targetFps", 60);
        }

        if (!config.has("floorFps")) {
            config.addProperty("floorFps", 30);
        }

        return config;
    }
}
