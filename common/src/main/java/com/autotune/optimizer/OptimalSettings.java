package com.autotune.optimizer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the computed optimal setting values produced by the optimizer,
 * along with per-setting explanations and predicted performance metrics.
 */
public class OptimalSettings {

    private final Map<String, Object> values;
    private final Map<String, String> explanations;
    private double predictedFps;
    private double predictedP1LowFps;

    public OptimalSettings() {
        this.values = new LinkedHashMap<>();
        this.explanations = new LinkedHashMap<>();
        this.predictedFps = 0.0;
        this.predictedP1LowFps = 0.0;
    }

    public OptimalSettings(Map<String, Object> values, Map<String, String> explanations,
                           double predictedFps, double predictedP1LowFps) {
        this.values = new LinkedHashMap<>(values);
        this.explanations = new LinkedHashMap<>(explanations);
        this.predictedFps = predictedFps;
        this.predictedP1LowFps = predictedP1LowFps;
    }

    /**
     * Sets a single optimal value with explanation.
     * // [CODE-REVIEW-FIX] Values are stored as Object for flexibility; callers must ensure
     * // type matches SettingDefinition<T>
     */
    public void put(String settingId, Object value, String explanation) {
        values.put(settingId, value);
        explanations.put(settingId, explanation);
    }

    /**
     * Gets the optimal value for a setting, or null if not set.
     */
    public Object getValue(String settingId) {
        return values.get(settingId);
    }

    /**
     * Gets the explanation for why a setting was set to its value.
     */
    public String getExplanation(String settingId) {
        return explanations.get(settingId);
    }

    public Map<String, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    public Map<String, String> getExplanations() {
        return Collections.unmodifiableMap(explanations);
    }

    public double getPredictedFps() {
        return predictedFps;
    }

    public void setPredictedFps(double predictedFps) {
        this.predictedFps = predictedFps;
    }

    public double getPredictedP1LowFps() {
        return predictedP1LowFps;
    }

    public void setPredictedP1LowFps(double predictedP1LowFps) {
        this.predictedP1LowFps = predictedP1LowFps;
    }

    public int size() {
        return values.size();
    }

    public boolean containsSetting(String settingId) {
        return values.containsKey(settingId);
    }

    @Override
    public String toString() {
        return "OptimalSettings{settings=" + values.size()
                + ", predictedFps=" + String.format("%.1f", predictedFps)
                + ", predictedP1Low=" + String.format("%.1f", predictedP1LowFps) + "}";
    }
}
