package com.autotune.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility handler for the Entity Culling mod. Reads and writes
 * entityculling.toml from the Minecraft config directory. Provides access
 * to key settings such as debug mode, async culling, and skip thresholds.
 */
public class EntityCullingCompat implements ModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityCullingCompat.class);
    private static final String CONFIG_FILE = "entityculling.toml";

    private final Path configPath;

    public EntityCullingCompat() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    @Override
    public String getModId() {
        return "entityculling";
    }

    @Override
    public String getModName() {
        return "Entity Culling";
    }

    @Override
    public boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(getModId());
    }

    @Override
    public void initialize() {
        LOGGER.info("Entity Culling compat initialized. Config path: {}", configPath);
    }

    /**
     * Reads the entityculling.toml config into a map of key-value pairs.
     * This is a simple TOML parser that handles basic key = value lines
     * and ignores section headers and comments.
     *
     * @return map of setting keys to their string values
     */
    public Map<String, String> readConfig() {
        Map<String, String> config = new LinkedHashMap<>();

        if (!Files.exists(configPath)) {
            LOGGER.warn("Entity Culling config not found at {}", configPath);
            return config;
        }

        try {
            List<String> lines = Files.readAllLines(configPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                    continue;
                }
                int eqIndex = trimmed.indexOf('=');
                if (eqIndex > 0) {
                    String key = trimmed.substring(0, eqIndex).trim();
                    String value = trimmed.substring(eqIndex + 1).trim();
                    // Strip surrounding quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    config.put(key, value);
                }
            }
            LOGGER.debug("Read Entity Culling config with {} keys", config.size());
        } catch (IOException e) {
            LOGGER.error("Failed to read Entity Culling config from {}", configPath, e);
        }

        return config;
    }

    /**
     * Writes the config map back to the entityculling.toml file.
     * Re-reads the original file to preserve comments and section structure,
     * updating values in-place and appending any new keys at the end.
     *
     * @param config the configuration key-value pairs to write
     */
    public void writeConfig(Map<String, String> config) {
        try {
            List<String> outputLines = new ArrayList<>();
            Map<String, String> remaining = new LinkedHashMap<>(config);

            if (Files.exists(configPath)) {
                List<String> existingLines = Files.readAllLines(configPath);
                for (String line : existingLines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                        outputLines.add(line);
                        continue;
                    }
                    int eqIndex = trimmed.indexOf('=');
                    if (eqIndex > 0) {
                        String key = trimmed.substring(0, eqIndex).trim();
                        if (remaining.containsKey(key)) {
                            outputLines.add(key + " = " + remaining.get(key));
                            remaining.remove(key);
                        } else {
                            outputLines.add(line);
                        }
                    } else {
                        outputLines.add(line);
                    }
                }
            }

            // Append any new keys that were not in the original file
            for (Map.Entry<String, String> entry : remaining.entrySet()) {
                outputLines.add(entry.getKey() + " = " + entry.getValue());
            }

            Files.createDirectories(configPath.getParent());
            Files.write(configPath, outputLines);
            LOGGER.debug("Wrote Entity Culling config with {} keys to {}", config.size(), configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write Entity Culling config to {}", configPath, e);
        }
    }

    /**
     * Gets a single config value by key.
     */
    public String getSettingValue(String key) {
        return readConfig().get(key);
    }

    /**
     * Sets a single config value and writes the file.
     */
    public void setSettingValue(String key, String value) {
        Map<String, String> config = readConfig();
        config.put(key, value);
        writeConfig(config);
        LOGGER.info("Set Entity Culling setting '{}' = {}", key, value);
    }

    /**
     * Returns whether debug rendering is enabled.
     */
    public boolean isDebugEnabled() {
        String value = getSettingValue("debug");
        return "true".equalsIgnoreCase(value);
    }

    /**
     * Enables or disables debug rendering.
     */
    public void setDebugEnabled(boolean enabled) {
        setSettingValue("debug", String.valueOf(enabled));
    }
}
