package com.autotune.profile;

import com.autotune.config.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages performance profiles: loading, saving, deleting, duplicating, importing,
 * exporting, and encoding/decoding for sharing. Uses ConfigManager to resolve the
 * profiles storage directory.
 */
public class ProfileManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileManager.class);
    private static final String PROFILE_EXTENSION = ".json";
    private static final String ACTIVE_PROFILE_FILE = "_active.txt";

    private final ConfigManager configManager;
    private final Gson gson;
    private final List<PerformanceProfile> profiles;
    private String activeProfileName;

    public ProfileManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.profiles = new ArrayList<>();
        this.activeProfileName = null;
    }

    /**
     * Loads all profiles from the profiles directory. Each profile is stored
     * as a separate JSON file named after the profile.
     *
     * @return list of loaded profiles
     */
    public List<PerformanceProfile> loadProfiles() {
        profiles.clear();
        Path profilesDir = configManager.getProfilesDirectory();

        // Load active profile name
        Path activePath = profilesDir.resolve(ACTIVE_PROFILE_FILE);
        if (Files.exists(activePath)) {
            try {
                activeProfileName = Files.readString(activePath).trim();
                LOGGER.debug("Active profile: {}", activeProfileName);
            } catch (IOException e) {
                LOGGER.error("Failed to read active profile marker", e);
                activeProfileName = null;
            }
        }

        // Scan for profile JSON files
        if (!Files.isDirectory(profilesDir)) {
            LOGGER.info("No profiles directory found, returning empty list");
            return Collections.unmodifiableList(profiles);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(profilesDir, "*" + PROFILE_EXTENSION)) {
            for (Path file : stream) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    PerformanceProfile profile = gson.fromJson(reader, PerformanceProfile.class);
                    if (profile != null && profile.getName() != null && !profile.getName().isEmpty()) {
                        profiles.add(profile);
                        LOGGER.debug("Loaded profile: {}", profile.getName());
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load profile from {}", file, e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to scan profiles directory at {}", profilesDir, e);
        }

        LOGGER.info("Loaded {} profiles from {}", profiles.size(), profilesDir);
        return Collections.unmodifiableList(profiles);
    }

    /**
     * Saves a profile to disk. If a profile with the same name already exists,
     * it is overwritten.
     */
    public void saveProfile(PerformanceProfile profile) {
        Path profilesDir = configManager.getProfilesDirectory();
        // [CODE-REVIEW-FIX] Validate resolved path is within profiles directory
        Path filePath = resolveAndValidateProfilePath(profile.getName());

        try {
            Files.createDirectories(profilesDir);
            try (Writer writer = Files.newBufferedWriter(filePath)) {
                gson.toJson(profile, writer);
            }
            LOGGER.info("Saved profile '{}' to {}", profile.getName(), filePath);

            // Update in-memory list
            profiles.removeIf(p -> p.getName().equals(profile.getName()));
            profiles.add(profile);
        } catch (IOException e) {
            LOGGER.error("Failed to save profile '{}' to {}", profile.getName(), filePath, e);
        }
    }

    /**
     * Deletes a profile by name from disk and from the in-memory list.
     */
    public void deleteProfile(String name) {
        Path profilesDir = configManager.getProfilesDirectory();
        // [CODE-REVIEW-FIX] Validate resolved path is within profiles directory
        Path filePath = resolveAndValidateProfilePath(name);

        try {
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                LOGGER.info("Deleted profile '{}' from {}", name, filePath);
            }
            profiles.removeIf(p -> p.getName().equals(name));

            if (name.equals(activeProfileName)) {
                activeProfileName = null;
                saveActiveProfileName();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to delete profile '{}'", name, e);
        }
    }

    /**
     * Duplicates an existing profile with a new name.
     *
     * @param name    the name of the existing profile to duplicate
     * @param newName the name for the duplicate
     */
    public void duplicateProfile(String name, String newName) {
        PerformanceProfile source = findProfile(name);
        if (source == null) {
            LOGGER.warn("Cannot duplicate: profile '{}' not found", name);
            return;
        }

        String json = gson.toJson(source);
        PerformanceProfile duplicate = gson.fromJson(json, PerformanceProfile.class);
        duplicate.setName(newName);
        duplicate.setCreatedTimestamp(System.currentTimeMillis());
        duplicate.setLastUsedTimestamp(System.currentTimeMillis());

        saveProfile(duplicate);
        LOGGER.info("Duplicated profile '{}' as '{}'", name, newName);
    }

    /**
     * Returns the currently active profile, or null if none is set.
     */
    public PerformanceProfile getActiveProfile() {
        if (activeProfileName == null || activeProfileName.isEmpty()) {
            return null;
        }
        return findProfile(activeProfileName);
    }

    /**
     * Sets the active profile by name.
     */
    public void setActiveProfile(String name) {
        PerformanceProfile profile = findProfile(name);
        if (profile == null) {
            LOGGER.warn("Cannot set active: profile '{}' not found", name);
            return;
        }

        this.activeProfileName = name;
        profile.markUsed();
        saveProfile(profile);
        saveActiveProfileName();
        LOGGER.info("Set active profile to '{}'", name);
    }

    /**
     * Imports a profile from a JSON string and saves it to disk.
     *
     * @param json the JSON representation of a PerformanceProfile
     * @return the imported profile, or null if parsing fails
     */
    public PerformanceProfile importProfile(String json) {
        try {
            PerformanceProfile profile = gson.fromJson(json, PerformanceProfile.class);
            if (profile == null || profile.getName() == null || profile.getName().isEmpty()) {
                LOGGER.warn("Imported profile has no name, rejecting");
                return null;
            }

            // Avoid overwriting existing profiles by appending a suffix
            String originalName = profile.getName();
            int suffix = 1;
            while (findProfile(profile.getName()) != null) {
                profile.setName(originalName + " (" + suffix + ")");
                suffix++;
            }

            saveProfile(profile);
            LOGGER.info("Imported profile '{}'", profile.getName());
            return profile;
        } catch (Exception e) {
            LOGGER.error("Failed to import profile from JSON", e);
            return null;
        }
    }

    /**
     * Exports a profile to a JSON string for external storage or sharing.
     *
     * @param name the name of the profile to export
     * @return the JSON string, or null if the profile is not found
     */
    public String exportProfile(String name) {
        PerformanceProfile profile = findProfile(name);
        if (profile == null) {
            LOGGER.warn("Cannot export: profile '{}' not found", name);
            return null;
        }
        String json = gson.toJson(profile);
        LOGGER.info("Exported profile '{}' ({} chars)", name, json.length());
        return json;
    }

    /**
     * Encodes a profile into a compact Base64 string for easy sharing.
     * Uses ProfileCodec: JSON -> GZIP -> Base64.
     */
    public String encodeForSharing(PerformanceProfile profile) {
        return ProfileCodec.encode(profile);
    }

    /**
     * Decodes a compact Base64 sharing string back into a PerformanceProfile.
     * Uses ProfileCodec: Base64 -> GZIP -> JSON.
     */
    public PerformanceProfile decodeFromSharing(String encoded) {
        return ProfileCodec.decode(encoded);
    }

    /**
     * Returns all loaded profiles.
     */
    public List<PerformanceProfile> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    // --- Internal helpers ---

    private PerformanceProfile findProfile(String name) {
        for (PerformanceProfile profile : profiles) {
            if (profile.getName().equals(name)) {
                return profile;
            }
        }
        return null;
    }

    private void saveActiveProfileName() {
        Path profilesDir = configManager.getProfilesDirectory();
        Path activePath = profilesDir.resolve(ACTIVE_PROFILE_FILE);
        try {
            if (activeProfileName != null) {
                Files.writeString(activePath, activeProfileName);
            } else if (Files.exists(activePath)) {
                Files.delete(activePath);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save active profile name", e);
        }
    }

    /**
     * Sanitizes a profile name for use as a file name.
     * [CODE-REVIEW-FIX] Strips path separators and traversal sequences to prevent path traversal attacks.
     * Only allows alphanumeric characters, spaces, hyphens, and underscores.
     */
    private String sanitizeFileName(String name) {
        // Strip any path separators and traversal sequences first
        String sanitized = name.replace("/", "").replace("\\", "").replace("..", "");
        // Only allow safe characters: alphanumeric, spaces, hyphens, underscores
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9 _\\-]", "_").trim();
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Profile name is empty after sanitization");
        }
        return sanitized;
    }

    /**
     * [CODE-REVIEW-FIX] Resolves a profile file path and validates it is within the profiles directory
     * to prevent path traversal attacks.
     */
    private Path resolveAndValidateProfilePath(String name) {
        Path profilesDir = configManager.getProfilesDirectory();
        Path filePath = profilesDir.resolve(sanitizeFileName(name) + PROFILE_EXTENSION).normalize();
        if (!filePath.startsWith(profilesDir.normalize())) {
            throw new IllegalArgumentException("Profile path escapes profiles directory: " + name);
        }
        return filePath;
    }
}
