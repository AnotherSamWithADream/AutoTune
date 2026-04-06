package com.autotune.config;

import com.autotune.benchmark.BenchmarkResult;
import com.autotune.benchmark.hardware.HardwareProfile;
import com.autotune.questionnaire.PlayerPreferences;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigManager.class);
    private static final Path BASE_DIR = Paths.get("config", "autotune");
    private static final String CONFIG_FILE = "config.json";
    private static final String HARDWARE_FILE = "hardware.json";
    private static final String BENCHMARK_FILE = "benchmark.json";
    private static final String PREFERENCES_FILE = "preferences.json";
    private static final String PROFILES_DIR = "profiles";

    private final Gson gson;

    public ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public AutoTuneConfig loadOrCreate() {
        Path configPath = BASE_DIR.resolve(CONFIG_FILE);
        if (Files.exists(configPath)) {
            // [CODE-REVIEW-FIX] Removed unused BufferedReader; only Files.readAllBytes is needed
            try {
                String raw = Files.readString(configPath, StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(raw).getAsJsonObject();

                int fileVersion = 0;
                if (json.has("configSchemaVersion")) {
                    fileVersion = json.get("configSchemaVersion").getAsInt();
                }

                if (fileVersion < ConfigSchema.CURRENT_VERSION) {
                    LOGGER.info("Migrating config from schema version {} to {}", fileVersion, ConfigSchema.CURRENT_VERSION);
                    ConfigMigrator migrator = new ConfigMigrator();
                    json = migrator.migrate(json, fileVersion, ConfigSchema.CURRENT_VERSION);
                }

                AutoTuneConfig config = gson.fromJson(json, AutoTuneConfig.class);
                config.setConfigSchemaVersion(ConfigSchema.CURRENT_VERSION);
                LOGGER.info("Loaded AutoTune config from {}", configPath);
                return config;
            } catch (IOException e) {
                LOGGER.error("Failed to load config from {}, creating default", configPath, e);
            }
        }

        AutoTuneConfig defaultConfig = new AutoTuneConfig();
        save(defaultConfig);
        LOGGER.info("Created default AutoTune config at {}", configPath);
        return defaultConfig;
    }

    public void save(AutoTuneConfig config) {
        Path configPath = BASE_DIR.resolve(CONFIG_FILE);
        try {
            Files.createDirectories(BASE_DIR);
            // [CODE-REVIEW-FIX] Specify UTF-8 on write for consistency with read side
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
            LOGGER.debug("Saved AutoTune config to {}", configPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save config to {}", configPath, e);
        }
    }

    public HardwareProfile loadHardwareProfile() {
        Path hardwarePath = BASE_DIR.resolve(HARDWARE_FILE);
        if (Files.exists(hardwarePath)) {
            try {
                String raw = Files.readString(hardwarePath, StandardCharsets.UTF_8);
                HardwareProfile profile = gson.fromJson(raw, HardwareProfile.class);
                LOGGER.info("Loaded hardware profile from {}", hardwarePath);
                return profile;
            } catch (IOException e) {
                LOGGER.error("Failed to load hardware profile from {}", hardwarePath, e);
            }
        }
        LOGGER.info("No hardware profile found at {}, returning null", hardwarePath);
        return null;
    }

    public void saveHardwareProfile(HardwareProfile profile) {
        Path hardwarePath = BASE_DIR.resolve(HARDWARE_FILE);
        try {
            Files.createDirectories(BASE_DIR);
            // [CODE-REVIEW-FIX] Specify UTF-8 on write for consistency with read side
            try (Writer writer = Files.newBufferedWriter(hardwarePath, StandardCharsets.UTF_8)) {
                gson.toJson(profile, writer);
            }
            LOGGER.debug("Saved hardware profile to {}", hardwarePath);
        } catch (IOException e) {
            LOGGER.error("Failed to save hardware profile to {}", hardwarePath, e);
        }
    }

    public BenchmarkResult loadBenchmarkResults() {
        Path benchmarkPath = BASE_DIR.resolve(BENCHMARK_FILE);
        if (Files.exists(benchmarkPath)) {
            try {
                String raw = Files.readString(benchmarkPath, StandardCharsets.UTF_8);
                BenchmarkResult result = gson.fromJson(raw, BenchmarkResult.class);
                LOGGER.info("Loaded benchmark results from {}", benchmarkPath);
                return result;
            } catch (IOException e) {
                LOGGER.error("Failed to load benchmark results from {}", benchmarkPath, e);
            }
        }
        LOGGER.info("No benchmark results found at {}, returning null", benchmarkPath);
        return null;
    }

    public void saveBenchmarkResults(BenchmarkResult result) {
        Path benchmarkPath = BASE_DIR.resolve(BENCHMARK_FILE);
        try {
            Files.createDirectories(BASE_DIR);
            // [CODE-REVIEW-FIX] Specify UTF-8 on write for consistency with read side
            try (Writer writer = Files.newBufferedWriter(benchmarkPath, StandardCharsets.UTF_8)) {
                gson.toJson(result, writer);
            }
            LOGGER.debug("Saved benchmark results to {}", benchmarkPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save benchmark results to {}", benchmarkPath, e);
        }
    }

    public PlayerPreferences loadPlayerPreferences() {
        Path prefsPath = BASE_DIR.resolve(PREFERENCES_FILE);
        if (Files.exists(prefsPath)) {
            try {
                String raw = Files.readString(prefsPath, StandardCharsets.UTF_8);
                PlayerPreferences prefs = gson.fromJson(raw, PlayerPreferences.class);
                LOGGER.info("Loaded player preferences from {}", prefsPath);
                return prefs;
            } catch (IOException e) {
                LOGGER.error("Failed to load player preferences from {}", prefsPath, e);
            }
        }
        LOGGER.info("No player preferences found at {}, returning null", prefsPath);
        return null;
    }

    public void savePlayerPreferences(PlayerPreferences prefs) {
        Path prefsPath = BASE_DIR.resolve(PREFERENCES_FILE);
        try {
            Files.createDirectories(BASE_DIR);
            // [CODE-REVIEW-FIX] Specify UTF-8 on write for consistency with read side
            try (Writer writer = Files.newBufferedWriter(prefsPath, StandardCharsets.UTF_8)) {
                gson.toJson(prefs, writer);
            }
            LOGGER.debug("Saved player preferences to {}", prefsPath);
        } catch (IOException e) {
            LOGGER.error("Failed to save player preferences to {}", prefsPath, e);
        }
    }

    public Path getProfilesDirectory() {
        Path profilesPath = BASE_DIR.resolve(PROFILES_DIR);
        try {
            Files.createDirectories(profilesPath);
        } catch (IOException e) {
            LOGGER.error("Failed to create profiles directory at {}", profilesPath, e);
        }
        return profilesPath;
    }
}
