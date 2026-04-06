package com.autotune.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Compatibility handler for Iris Shaders. Reads and writes config/iris.properties
 * for shader enable/disable, shaderpack selection, and shadow resolution.
 * Also scans the shaderpacks directory for available packs.
 */
public class IrisCompat implements ModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(IrisCompat.class);
    private static final String CONFIG_FILE = "iris.properties";
    private static final String SHADERPACKS_DIR = "shaderpacks";

    private static final String KEY_ENABLE_SHADERS = "enableShaders";
    private static final String KEY_SHADERPACK = "shaderPack";
    private static final String KEY_SHADOW_RESOLUTION = "shadowResolution";

    protected final Path configPath;
    protected final Path shaderpacksPath;

    public IrisCompat() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve(CONFIG_FILE);
        this.shaderpacksPath = FabricLoader.getInstance().getGameDir().resolve(SHADERPACKS_DIR);
    }

    protected IrisCompat(String configFileName) {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.configPath = configDir.resolve(configFileName);
        this.shaderpacksPath = FabricLoader.getInstance().getGameDir().resolve(SHADERPACKS_DIR);
    }

    @Override
    public String getModId() {
        return "iris";
    }

    @Override
    public String getModName() {
        return "Iris Shaders";
    }

    @Override
    public boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(getModId());
    }

    @Override
    public void initialize() {
        LOGGER.info("{} compat initialized. Config: {}, Shaderpacks: {}", getModName(), configPath, shaderpacksPath);
    }

    /**
     * Reads the Iris properties file into a Properties object.
     */
    protected Properties readProperties() {
        Properties props = new Properties();
        if (!Files.exists(configPath)) {
            LOGGER.warn("{} config not found at {}", getModName(), configPath);
            return props;
        }
        try (var reader = Files.newBufferedReader(configPath)) {
            props.load(reader);
        } catch (IOException e) {
            LOGGER.error("Failed to read {} config from {}", getModName(), configPath, e);
        }
        return props;
    }

    /**
     * Writes the Properties object back to the Iris config file.
     */
    protected void writeProperties(Properties props) {
        try {
            Files.createDirectories(configPath.getParent());
            try (var writer = Files.newBufferedWriter(configPath)) {
                props.store(writer, getModName() + " configuration - modified by AutoTune");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write {} config to {}", getModName(), configPath, e);
        }
    }

    /**
     * Returns whether shaders are currently enabled.
     */
    public boolean isShaderEnabled() {
        Properties props = readProperties();
        String value = props.getProperty(KEY_ENABLE_SHADERS, "false");
        return Boolean.parseBoolean(value);
    }

    /**
     * Enables or disables shaders.
     */
    public void setShaderEnabled(boolean enabled) {
        Properties props = readProperties();
        props.setProperty(KEY_ENABLE_SHADERS, String.valueOf(enabled));
        writeProperties(props);
        LOGGER.info("Set {} shaders enabled = {}", getModName(), enabled);
    }

    /**
     * Returns the name of the currently selected shaderpack.
     */
    public String getCurrentShaderpack() {
        Properties props = readProperties();
        return props.getProperty(KEY_SHADERPACK, "");
    }

    /**
     * Sets the currently selected shaderpack.
     */
    public void setShaderpack(String name) {
        Properties props = readProperties();
        props.setProperty(KEY_SHADERPACK, name);
        writeProperties(props);
        LOGGER.info("Set {} shaderpack = {}", getModName(), name);
    }

    /**
     * Scans the shaderpacks directory for available shaderpack folders and zip files.
     *
     * @return list of shaderpack names found, or empty if the directory does not exist
     */
    public List<String> getAvailableShaderpacks() {
        List<String> packs = new ArrayList<>();

        if (!Files.isDirectory(shaderpacksPath)) {
            LOGGER.debug("Shaderpacks directory does not exist: {}", shaderpacksPath);
            return packs;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shaderpacksPath)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry) || name.endsWith(".zip")) {
                    packs.add(name);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan shaderpacks directory at {}", shaderpacksPath, e);
        }

        Collections.sort(packs);
        LOGGER.debug("Found {} available shaderpacks", packs.size());
        return packs;
    }

    /**
     * Returns the current shadow map resolution.
     */
    public int getShadowResolution() {
        Properties props = readProperties();
        String value = props.getProperty(KEY_SHADOW_RESOLUTION, "1024");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid shadow resolution value '{}', defaulting to 1024", value);
            return 1024;
        }
    }

    /**
     * Sets the shadow map resolution. Common values: 512, 1024, 2048, 4096.
     */
    public void setShadowResolution(int resolution) {
        Properties props = readProperties();
        props.setProperty(KEY_SHADOW_RESOLUTION, String.valueOf(resolution));
        writeProperties(props);
        LOGGER.info("Set {} shadow resolution = {}", getModName(), resolution);
    }
}
