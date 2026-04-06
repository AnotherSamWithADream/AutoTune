package com.autotune.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility handler for Oculus, the Forge port of Iris Shaders.
 * Oculus uses the same properties-based config format as Iris (oculus.properties),
 * so this class extends IrisCompat and overrides only the mod identity and config path.
 */
public class OculusCompat extends IrisCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(OculusCompat.class);
    private static final String CONFIG_FILE = "oculus.properties";

    public OculusCompat() {
        super(CONFIG_FILE);
    }

    @Override
    public String getModId() {
        return "oculus";
    }

    @Override
    public String getModName() {
        return "Oculus";
    }

    @Override
    public boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(getModId());
    }

    @Override
    public void initialize() {
        LOGGER.info("Oculus compat initialized. Config: {}, Shaderpacks: {}", configPath, shaderpacksPath);
    }
}
