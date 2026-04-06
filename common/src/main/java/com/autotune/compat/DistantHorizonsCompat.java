package com.autotune.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compatibility handler for Distant Horizons. Detects whether the mod is loaded
 * and provides access to the LOD (Level of Detail) render distance configuration
 * stored in config/DistantHorizons.toml (parsed as simple key-value pairs).
 */
public class DistantHorizonsCompat implements ModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(DistantHorizonsCompat.class);
    private static final String CONFIG_FILE = "DistantHorizons.toml";

    private static final String KEY_LOD_CHUNK_RENDER_DISTANCE = "maxHorizontalResolution";
    private static final String KEY_LOD_QUALITY = "lodQuality";
    private static final String KEY_ENABLED = "enableRendering";

    private final Path configPath;

    public DistantHorizonsCompat() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    @Override
    public String getModId() {
        return "distanthorizons";
    }

    @Override
    public String getModName() {
        return "Distant Horizons";
    }

    @Override
    public boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(getModId());
    }

    @Override
    public void initialize() {
        LOGGER.info("Distant Horizons compat initialized. Config path: {}", configPath);
    }

    /**
     * Reads the Distant Horizons TOML config into a map of key-value pairs.
     * Handles basic TOML: key = value lines, ignoring comments and section headers.
     *
     * @return map of setting keys to their string values
     */
    public Map<String, String> readConfig() {
        Map<String, String> config = new LinkedHashMap<>();

        if (!Files.exists(configPath)) {
            LOGGER.warn("Distant Horizons config not found at {}", configPath);
            return config;
        }

        try {
            for (String line : Files.readAllLines(configPath)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                    continue;
                }
                int eqIndex = trimmed.indexOf('=');
                if (eqIndex > 0) {
                    String key = trimmed.substring(0, eqIndex).trim();
                    String value = trimmed.substring(eqIndex + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    }
                    config.put(key, value);
                }
            }
            LOGGER.debug("Read Distant Horizons config with {} keys", config.size());
        } catch (IOException e) {
            LOGGER.error("Failed to read Distant Horizons config from {}", configPath, e);
        }

        return config;
    }

    /**
     * Writes settings back to the TOML config file, preserving comments and
     * section structure from the original file where possible.
     */
    public void writeConfig(Map<String, String> config) {
        try {
            java.util.List<String> outputLines = new java.util.ArrayList<>();
            Map<String, String> remaining = new LinkedHashMap<>(config);

            if (Files.exists(configPath)) {
                for (String line : Files.readAllLines(configPath)) {
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

            for (Map.Entry<String, String> entry : remaining.entrySet()) {
                outputLines.add(entry.getKey() + " = " + entry.getValue());
            }

            Files.createDirectories(configPath.getParent());
            Files.write(configPath, outputLines);
            LOGGER.debug("Wrote Distant Horizons config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write Distant Horizons config to {}", configPath, e);
        }
    }

    /**
     * Returns the LOD chunk render distance. Defaults to 128 if not set.
     */
    public int getLodRenderDistance() {
        String value = readConfig().get(KEY_LOD_CHUNK_RENDER_DISTANCE);
        if (value == null) {
            return 128;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid LOD render distance value '{}', defaulting to 128", value);
            return 128;
        }
    }

    /**
     * Sets the LOD chunk render distance.
     *
     * @param distance the distance in chunks
     */
    public void setLodRenderDistance(int distance) {
        Map<String, String> config = readConfig();
        config.put(KEY_LOD_CHUNK_RENDER_DISTANCE, String.valueOf(distance));
        writeConfig(config);
        LOGGER.info("Set Distant Horizons LOD render distance = {}", distance);
    }

    /**
     * Returns whether LOD rendering is currently enabled.
     */
    public boolean isRenderingEnabled() {
        String value = readConfig().get(KEY_ENABLED);
        return !"false".equalsIgnoreCase(value);
    }

    /**
     * Enables or disables LOD rendering.
     */
    public void setRenderingEnabled(boolean enabled) {
        Map<String, String> config = readConfig();
        config.put(KEY_ENABLED, String.valueOf(enabled));
        writeConfig(config);
        LOGGER.info("Set Distant Horizons rendering enabled = {}", enabled);
    }

    /**
     * Returns the LOD quality setting. Common values: "LOW", "MEDIUM", "HIGH".
     */
    public String getLodQuality() {
        String value = readConfig().get(KEY_LOD_QUALITY);
        return value != null ? value : "MEDIUM";
    }

    /**
     * Sets the LOD quality level.
     */
    public void setLodQuality(String quality) {
        Map<String, String> config = readConfig();
        config.put(KEY_LOD_QUALITY, quality);
        writeConfig(config);
        LOGGER.info("Set Distant Horizons LOD quality = {}", quality);
    }
}
