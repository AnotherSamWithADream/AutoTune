package com.autotune.profile;

import com.autotune.questionnaire.PlayerPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * A saved performance profile containing a complete set of player preferences
 * and setting overrides. Profiles can be created manually, from presets, or
 * imported/exported for sharing between players.
 */
public class PerformanceProfile {

    private String name;
    private String description;
    private PlayerPreferences preferences;
    private Map<String, Object> settingOverrides;
    private long createdTimestamp;
    private long lastUsedTimestamp;

    public PerformanceProfile() {
        this.name = "";
        this.description = "";
        this.preferences = new PlayerPreferences();
        this.settingOverrides = new HashMap<>();
        this.createdTimestamp = System.currentTimeMillis();
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    public PerformanceProfile(String name, String description, PlayerPreferences preferences,
                              Map<String, Object> settingOverrides) {
        this.name = name;
        this.description = description;
        this.preferences = preferences;
        this.settingOverrides = settingOverrides != null ? new HashMap<>(settingOverrides) : new HashMap<>();
        this.createdTimestamp = System.currentTimeMillis();
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    // --- Factory methods for preset profiles ---

    /**
     * Creates a Competitive PvP profile optimized for maximum FPS with minimal
     * visual effects and low render distance to reduce distractions.
     */
    public static PerformanceProfile competitivePvp() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.setFpsRank(1);
        prefs.setStabilityRank(2);
        prefs.setRenderDistanceRank(3);
        prefs.setVisualQualityRank(4);
        prefs.setTargetFps(240);
        prefs.setFloorFps(144);
        prefs.setPlayStyle(PlayerPreferences.PlayStyle.COMPETITIVE_PVP);
        prefs.setShaderPref(PlayerPreferences.ShaderPref.NO_SHADERS);
        prefs.setLiveModePref(PlayerPreferences.LiveModePref.FULL);
        prefs.setAdjustVisibility(PlayerPreferences.AdjustmentVisibility.PREFER_STABLE_FPS);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("particles", 0);         // Minimal particles
        overrides.put("clouds", false);         // No clouds
        overrides.put("entityShadows", false);  // No entity shadows
        overrides.put("vignette", false);       // No vignette
        overrides.put("smoothLighting", 0);     // No smooth lighting
        overrides.put("renderDistance", 6);      // Low render distance
        overrides.put("entityDistance", 75);     // Reduced entity render distance %

        return new PerformanceProfile(
                "Competitive PvP",
                "Maximum FPS with minimal visual effects for competitive play. "
                        + "Prioritizes frame rate and input latency over visuals.",
                prefs,
                overrides
        );
    }

    /**
     * Creates a Building & Screenshots profile optimized for maximum visual
     * quality with high render distance and shader support.
     */
    public static PerformanceProfile buildingScreenshots() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.setFpsRank(4);
        prefs.setStabilityRank(3);
        prefs.setRenderDistanceRank(2);
        prefs.setVisualQualityRank(1);
        prefs.setTargetFps(60);
        prefs.setFloorFps(30);
        prefs.setPlayStyle(PlayerPreferences.PlayStyle.BUILDING);
        prefs.setShaderPref(PlayerPreferences.ShaderPref.AUTO_SELECT);
        prefs.setLiveModePref(PlayerPreferences.LiveModePref.CONSERVATIVE);
        prefs.setAdjustVisibility(PlayerPreferences.AdjustmentVisibility.BALANCED);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("particles", 2);          // All particles
        overrides.put("clouds", true);           // Fancy clouds
        overrides.put("entityShadows", true);    // Entity shadows on
        overrides.put("smoothLighting", 2);      // Maximum smooth lighting
        overrides.put("renderDistance", 24);      // High render distance
        overrides.put("entityDistance", 100);     // Full entity render distance
        overrides.put("mipmapLevels", 4);        // Full mipmapping
        overrides.put("biomeBlend", 5);          // High biome blend

        return new PerformanceProfile(
                "Building & Screenshots",
                "Maximum visual quality for building and taking screenshots. "
                        + "Prioritizes visuals and render distance over frame rate.",
                prefs,
                overrides
        );
    }

