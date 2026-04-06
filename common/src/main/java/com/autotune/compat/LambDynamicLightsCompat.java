package com.autotune.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compatibility handler for LambDynamicLights. Reads and writes the
 * lambdynlights.json config from the Minecraft config directory.
 * Provides access to dynamic lighting mode and entity light source settings.
 */
public class LambDynamicLightsCompat implements ModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(LambDynamicLightsCompat.class);
    private static final String CONFIG_FILE = "lambdynlights.json";

    private static final String KEY_MODE = "mode";
    private static final String KEY_ENTITY_LIGHTING = "light_sources.entities";
    private static final String KEY_BLOCK_ENTITY_LIGHTING = "light_sources.block_entities";

    private final Gson gson;
    private final Path configPath;

    public LambDynamicLightsCompat() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    @Override
    public String getModId() {
        return "lambdynlights";
    }

    @Override
    public String getModName() {
        return "LambDynamicLights";
    }

    @Override
    public boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(getModId());
    }

    @Override
    public void initialize() {
        LOGGER.info("LambDynamicLights compat initialized. Config path: {}", configPath);
    }

    /**
     * Reads the full LambDynamicLights configuration from the JSON file.
     *
     * @return the config as a map, or empty map on failure
     */
    public Map<String, Object> readConfig() {
        if (!Files.exists(configPath)) {
            LOGGER.warn("LambDynamicLights config not found at {}", configPath);
            return new LinkedHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Type type = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();
            Map<String, Object> config = gson.fromJson(reader, type);
            if (config == null) {
                return new LinkedHashMap<>();
            }
            LOGGER.debug("Read LambDynamicLights config with {} keys", config.size());
            return config;
        } catch (IOException e) {
            LOGGER.error("Failed to read LambDynamicLights config from {}", configPath, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * Writes the full configuration to the LambDynamicLights config file.
     *
     * @param config the configuration to persist
     */
    public void writeConfig(Map<String, Object> config) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(config, writer);
            }
            LOGGER.debug("Wrote LambDynamicLights config with {} keys to {}", config.size(), configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write LambDynamicLights config to {}", configPath, e);
        }
    }

    /**
     * Gets a single config value by key.
     */
    public Object getSettingValue(String key) {
        return readConfig().get(key);
    }

    /**
     * Sets a single config value and writes the file.
     */
    public void setSettingValue(String key, Object value) {
        Map<String, Object> config = readConfig();
        config.put(key, value);
        writeConfig(config);
        LOGGER.info("Set LambDynamicLights setting '{}' = {}", key, value);
    }

    /**
     * Returns the current dynamic lighting mode. Common values: "OFF", "FASTEST", "FAST", "FANCY".
     */
    public String getDynamicLightingMode() {
        Object value = getSettingValue(KEY_MODE);
        return value != null ? value.toString() : "OFF";
    }

    /**
     * Sets the dynamic lighting mode.
     *
     * @param mode one of "OFF", "FASTEST", "FAST", "FANCY"
     */
    public void setDynamicLightingMode(String mode) {
        setSettingValue(KEY_MODE, mode);
        LOGGER.info("Set LambDynamicLights mode = {}", mode);
    }

    /**
     * Returns whether entity light sources are enabled.
     */
    public boolean isEntityLightingEnabled() {
        Object value = getSettingValue(KEY_ENTITY_LIGHTING);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * Enables or disables entity light sources.
     */
    public void setEntityLightingEnabled(boolean enabled) {
        setSettingValue(KEY_ENTITY_LIGHTING, enabled);
    }

    /**
     * Returns whether block entity light sources are enabled.
     */
    public boolean isBlockEntityLightingEnabled() {
        Object value = getSettingValue(KEY_BLOCK_ENTITY_LIGHTING);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    /**
     * Enables or disables block entity light sources.
     */
    public void setBlockEntityLightingEnabled(boolean enabled) {
        setSettingValue(KEY_BLOCK_ENTITY_LIGHTING, enabled);
    }
}
