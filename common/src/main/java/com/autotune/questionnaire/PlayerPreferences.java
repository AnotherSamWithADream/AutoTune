package com.autotune.questionnaire;

import java.util.HashMap;
import java.util.Map;

public class PlayerPreferences {

    // Priority rankings (1-4, 1=highest)
    private int fpsRank = 1;
    private int visualQualityRank = 2;
    private int renderDistanceRank = 3;
    private int stabilityRank = 4;

    // Target FPS
    private int targetFps = 60;
    private int floorFps = 30;

    // Shader preference
    private ShaderPref shaderPref = ShaderPref.AUTO_SELECT;
    private String preferredShaderpack = "";

    // Play style
    private PlayStyle playStyle = PlayStyle.MIXED;
    private boolean usesElytra = false;
    private boolean usesRedstone = false;
    private boolean usesMobFarms = false;

    // Special considerations
    private boolean isLaptop = false;
    private boolean limitThermals = false;
    private boolean recordsVideo = false;
    private boolean streams = false;

    // Setting overrides (settings the player wants locked)
    private Map<String, Object> lockedSettings = new HashMap<>();

    // Live mode preferences
    private LiveModePref liveModePref = LiveModePref.FULL;
    private AdjustmentVisibility adjustVisibility = AdjustmentVisibility.BALANCED;
    private boolean allowLiveRenderDistance = true;

    // --- Enums ---

    public enum ShaderPref {
        NO_SHADERS("No Shaders"),
        AUTO_SELECT("Auto Select"),
        KEEP_CURRENT("Keep Current"),
        SPECIFIC_PACK("Specific Pack");

        private final String displayName;