    /**
     * Creates a Streaming profile that balances visual quality with enough
     * CPU headroom for encoding software to run smoothly alongside the game.
     */
    public static PerformanceProfile streaming() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.setFpsRank(2);
        prefs.setStabilityRank(1);
        prefs.setRenderDistanceRank(3);
        prefs.setVisualQualityRank(4);
        prefs.setTargetFps(60);
        prefs.setFloorFps(45);
        prefs.setPlayStyle(PlayerPreferences.PlayStyle.MIXED);
        prefs.setShaderPref(PlayerPreferences.ShaderPref.AUTO_SELECT);
        prefs.setStreams(true);
        prefs.setRecordsVideo(true);
        prefs.setLiveModePref(PlayerPreferences.LiveModePref.FULL);
        prefs.setAdjustVisibility(PlayerPreferences.AdjustmentVisibility.PREFER_STABLE_FPS);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("particles", 1);          // Decreased particles
        overrides.put("clouds", true);           // Keep clouds for viewers
        overrides.put("entityShadows", true);    // Keep shadows
        overrides.put("smoothLighting", 1);      // Medium smooth lighting
        overrides.put("renderDistance", 12);      // Moderate render distance
        overrides.put("entityDistance", 100);     // Full entity distance
        overrides.put("mipmapLevels", 4);        // Full mipmapping for visual quality

        return new PerformanceProfile(
                "Streaming",
                "Balanced settings with CPU headroom for OBS/streaming software. "
                        + "Prioritizes frame stability to avoid drops during stream.",
                prefs,
                overrides
        );
    }

    /**
     * Creates a Battery Saver profile for laptops that limits thermal output
     * by capping FPS and reducing intensive settings, extending battery life.
     */
    public static PerformanceProfile batterySaver() {
        PlayerPreferences prefs = new PlayerPreferences();
        prefs.setFpsRank(3);
        prefs.setStabilityRank(1);
        prefs.setRenderDistanceRank(4);
        prefs.setVisualQualityRank(2);
        prefs.setTargetFps(30);
        prefs.setFloorFps(20);
        prefs.setPlayStyle(PlayerPreferences.PlayStyle.MIXED);
        prefs.setShaderPref(PlayerPreferences.ShaderPref.NO_SHADERS);
        prefs.setLaptop(true);
        prefs.setLimitThermals(true);
        prefs.setLiveModePref(PlayerPreferences.LiveModePref.FULL);
        prefs.setAdjustVisibility(PlayerPreferences.AdjustmentVisibility.DO_WHATEVER);

        Map<String, Object> overrides = new HashMap<>();
        overrides.put("particles", 0);          // Minimal particles
        overrides.put("clouds", false);          // No clouds
        overrides.put("entityShadows", false);   // No entity shadows
        overrides.put("smoothLighting", 0);      // No smooth lighting
        overrides.put("renderDistance", 8);       // Low render distance
        overrides.put("entityDistance", 50);      // Reduced entity distance
        overrides.put("mipmapLevels", 0);        // No mipmapping
        overrides.put("biomeBlend", 1);          // Minimal biome blend
        overrides.put("maxFps", 30);             // Cap FPS to reduce thermal load

        return new PerformanceProfile(
                "Battery Saver",
                "Thermal management for laptops. Caps FPS and reduces heavy settings "
                        + "to keep temperatures low and extend battery life.",
                prefs,
                overrides
        );
    }

    // --- Getters and Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PlayerPreferences getPreferences() {
        return preferences;
    }

    public void setPreferences(PlayerPreferences preferences) {
        this.preferences = preferences;
    }

    public Map<String, Object> getSettingOverrides() {
        return settingOverrides;
    }

    public void setSettingOverrides(Map<String, Object> settingOverrides) {
        this.settingOverrides = settingOverrides != null ? new HashMap<>(settingOverrides) : new HashMap<>();
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public long getLastUsedTimestamp() {
        return lastUsedTimestamp;
    }

    public void setLastUsedTimestamp(long lastUsedTimestamp) {
        this.lastUsedTimestamp = lastUsedTimestamp;
    }

    /**
     * Marks this profile as recently used by updating the lastUsedTimestamp.
     */
    public void markUsed() {
        this.lastUsedTimestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "PerformanceProfile{name='" + name + "', overrides=" + settingOverrides.size()
                + ", created=" + createdTimestamp + "}";
    }
}
