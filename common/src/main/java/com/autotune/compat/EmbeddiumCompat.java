package com.autotune.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility handler for Embeddium, the Forge port of Sodium.
 * Embeddium uses the same JSON config format as Sodium (embeddium-options.json),
 * so this class extends SodiumCompat and overrides only the mod identity and config path.
 */
public class EmbeddiumCompat extends SodiumCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddiumCompat.class);
    private static final String CONFIG_FILE = "embeddium-options.json";

    public EmbeddiumCompat() {
        super(CONFIG_FILE);
    }

    @Override
    public String getModId() {
        return "embeddium";
    }

    @Override
    public String getModName() {
        return "Embeddium";
    }

    @Override
    public boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(getModId());
    }

    @Override
    public void initialize() {
        LOGGER.info("Embeddium compat initialized. Config path: {}", configPath);
    }
}
