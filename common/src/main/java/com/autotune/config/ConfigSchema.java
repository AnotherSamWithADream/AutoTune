package com.autotune.config;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigSchema {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigSchema.class);

    public static final int CURRENT_VERSION = 1;

    public static boolean validateSchema(JsonObject config) {
        if (config == null) {
            LOGGER.error("Config validation failed: config is null");
            return false;
        }

        if (!config.has("configSchemaVersion")) {
            LOGGER.warn("Config missing 'configSchemaVersion' field");
            return false;
        }

        int version = config.get("configSchemaVersion").getAsInt();
        if (version < 1 || version > CURRENT_VERSION) {
            LOGGER.error("Config validation failed: unsupported schema version {}", version);
            return false;
        }

        if (!config.has("targetFps")) {
            LOGGER.warn("Config missing 'targetFps' field");
            return false;
        }

        if (!config.has("floorFps")) {
            LOGGER.warn("Config missing 'floorFps' field");
            return false;
        }

        if (!config.has("activeProfileName")) {
            LOGGER.warn("Config missing 'activeProfileName' field");
            return false;
        }

        if (!config.has("liveModeConfig")) {
            LOGGER.warn("Config missing 'liveModeConfig' field");
            return false;
        }

        JsonObject liveModeConfig = config.getAsJsonObject("liveModeConfig");
        if (liveModeConfig == null) {
            LOGGER.warn("Config 'liveModeConfig' is not a valid object");
            return false;
        }

        if (!liveModeConfig.has("mode")) {
            LOGGER.warn("LiveModeConfig missing 'mode' field");
            return false;
        }

        String mode = liveModeConfig.get("mode").getAsString();
        if (!"full".equals(mode) && !"conservative".equals(mode) && !"static".equals(mode)) {
            LOGGER.warn("LiveModeConfig has invalid mode '{}', expected 'full', 'conservative', or 'static'", mode);
            return false;
        }

        if (config.has("targetFps") && config.has("floorFps")) {
            int targetFps = config.get("targetFps").getAsInt();
            int floorFps = config.get("floorFps").getAsInt();
            if (floorFps > targetFps) {
                LOGGER.warn("Config validation warning: floorFps ({}) is greater than targetFps ({})", floorFps, targetFps);
                return false;
            }
            if (targetFps <= 0) {
                LOGGER.warn("Config validation failed: targetFps must be positive, got {}", targetFps);
                return false;
            }
            if (floorFps <= 0) {
                LOGGER.warn("Config validation failed: floorFps must be positive, got {}", floorFps);
                return false;
            }
        }

        LOGGER.debug("Config schema validation passed for version {}", version);
        return true;
    }
}
