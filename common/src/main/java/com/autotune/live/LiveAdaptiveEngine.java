package com.autotune.live;

import com.autotune.AutoTuneMod;
import com.autotune.AutoTuneLogger;
import com.autotune.config.AutoTuneConfig;
import com.autotune.optimizer.SettingsRegistry;
import com.autotune.optimizer.SettingDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The live adaptive performance engine. Continuously monitors FPS and dynamically
 * adjusts settings in real time to maintain the player's target framerate.
 */
public class LiveAdaptiveEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoTune/LiveEngine");

    private final SettingsRegistry settingsRegistry;
    private final AutoTuneConfig config;

    private final RollingFrameBuffer frameBuffer = new RollingFrameBuffer(300);
    private final SettingsPriorityQueue priorityQueue = new SettingsPriorityQueue();
    private final OscillationDetector oscillationDetector;
    private final ContextDetector contextDetector = new ContextDetector();
    private final AdjustmentHistory adjustmentHistory = new AdjustmentHistory(500);
    private final ToastNotificationManager toastManager = new ToastNotificationManager();

    private volatile AdaptiveState state = AdaptiveState.STABLE;
    private volatile boolean enabled;
    private volatile int targetFps;
    private volatile int floorFps;

    // Timing state
    private long lastEvaluationTime;
    private long lastAdjustmentTime;
    private long stateEnteredTime;
    private long degradingStartTime;
    private long boostingStartTime;
    private long emergencyStartTime;

    // Adjustment tracking
    private int downgradeQueueIndex;
    private int upgradeQueueIndex;
    private int emergencyQueueIndex;

    // [CODE-REVIEW-FIX] ConcurrentHashMap: stateTimeTotals is written from tick thread
    // and read from UI thread via getStateTimeTotals(). Plain HashMap risks
    // ConcurrentModificationException.
    private final Map<AdaptiveState, Long> stateTimeTotals = new ConcurrentHashMap<>();

    // Context tracking
    private int contextRenderDistanceOffset;
    private int contextSimDistanceOffset;
    // [CODE-REVIEW-FIX] H-024: Track base values that represent the optimizer's recommended values.
    // Context offsets are applied relative to these bases, not the current (already-offset) values.
    private int baseRenderDistance;
    private int baseSimDistance;
    private boolean baseValuesInitialized;

    // [CODE-REVIEW-FIX] Removed dead field 'optimalValues' (declared but never read/written)

    public LiveAdaptiveEngine(SettingsRegistry settingsRegistry, AutoTuneConfig config) {
        this.settingsRegistry = settingsRegistry;
        this.config = config;
        this.targetFps = config.getTargetFps();
        this.floorFps = config.getFloorFps();
        this.enabled = config.getLiveModeConfig().isEnabled();
        this.oscillationDetector = new OscillationDetector(
                config.getLiveModeConfig().getOscillationLockMinutes() * 60_000L);
        this.lastEvaluationTime = System.currentTimeMillis();
        this.stateEnteredTime = System.currentTimeMillis();
    }

    /**
     * Called every frame from the mixin with frame time in nanoseconds.
     */
    public void onFrameRendered(long frameTimeNanos) {
        frameBuffer.record(frameTimeNanos);
    }

    /**
     * Called every client tick. Evaluates performance and adapts if needed.
     */
    public void tick() {
        if (!enabled || state == AdaptiveState.LOCKED) return;

        long now = System.currentTimeMillis();
        int evalInterval = config.getLiveModeConfig().getEvaluationIntervalMs();

        if (now - lastEvaluationTime < evalInterval) return;
        lastEvaluationTime = now;

        // Update context
        contextDetector.update();
        applyContextOffsets();

        // Update state time tracking
        stateTimeTotals.merge(state, (long) evalInterval, Long::sum);

        // Evaluate and potentially transition states
        evaluateAndAdapt(now);
    }

    private void evaluateAndAdapt(long now) {
        if (!frameBuffer.isFull()) return; // Need enough data

        double avgFps = frameBuffer.getAverageFps();
        double p1LowFps = frameBuffer.get1PercentLowFps();
        double trendSlope = frameBuffer.getFrameTimeTrendSlope();
        float hysteresis = config.getLiveModeConfig().getHysteresisPercent() / 100f;
        float boostThreshold = config.getLiveModeConfig().getBoostThresholdPercent() / 100f;
        int cooldownMs = config.getLiveModeConfig().getAdjustmentCooldownMs();
        int measureWindowMs = config.getLiveModeConfig().getMeasurementWindowMs();

        switch (state) {
            case STABLE -> {
                // Check for degradation
                if (p1LowFps < targetFps * (1 - hysteresis)) {
                    if (degradingStartTime == 0) degradingStartTime = now;
                    if (now - degradingStartTime > 3000) {
                        transitionTo(AdaptiveState.DEGRADING, now);
                    }
                } else {
                    degradingStartTime = 0;
                }

                // Check for boost opportunity
                if (avgFps > targetFps * (1 + boostThreshold)) {
                    if (boostingStartTime == 0) boostingStartTime = now;
                    if (now - boostingStartTime > config.getLiveModeConfig().getBoostSustainSeconds() * 1000L) {
                        transitionTo(AdaptiveState.BOOSTING, now);
                    }
                } else {
                    boostingStartTime = 0;
                }

                // Check trend getting worse
                if (trendSlope > 50_000 && p1LowFps < targetFps * 1.1) {
                    transitionTo(AdaptiveState.DEGRADING, now);
                }
            }

            case DEGRADING -> {
                // Check for emergency
                if (avgFps < floorFps) {
                    if (emergencyStartTime == 0) emergencyStartTime = now;
                    if (now - emergencyStartTime > config.getLiveModeConfig().getEmergencyDurationMs()) {
                        transitionTo(AdaptiveState.EMERGENCY, now);
                        return;
                    }
                } else {
                    emergencyStartTime = 0;
                }

                // Try to adjust a setting
                if (now - lastAdjustmentTime >= cooldownMs) {
                    if (adjustDowngrade()) {
                        lastAdjustmentTime = now;
                        transitionTo(AdaptiveState.RECOVERING, now);
                    }
                }
            }

            case RECOVERING -> {
                // Wait for measurement window, then check if FPS recovered
                if (now - stateEnteredTime > measureWindowMs) {
                    if (frameBuffer.getAverageFps() >= targetFps * (1 - hysteresis * 0.5)) {
                        // Recovered
                        transitionTo(AdaptiveState.STABLE, now);
                        resetDegradingTracking();
                    } else {
                        // Not enough, keep degrading
                        transitionTo(AdaptiveState.DEGRADING, now);
                    }
                }
            }

            case BOOSTING -> {
                if (now - lastAdjustmentTime >= cooldownMs) {
                    if (adjustUpgrade()) {
                        lastAdjustmentTime = now;
                        transitionTo(AdaptiveState.RECOVERING, now);
                    } else {
                        // Nothing more to upgrade
                        transitionTo(AdaptiveState.STABLE, now);
                    }
                }
            }

            case EMERGENCY -> {
                // Aggressive downgrade
                if (now - lastAdjustmentTime >= 500) { // Fast cooldown in emergency
                    if (adjustEmergency()) {
                        lastAdjustmentTime = now;
                    } else {
                        // Nothing left to downgrade
                        transitionTo(AdaptiveState.RECOVERING, now);
                    }
                }

                // Check if emergency resolved
                if (frameBuffer.getAverageFps() >= floorFps) {
                    transitionTo(AdaptiveState.RECOVERING, now);
                }
            }

            case LOCKED -> {} // No-op
        }
    }

    private boolean adjustDowngrade() {
        String mode = config.getLiveModeConfig().getMode();
        if ("static".equals(mode)) return false;

        List<SettingsPriorityQueue.AdjustableEntry> queue = priorityQueue.getDowngradeQueue();
        while (downgradeQueueIndex < queue.size()) {
            SettingsPriorityQueue.AdjustableEntry entry = queue.get(downgradeQueueIndex);
            if (oscillationDetector.isLocked(entry.settingId())) {
                downgradeQueueIndex++;
                continue;
            }
            SettingDefinition<?> setting = settingsRegistry.get(entry.settingId());
            if (setting != null && setting.supportsLiveAdjust()) {
                String oldValue = String.valueOf(setting.reader().get());
                if (applySingleAdjustment(setting, entry.type())) {
                    String newValue = String.valueOf(setting.reader().get());
                    oscillationDetector.recordAdjustment(entry.settingId(), OscillationDetector.AdjustmentDirection.DOWN);
                    adjustmentHistory.record(entry.settingId(), entry.displayName(), oldValue, newValue,
                            "FPS below target", state);
                    if (config.isShowToastNotifications()) {
                        toastManager.showSettingChange(entry.displayName(), oldValue, newValue, "maintain " + targetFps + " FPS");
                    }
                    AutoTuneLogger.info("Live adjust DOWN: {} {} -> {}", entry.displayName(), oldValue, newValue);
                    // [CODE-REVIEW-FIX] L-004: Only advance the queue index on successful adjustment.
                    // Previously, the index was also incremented on failure (below), which skipped
                    // settings that failed to apply but might succeed on a later tick.
                    downgradeQueueIndex++;
                    return true;
                }
                // [CODE-REVIEW-FIX] L-004: Do NOT increment index on failure -- retry next tick
                return false;
            }
            // Setting not found or doesn't support live adjust -- skip it
            downgradeQueueIndex++;
        }
        return false;
    }

    private boolean adjustUpgrade() {
        String mode = config.getLiveModeConfig().getMode();
        if (!"full".equals(mode)) return false;

        List<SettingsPriorityQueue.AdjustableEntry> queue = priorityQueue.getUpgradeQueue();
        while (upgradeQueueIndex < queue.size()) {
            SettingsPriorityQueue.AdjustableEntry entry = queue.get(upgradeQueueIndex);
            if (oscillationDetector.isLocked(entry.settingId())) {
                upgradeQueueIndex++;
                continue;
            }
            SettingDefinition<?> setting = settingsRegistry.get(entry.settingId());
            if (setting != null && setting.supportsLiveAdjust()) {
                String oldValue = String.valueOf(setting.reader().get());
                if (applySingleAdjustment(setting, entry.type())) {
                    String newValue = String.valueOf(setting.reader().get());
                    oscillationDetector.recordAdjustment(entry.settingId(), OscillationDetector.AdjustmentDirection.UP);
                    adjustmentHistory.record(entry.settingId(), entry.displayName(), oldValue, newValue,
                            "FPS headroom available", state);
                    if (config.isShowToastNotifications()) {
                        toastManager.showSettingChange(entry.displayName(), oldValue, newValue, "quality boost");
                    }
                    AutoTuneLogger.info("Live adjust UP: {} {} -> {}", entry.displayName(), oldValue, newValue);
                    upgradeQueueIndex++;
                    return true;
                }
            }
            upgradeQueueIndex++;
        }
        return false;
    }

    private boolean adjustEmergency() {
        List<SettingsPriorityQueue.AdjustableEntry> queue = priorityQueue.getEmergencyQueue();
        while (emergencyQueueIndex < queue.size()) {
            SettingsPriorityQueue.AdjustableEntry entry = queue.get(emergencyQueueIndex);
            SettingDefinition<?> setting = settingsRegistry.get(entry.settingId());
            if (setting != null) {
                String oldValue = String.valueOf(setting.reader().get());
                if (applySingleAdjustment(setting, entry.type())) {
                    String newValue = String.valueOf(setting.reader().get());
                    adjustmentHistory.record(entry.settingId(), entry.displayName(), oldValue, newValue,
                            "EMERGENCY", state);
                    if (config.isShowToastNotifications()) {
                        toastManager.showEmergency("Reducing settings to maintain playability");
                    }
                    AutoTuneLogger.warn("EMERGENCY adjust: {} {} -> {}", entry.displayName(), oldValue, newValue);
                    emergencyQueueIndex++;
                    return true;
                }
            }
            emergencyQueueIndex++;
        }
        return false;
    }

    private boolean applySingleAdjustment(SettingDefinition<?> setting, SettingsPriorityQueue.AdjustType type) {
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null || mod.getPlatformAdapter() == null) return false;
        var adapter = mod.getPlatformAdapter();
        try {
            boolean result = switch (type) {
                case STEP_DOWN -> setting.stepDown(adapter);
                case STEP_UP -> setting.stepUp(adapter);
                case REDUCE_10_PERCENT -> setting.reduceByPercent(adapter, 10);
                case INCREASE_10_PERCENT -> setting.increaseByPercent(adapter, 10);
                case DISABLE -> setting.setToMinimum(adapter);
                case ENABLE -> setting.setToMaximum(adapter);
                case DROP_4 -> setting.dropBy(adapter, 4);
            };
            // [CODE-REVIEW-FIX] H-024: When live adjustments change render/sim distance,
            // update the base value so context offsets remain correctly relative.
            if (result && baseValuesInitialized) {
                String id = setting.id();
                if ("vanilla.render_distance".equals(id)) {
                    updateBaseRenderDistance(adapter.getRenderDistance());
                } else if ("vanilla.simulation_distance".equals(id)) {
                    updateBaseSimDistance(adapter.getSimulationDistance());
                }
            }
            return result;
        } catch (Exception e) {
            LOGGER.error("Failed to apply adjustment to {}: {}", setting.id(), e.toString());
            return false;
        }
    }

    // [CODE-REVIEW-FIX] H-024: Apply context offsets relative to base values, not current values.
    // This prevents offset drift where repeated context changes accumulate errors.
    private void applyContextOffsets() {
        ContextDetector.GameplayContext ctx = contextDetector.getLastContext();
        int newRdOffset = ctx.getRenderDistanceOffset();
        int newSimOffset = ctx.getSimulationDistanceOffset();

        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null || mod.getPlatformAdapter() == null) return;
        var adapter = mod.getPlatformAdapter();

        // Initialize base values on first call (captures optimizer's recommended values)
        if (!baseValuesInitialized) {
            baseRenderDistance = adapter.getRenderDistance();
            baseSimDistance = adapter.getSimulationDistance();
            baseValuesInitialized = true;
        }

        if (newRdOffset != contextRenderDistanceOffset) {
            // Apply offset relative to base, not the current (possibly already-offset) value
            int adjusted = baseRenderDistance + newRdOffset;
            adjusted = Math.clamp(adjusted, 2, 32);
            adapter.setRenderDistance(adjusted);
            contextRenderDistanceOffset = newRdOffset;
        }
        if (newSimOffset != contextSimDistanceOffset) {
            // Apply offset relative to base, not the current (possibly already-offset) value
            int adjusted = baseSimDistance + newSimOffset;
            adjusted = Math.clamp(adjusted, 5, 32);
            adapter.setSimulationDistance(adjusted);
            contextSimDistanceOffset = newSimOffset;
        }
    }

    // [CODE-REVIEW-FIX] H-024: When live adjustments change render/sim distance via
    // adjustDowngrade/adjustUpgrade, update the base value so context offsets remain correct.
    private void updateBaseRenderDistance(int newBase) {
        this.baseRenderDistance = newBase;
    }

    private void updateBaseSimDistance(int newBase) {
        this.baseSimDistance = newBase;
    }

    private void transitionTo(AdaptiveState newState, long now) {
        AutoTuneLogger.debug("State transition: {} -> {}", state, newState);
        state = newState;
        stateEnteredTime = now;
        if (newState == AdaptiveState.STABLE) {
            downgradeQueueIndex = 0;
            upgradeQueueIndex = 0;
            emergencyQueueIndex = 0;
        }
    }

    private void resetDegradingTracking() {
        degradingStartTime = 0;
        emergencyStartTime = 0;
        downgradeQueueIndex = 0;
        emergencyQueueIndex = 0;
    }

    // --- Public API ---

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) {
            frameBuffer.reset();
            // [CODE-REVIEW-FIX] Use transitionTo() instead of directly setting state,
            // so queue indices (downgradeQueueIndex, upgradeQueueIndex, emergencyQueueIndex)
            // get properly reset via the STABLE branch in transitionTo().
            long now = System.currentTimeMillis();
            transitionTo(AdaptiveState.STABLE, now);
            lastEvaluationTime = now;
            AutoTuneLogger.info("Live adaptive mode ENABLED (target={}, floor={})", targetFps, floorFps);
        } else {
            state = AdaptiveState.LOCKED;
            AutoTuneLogger.info("Live adaptive mode DISABLED");
        }
    }

    public boolean isEnabled() { return enabled; }
    public AdaptiveState getState() { return state; }
    public RollingFrameBuffer getFrameBuffer() { return frameBuffer; }
    public AdjustmentHistory getAdjustmentHistory() { return adjustmentHistory; }
    public ToastNotificationManager getToastManager() { return toastManager; }
    public ContextDetector getContextDetector() { return contextDetector; }
    // [CODE-REVIEW-FIX] Return defensive copy wrapped in unmodifiableMap to prevent
    // UI thread from mutating the live map and to avoid CME during iteration.
    public Map<AdaptiveState, Long> getStateTimeTotals() {
        return Map.copyOf(stateTimeTotals);
    }

    public int getTargetFps() { return targetFps; }
    public void setTargetFps(int fps) { this.targetFps = fps; }
    public int getFloorFps() { return floorFps; }
    public void setFloorFps(int fps) { this.floorFps = fps; }

    public double getCurrentFps() { return frameBuffer.getAverageFps(); }
    public double getCurrent1PercentLow() { return frameBuffer.get1PercentLowFps(); }
}
