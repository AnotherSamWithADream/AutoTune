package com.autotune.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compatibility handler for Nvidium. Detects whether the mod is present and
 * whether it should be enabled based on the GPU vendor. Nvidium only works
 * on NVIDIA GPUs and should be disabled if the user has an AMD or Intel GPU.
 * Configuration is stored in config/nvidium.properties.
 */
public class NvidiumCompat implements ModCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(NvidiumCompat.class);
    private static final String CONFIG_FILE = "nvidium.properties";

    private static final String KEY_ENABLED = "enabled";
    private static final String NVIDIA_VENDOR = "nvidia";

    private final Path configPath;

    public NvidiumCompat() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
    }

    @Override
    public String getModId() {
        return "nvidium";
    }

    @Override
    public String getModName() {
        return "Nvidium";
    }

    @Override
    public boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(getModId());
    }

    @Override
    public void initialize() {
        LOGGER.info("Nvidium compat initialized. Config path: {}", configPath);
    }

    /**
     * Reads the nvidium.properties config file.
     */
    private java.util.Properties readProperties() {
        java.util.Properties props = new java.util.Properties();
        if (!Files.exists(configPath)) {
            LOGGER.debug("Nvidium config not found at {}", configPath);
            return props;
        }
        try (var reader = Files.newBufferedReader(configPath)) {
            props.load(reader);
        } catch (IOException e) {
            LOGGER.error("Failed to read Nvidium config from {}", configPath, e);
        }
        return props;
    }

    /**
     * Writes the properties back to the nvidium.properties config file.
     */
    private void writeProperties(java.util.Properties props) {
        try {
            Files.createDirectories(configPath.getParent());
            try (var writer = Files.newBufferedWriter(configPath)) {
                props.store(writer, "Nvidium configuration - modified by AutoTune");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write Nvidium config to {}", configPath, e);
        }
    }

    /**
     * Returns whether Nvidium is currently enabled in its config.
     */
    public boolean isEnabled() {
        java.util.Properties props = readProperties();
        String value = props.getProperty(KEY_ENABLED, "true");
        return Boolean.parseBoolean(value);
    }

    /**
     * Enables or disables Nvidium.
     */
    public void setEnabled(boolean enabled) {
        java.util.Properties props = readProperties();
        props.setProperty(KEY_ENABLED, String.valueOf(enabled));
        writeProperties(props);
        LOGGER.info("Set Nvidium enabled = {}", enabled);
    }

    /**
     * Determines whether Nvidium should be enabled for the given GPU vendor string.
     * Nvidium only works on NVIDIA GPUs.
     *
     * @param gpuVendor the GPU vendor string (e.g., from HardwareProfile.gpuVendor())
     * @return true if the GPU is NVIDIA and Nvidium should be enabled
     */
    public boolean shouldBeEnabled(String gpuVendor) {
        if (gpuVendor == null) {
            return false;
        }
        return gpuVendor.toLowerCase().contains(NVIDIA_VENDOR);
    }

    /**
     * Automatically enables or disables Nvidium based on the detected GPU vendor.
     * If the GPU is not NVIDIA, Nvidium is disabled to prevent rendering issues.
     *
     * @param gpuVendor the GPU vendor string
     */
    public void autoConfigureForGpu(String gpuVendor) {
        boolean shouldEnable = shouldBeEnabled(gpuVendor);
        boolean currentlyEnabled = isEnabled();

        if (currentlyEnabled != shouldEnable) {
            setEnabled(shouldEnable);
            if (shouldEnable) {
                LOGGER.info("Nvidium auto-enabled for NVIDIA GPU (vendor: {})", gpuVendor);
            } else {
                LOGGER.info("Nvidium auto-disabled for non-NVIDIA GPU (vendor: {})", gpuVendor);
            }
        } else {
            LOGGER.debug("Nvidium already {} for GPU vendor: {}", shouldEnable ? "enabled" : "disabled", gpuVendor);
        }
    }

    /**
     * Gets a config value by key from the properties file.
     */
    public String getSettingValue(String key) {
        return readProperties().getProperty(key);
    }

    /**
     * Sets a config value and persists it.
     */
    public void setSettingValue(String key, String value) {
        java.util.Properties props = readProperties();
        props.setProperty(key, value);
        writeProperties(props);
        LOGGER.info("Set Nvidium setting '{}' = {}", key, value);
    }
}