        ShaderPref(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum PlayStyle {
        COMPETITIVE_PVP("Competitive PvP"),
        BUILDING("Building"),
        EXPLORATION("Exploration"),
        REDSTONE("Redstone"),
        SURVIVAL("Survival"),
        MIXED("Mixed");

        private final String displayName;

        PlayStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum LiveModePref {
        FULL("Full", "Allows all automatic adjustments"),
        CONSERVATIVE("Conservative", "Only adjusts non-visual settings"),
        STATIC("Static", "No automatic adjustments after initial optimization");

        private final String displayName;
        private final String description;

        LiveModePref(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum AdjustmentVisibility {
        PREFER_STABLE_FPS("Prefer Stable FPS", "Prioritizes consistent frame timing over visual quality"),
        BALANCED("Balanced", "Balances visual quality with frame rate stability"),
        DO_WHATEVER("Do Whatever", "Lets the optimizer make the best overall decision");

        private final String displayName;
        private final String description;

        AdjustmentVisibility(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    // --- Getters and Setters ---

    public int getFpsRank() {
        return fpsRank;
    }

    public void setFpsRank(int fpsRank) {
        this.fpsRank = fpsRank;
    }

    public int getVisualQualityRank() {
        return visualQualityRank;
    }

    public void setVisualQualityRank(int visualQualityRank) {
        this.visualQualityRank = visualQualityRank;
    }

    public int getRenderDistanceRank() {
        return renderDistanceRank;
    }

    public void setRenderDistanceRank(int renderDistanceRank) {
        this.renderDistanceRank = renderDistanceRank;
    }

    public int getStabilityRank() {
        return stabilityRank;
    }

    public void setStabilityRank(int stabilityRank) {
        this.stabilityRank = stabilityRank;
    }

    public int getTargetFps() {
        return targetFps;
    }

    public void setTargetFps(int targetFps) {
        this.targetFps = targetFps;
    }

    public int getFloorFps() {
        return floorFps;
    }

    public void setFloorFps(int floorFps) {
        this.floorFps = floorFps;
    }

    public ShaderPref getShaderPref() {
        return shaderPref;
    }

    public void setShaderPref(ShaderPref shaderPref) {
        this.shaderPref = shaderPref;
    }

    public String getPreferredShaderpack() {
        return preferredShaderpack;
    }

    public void setPreferredShaderpack(String preferredShaderpack) {
        this.preferredShaderpack = preferredShaderpack;
    }

    public PlayStyle getPlayStyle() {
        return playStyle;
    }

    public void setPlayStyle(PlayStyle playStyle) {
        this.playStyle = playStyle;
    }

    public boolean isUsesElytra() {
        return usesElytra;
    }

    public void setUsesElytra(boolean usesElytra) {
        this.usesElytra = usesElytra;
    }

    public boolean isUsesRedstone() {
        return usesRedstone;
    }

    public void setUsesRedstone(boolean usesRedstone) {
        this.usesRedstone = usesRedstone;
    }

    public boolean isUsesMobFarms() {
        return usesMobFarms;
    }

    public void setUsesMobFarms(boolean usesMobFarms) {
        this.usesMobFarms = usesMobFarms;
    }

    public boolean isLaptop() {
        return isLaptop;
    }

    public void setLaptop(boolean laptop) {
        isLaptop = laptop;
    }

    public boolean isLimitThermals() {
        return limitThermals;
    }

    public void setLimitThermals(boolean limitThermals) {
        this.limitThermals = limitThermals;
    }

    public boolean isRecordsVideo() {
        return recordsVideo;
    }

    public void setRecordsVideo(boolean recordsVideo) {
        this.recordsVideo = recordsVideo;
    }

    public boolean isStreams() {
        return streams;
    }

    public void setStreams(boolean streams) {
        this.streams = streams;
    }

    public Map<String, Object> getLockedSettings() {
        return lockedSettings;
    }

    public void setLockedSettings(Map<String, Object> lockedSettings) {
        this.lockedSettings = lockedSettings;
    }

    public void lockSetting(String settingKey, Object value) {
        lockedSettings.put(settingKey, value);
    }

    public void unlockSetting(String settingKey) {
        lockedSettings.remove(settingKey);
    }

    public boolean isSettingLocked(String settingKey) {
        return lockedSettings.containsKey(settingKey);
    }

    public LiveModePref getLiveModePref() {
        return liveModePref;
    }

    public void setLiveModePref(LiveModePref liveModePref) {
        this.liveModePref = liveModePref;
    }

    public AdjustmentVisibility getAdjustVisibility() {
        return adjustVisibility;
    }

    public void setAdjustVisibility(AdjustmentVisibility adjustVisibility) {
        this.adjustVisibility = adjustVisibility;
    }

    public boolean isAllowLiveRenderDistance() {
        return allowLiveRenderDistance;
    }

    public void setAllowLiveRenderDistance(boolean allowLiveRenderDistance) {
        this.allowLiveRenderDistance = allowLiveRenderDistance;
    }

    /**
     * Sets the priority rankings from an ordered array of priority labels.
     * Index 0 = rank 1 (highest), index 3 = rank 4 (lowest).
     */
    public void setRanksFromOrder(String[] order) {
        for (int i = 0; i < order.length; i++) {
            int rank = i + 1;
            switch (order[i]) {
                case "FPS":
                    fpsRank = rank;
                    break;
                case "Visual Quality":
                    visualQualityRank = rank;
                    break;
                case "Render Distance":
                    renderDistanceRank = rank;
                    break;
                case "Stability":
                    stabilityRank = rank;
                    break;
            }
        }
    }

    /**
     * Returns the priority order as an array of labels, from highest to lowest.
     */
    public String[] getRankOrder() {
        String[] order = new String[4];
        order[fpsRank - 1] = "FPS";
        order[visualQualityRank - 1] = "Visual Quality";
        order[renderDistanceRank - 1] = "Render Distance";
        order[stabilityRank - 1] = "Stability";
        return order;
    }

    /**
     * Returns a visual quality weight between 0.0 (pure performance) and 1.0 (pure quality)
     * based on the visual quality rank relative to the FPS rank.
     */
    public double getVisualWeight() {
        // Higher rank number = lower priority. Convert to a 0-1 weight.
        // If visual quality rank (1=best, 4=worst), we invert so higher rank => lower weight.
        return 1.0 - ((visualQualityRank - 1) / 3.0);
    }

    public boolean hasHardwareProfile() {
        return com.autotune.AutoTuneMod.getInstance() != null
                && com.autotune.AutoTuneMod.getInstance().getHardwareProfile() != null;
    }
}
