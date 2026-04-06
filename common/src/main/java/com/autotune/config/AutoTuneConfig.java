package com.autotune.config;

public class AutoTuneConfig {

    private boolean hasHardwareProfile;
    private boolean showFpsOverlay;
    private boolean showLiveModeHud;
    private boolean showToastNotifications;
    private int targetFps;
    private int floorFps;
    private String activeProfileName;
    private LiveModeConfig liveModeConfig;
    private int configSchemaVersion;

    public AutoTuneConfig() {
        this.hasHardwareProfile = false;
        this.showFpsOverlay = true;
        this.showLiveModeHud = true;
        this.showToastNotifications = true;
        this.targetFps = 60;
        this.floorFps = 30;
        this.activeProfileName = "default";
        this.liveModeConfig = new LiveModeConfig();
        this.configSchemaVersion = ConfigSchema.CURRENT_VERSION;
    }

    public boolean isHasHardwareProfile() {
        return hasHardwareProfile;
    }

    public boolean hasHardwareProfile() {
        return hasHardwareProfile;
    }

    public void setHasHardwareProfile(boolean hasHardwareProfile) {
        this.hasHardwareProfile = hasHardwareProfile;
    }

    public boolean isShowFpsOverlay() {
        return showFpsOverlay;
    }

    public void setShowFpsOverlay(boolean showFpsOverlay) {
        this.showFpsOverlay = showFpsOverlay;
    }

    public boolean isShowLiveModeHud() {
        return showLiveModeHud;
    }

    public void setShowLiveModeHud(boolean showLiveModeHud) {
        this.showLiveModeHud = showLiveModeHud;
    }

    public boolean isShowToastNotifications() {
        return showToastNotifications;
    }

    public void setShowToastNotifications(boolean showToastNotifications) {
        this.showToastNotifications = showToastNotifications;
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

    public String getActiveProfileName() {
        return activeProfileName;
    }

    public void setActiveProfileName(String activeProfileName) {
        this.activeProfileName = activeProfileName;
    }

    public LiveModeConfig getLiveModeConfig() {
        return liveModeConfig;
    }

    public void setLiveModeConfig(LiveModeConfig liveModeConfig) {
        this.liveModeConfig = liveModeConfig;
    }

    public int getConfigSchemaVersion() {
        return configSchemaVersion;
    }

    public void setConfigSchemaVersion(int configSchemaVersion) {
        this.configSchemaVersion = configSchemaVersion;
    }

    public static class LiveModeConfig {

        private boolean enabled;
        private String mode;
        private int evaluationIntervalMs;
        private int adjustmentCooldownMs;
        private int measurementWindowMs;
        private float hysteresisPercent;
        private float boostThresholdPercent;
        private int boostSustainSeconds;
        private int emergencyDurationMs;
        private int oscillationLockMinutes;

        public LiveModeConfig() {
            this.enabled = false;
            this.mode = "full";
            this.evaluationIntervalMs = 500;
            this.adjustmentCooldownMs = 3000;
            this.measurementWindowMs = 2000;
            this.hysteresisPercent = 5.0f;
            this.boostThresholdPercent = 20.0f;
            this.boostSustainSeconds = 15;
            this.emergencyDurationMs = 2000;
            this.oscillationLockMinutes = 5;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public int getEvaluationIntervalMs() {
            return evaluationIntervalMs;
        }

        public void setEvaluationIntervalMs(int evaluationIntervalMs) {
            this.evaluationIntervalMs = evaluationIntervalMs;
        }

        public int getAdjustmentCooldownMs() {
            return adjustmentCooldownMs;
        }

        public void setAdjustmentCooldownMs(int adjustmentCooldownMs) {
            this.adjustmentCooldownMs = adjustmentCooldownMs;
        }

        public int getMeasurementWindowMs() {
            return measurementWindowMs;
        }

        public void setMeasurementWindowMs(int measurementWindowMs) {
            this.measurementWindowMs = measurementWindowMs;
        }

        public float getHysteresisPercent() {
            return hysteresisPercent;
        }

        public void setHysteresisPercent(float hysteresisPercent) {
            this.hysteresisPercent = hysteresisPercent;
        }

        public float getBoostThresholdPercent() {
            return boostThresholdPercent;
        }

        public void setBoostThresholdPercent(float boostThresholdPercent) {
            this.boostThresholdPercent = boostThresholdPercent;
        }

        public int getBoostSustainSeconds() {
            return boostSustainSeconds;
        }

        public void setBoostSustainSeconds(int boostSustainSeconds) {
            this.boostSustainSeconds = boostSustainSeconds;
        }

        public int getEmergencyDurationMs() {
            return emergencyDurationMs;
        }

        public void setEmergencyDurationMs(int emergencyDurationMs) {
            this.emergencyDurationMs = emergencyDurationMs;
        }

        public int getOscillationLockMinutes() {
            return oscillationLockMinutes;
        }

        public void setOscillationLockMinutes(int oscillationLockMinutes) {
            this.oscillationLockMinutes = oscillationLockMinutes;
        }
    }
}
