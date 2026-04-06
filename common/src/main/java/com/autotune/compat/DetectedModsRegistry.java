package com.autotune.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scans for and tracks compatible performance mods that AutoTune can interact with.
 * On scan, each known ModCompat implementation is checked against FabricLoader and
 * initialized if its mod is present.
 */
public class DetectedModsRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DetectedModsRegistry.class);

    private final List<ModCompat> knownMods;
    private final List<ModCompat> detectedMods;

    public DetectedModsRegistry() {
        this.knownMods = new ArrayList<>();
        this.detectedMods = new ArrayList<>();

        knownMods.add(new SodiumCompat());
        knownMods.add(new IrisCompat());
        knownMods.add(new EmbeddiumCompat());
        knownMods.add(new OculusCompat());
        knownMods.add(new EntityCullingCompat());
        knownMods.add(new LambDynamicLightsCompat());
        knownMods.add(new DistantHorizonsCompat());
        knownMods.add(new NvidiumCompat());
    }

    /**
     * Scans FabricLoader for each known mod. Detected mods are initialized and
     * added to the detected list.
     */
    public void scan() {
        detectedMods.clear();

        for (ModCompat mod : knownMods) {
            if (mod.isLoaded()) {
                LOGGER.info("Detected compatible mod: {} ({})", mod.getModName(), mod.getModId());
                try {
                    mod.initialize();
                    detectedMods.add(mod);
                } catch (Exception e) {
                    LOGGER.error("Failed to initialize compat for mod: {} ({})", mod.getModName(), mod.getModId(), e);
                }
            } else {
                LOGGER.debug("Mod not present: {} ({})", mod.getModName(), mod.getModId());
            }
        }

        LOGGER.info("Mod scan complete. Detected {} compatible mods: {}", detectedMods.size(), getDetectedModNames());
    }

    /**
     * Checks whether a mod with the given ID is currently loaded via FabricLoader.
     */
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /**
     * Returns the list of mods that were detected and initialized during the last scan.
     */
    public List<ModCompat> getDetectedMods() {
        return Collections.unmodifiableList(detectedMods);
    }

    /**
     * Returns a comma-separated string of all detected mod names.
     */
    public String getDetectedModNames() {
        if (detectedMods.isEmpty()) {
            return "(none)";
        }
        return detectedMods.stream()
                .map(ModCompat::getModName)
                .collect(Collectors.joining(", "));
    }
}
