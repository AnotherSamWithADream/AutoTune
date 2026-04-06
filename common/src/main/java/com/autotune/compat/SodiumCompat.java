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
 * Compatibility handler for Sodium. Reads and writes sodium-options.json
 * from the Minecraft config directory, allowing AutoTune to query and
 * modify Sodium rendering settings.
 */
public class SodiumCompat implements ModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(SodiumCompat.class);
    private static final String CONFIG_FILE = "sodium-options.json";

    protected final Gson gson;
    protected final Path configPath;

    public SodiumCompat() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    protected SodiumCompat(String configFileName) {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(configFileName);
    }

    @Override
    public String getModId() {
        return "sodium";
    }

    @Override
    public String getModName() {
        return "Sodium";
    }

    @Override
    public boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(getModId());
    }

    @Override
    public void initialize() {
        LOGGER.info("{} compat initialized. Config path: {}", getModName(), configPath);
    }

    /**
     * Reads the entire Sodium configuration file into a map.
     *
     * @return the configuration as a key-value map, or an empty map if the file cannot be read
     */
    public Map<String, Object> readConfig() {
        if (!Files.exists(configPath)) {
            LOGGER.warn("{} config file not found at {}", getModName(), configPath);
            return new LinkedHashMap<>();
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            Type type = new TypeToken<LinkedHashMap<String, Object>>() {}.getType();
            Map<String, Object> config = gson.fromJson(reader, type);
            if (config == null) {
                return new LinkedHashMap<>();
            }
            LOGGER.debug("Read {} config with {} keys", getModName(), config.size());
            return config;
        } catch (IOException e) {
            LOGGER.error("Failed to read {} config from {}", getModName(), configPath, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * Writes the given configuration map to the Sodium config file, replacing its content.
     *
     * @param config the full configuration to write
     */
    public void writeConfig(Map<String, Object> config) {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(config, writer);
            }
            LOGGER.debug("Wrote {} config with {} keys to {}", getModName(), config.size(), configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to write {} config to {}", getModName(), configPath, e);
        }
    }

    /**
     * Reads a single setting value from the Sodium config by key.
     *
     * @param key the config key to look up
     * @return the value, or null if the key is not found or the config cannot be read
     */
    public Object getSettingValue(String key) {
        Map<String, Object> config = readConfig();
        Object value = config.get(key);
        LOGGER.debug("{} setting '{}' = {}", getModName(), key, value);
        return value;
    }

    /**
     * Sets a single setting value in the Sodium config and writes the file.
     *
     * @param key   the config key to set
     * @param value the value to write
     */
    public void setSettingValue(String key, Object value) {
        Map<String, Object> config = readConfig();
        config.put(key, value);
        writeConfig(config);
        LOGGER.info("Set {} setting '{}' = {}", getModName(), key, value);
    }
}
