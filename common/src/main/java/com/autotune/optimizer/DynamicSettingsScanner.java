package com.autotune.optimizer;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scans the Minecraft config directory for known mod configuration files
 * that AutoTune can manage. Detects Sodium, Iris, and other supported mods
 * by their config file presence.
 */
public class DynamicSettingsScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoTune/SettingsScanner");

    /**
     * Represents a detected mod configuration file.
     */
    public record DetectedModConfig(
            String modName,
            Path configPath,
            boolean managed
    ) {}

    /**
     * Known config file patterns and their associated mods.
     */
    private record ConfigPattern(String fileName, String modName, boolean managed) {}

    private static final List<ConfigPattern> KNOWN_CONFIGS = List.of(
            new ConfigPattern("sodium-options.json", "sodium", true),
            new ConfigPattern("sodium-mixins.properties", "sodium", false),
            new ConfigPattern("iris.properties", "iris", true),
            new ConfigPattern("irisshaders/iris.properties", "iris", true),
            new ConfigPattern("distant-horizons.toml", "distant_horizons", false),
            new ConfigPattern("nvidium-config.json", "nvidium", false),
            new ConfigPattern("entity_model_features.json", "entity_model_features", false),
            new ConfigPattern("entity_texture_features.json", "entity_texture_features", false),
            new ConfigPattern("lambdynlights.toml", "lambdynlights", false),
            new ConfigPattern("continuity.json", "continuity", false),
            new ConfigPattern("modernfix-mixins.properties", "modernfix", false),
            new ConfigPattern("ferrite-core.mixin.properties", "ferrite_core", false),
            new ConfigPattern("lithium.properties", "lithium", false),
            new ConfigPattern("starlight.properties", "starlight", false),
            new ConfigPattern("indium-renderer.properties", "indium", false),
            new ConfigPattern("c2me.toml", "c2me", false),
            new ConfigPattern("bobby.json", "bobby", false)
    );

    private final Path configDir;

    public DynamicSettingsScanner() {
        this.configDir = FabricLoader.getInstance().getConfigDir();
    }

    public DynamicSettingsScanner(Path configDir) {
        this.configDir = configDir;
    }

    /**
     * Scans the config directory for known mod configuration files.
     *
     * @return list of detected mod configurations
     */
    public List<DetectedModConfig> scan() {
        if (configDir == null || !Files.isDirectory(configDir)) {
            LOGGER.warn("Config directory not found or not accessible: {}", configDir);
            return Collections.emptyList();
        }

        List<DetectedModConfig> detected = new ArrayList<>();

        for (ConfigPattern pattern : KNOWN_CONFIGS) {
            Path configPath = configDir.resolve(pattern.fileName());
            // [CODE-REVIEW-FIX] M-006: Use NOFOLLOW_LINKS to avoid following symlinks,
            // and verify the resolved path is within the config directory to prevent
            // symlink-based path traversal attacks.
            if (Files.exists(configPath, LinkOption.NOFOLLOW_LINKS)
                    && Files.isRegularFile(configPath, LinkOption.NOFOLLOW_LINKS)
                    && isWithinConfigDir(configPath)) {
                detected.add(new DetectedModConfig(pattern.modName(), configPath, pattern.managed()));
                LOGGER.debug("Found config for {}: {}", pattern.modName(), configPath);
            }
        }

        // Also scan for unknown config files to log them
        scanForUnknownConfigs(detected);

        LOGGER.info("Settings scanner found {} mod configs ({} managed)",
                detected.size(), detected.stream().filter(DetectedModConfig::managed).count());

        return Collections.unmodifiableList(detected);
    }

    /**
     * Checks if a specific mod's config file exists.
     */
    // [CODE-REVIEW-FIX] M-006: Added NOFOLLOW_LINKS and path containment checks
    public boolean hasConfig(String modName) {
        for (ConfigPattern pattern : KNOWN_CONFIGS) {
            if (pattern.modName().equals(modName)) {
                Path configPath = configDir.resolve(pattern.fileName());
                if (Files.exists(configPath, LinkOption.NOFOLLOW_LINKS)
                        && isWithinConfigDir(configPath)) return true;
            }
        }
        return false;
    }

    /**
     * Returns the config file path for a specific mod, or null if not found.
     */
    public Path getConfigPath(String modName) {
        for (ConfigPattern pattern : KNOWN_CONFIGS) {
            if (pattern.modName().equals(modName)) {
                Path configPath = configDir.resolve(pattern.fileName());
                if (Files.exists(configPath, LinkOption.NOFOLLOW_LINKS)
                        && isWithinConfigDir(configPath)) return configPath;
            }
        }
        return null;
    }

    private void scanForUnknownConfigs(List<DetectedModConfig> alreadyDetected) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir)) {
            for (Path entry : stream) {
                // [CODE-REVIEW-FIX] M-006: Use NOFOLLOW_LINKS and validate path containment
                if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!isWithinConfigDir(entry)) continue;
                String fileName = entry.getFileName().toString().toLowerCase();

                // Skip files we already know about
                boolean known = false;
                for (DetectedModConfig d : alreadyDetected) {
                    if (entry.equals(d.configPath())) {
                        known = true;
                        break;
                    }
                }
                if (known) continue;

                // Check for patterns that might be mod configs
                if (fileName.endsWith(".json") || fileName.endsWith(".toml")
                        || fileName.endsWith(".properties") || fileName.endsWith(".yml")) {
                    // Check file size - very large configs are likely mod configs
                    try {
                        long size = Files.size(entry);
                        if (size > 0 && size < 1_000_000) {
                            LOGGER.debug("Unknown config file: {} ({} bytes)", fileName, size);
                        }
                    } catch (IOException ignored) {
                        // Skip files we cannot read
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Could not scan config directory for unknown configs: {}", e.toString());
        }
    }

    // [CODE-REVIEW-FIX] M-006: Verify that the resolved path is within the config directory
    // to prevent symlink-based path traversal attacks.
    private boolean isWithinConfigDir(Path path) {
        try {
            Path normalizedConfig = configDir.toAbsolutePath().normalize();
            Path normalizedPath = path.toAbsolutePath().normalize();
            return normalizedPath.startsWith(normalizedConfig);
        } catch (Exception e) {
            LOGGER.debug("Could not verify path containment for {}: {}", path, e.toString());
            return false;
        }
    }
}
