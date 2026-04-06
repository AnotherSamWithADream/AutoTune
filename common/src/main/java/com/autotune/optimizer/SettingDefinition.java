package com.autotune.optimizer;

import com.autotune.platform.PlatformAdapter;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Defines a single manageable game setting with metadata about its visual and
 * performance impact, valid range, and methods for programmatic adjustment.
 *
 * @param <T> the value type of the setting (Integer, Boolean, Float, or a discrete enum-like type)
 */
public class SettingDefinition<T> {

    private final String id;
    private final String displayName;
    private final String category;
    private final Class<T> type;
    private final T minValue;
    private final T maxValue;
    private final T defaultValue;
    private final List<T> discreteValues;
    private final double visualImpact;
    private final double performanceImpact;
    private final Consumer<T> applier;
    private final Supplier<T> reader;
    private final String benchmarkPhaseId;
    private final boolean supportsLiveAdjust;
    private final int liveAdjustCooldownMs;
    private final String description;

    // [CODE-REVIEW-FIX] Package-private to force builder usage, preventing parameter ordering mistakes
    SettingDefinition(String id, String displayName, String category, Class<T> type,
                      T minValue, T maxValue, T defaultValue, List<T> discreteValues,
                      double visualImpact, double performanceImpact,
                      Consumer<T> applier, Supplier<T> reader,
                      String benchmarkPhaseId, boolean supportsLiveAdjust,
                      int liveAdjustCooldownMs, String description) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.category = Objects.requireNonNull(category);
        this.type = Objects.requireNonNull(type);
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.defaultValue = defaultValue;
        this.discreteValues = discreteValues;
        this.visualImpact = visualImpact;
        this.performanceImpact = performanceImpact;
        this.applier = Objects.requireNonNull(applier);
        this.reader = Objects.requireNonNull(reader);
        this.benchmarkPhaseId = benchmarkPhaseId;
        this.supportsLiveAdjust = supportsLiveAdjust;
        this.liveAdjustCooldownMs = liveAdjustCooldownMs;
        this.description = description;
    }

    // ---- Getters ----

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String category() { return category; }
    public Class<T> type() { return type; }
    public T minValue() { return minValue; }
    public T maxValue() { return maxValue; }
    public T defaultValue() { return defaultValue; }
    // [CODE-REVIEW-FIX] M-013: Return immutable view to prevent callers from mutating the internal list
    public List<T> discreteValues() { return discreteValues != null ? java.util.Collections.unmodifiableList(discreteValues) : null; }
    public double visualImpact() { return visualImpact; }
    public double performanceImpact() { return performanceImpact; }
    public String benchmarkPhaseId() { return benchmarkPhaseId; }
    public boolean supportsLiveAdjust() { return supportsLiveAdjust; }
    public int liveAdjustCooldownMs() { return liveAdjustCooldownMs; }
    public String description() { return description; }
    public Supplier<T> reader() { return reader; }
    public Consumer<T> applier() { return applier; }

    /**
     * Returns the ratio of visual impact to performance impact.
     * Higher values mean the setting is visually important relative to its cost.
     */
    public double impactRatio() {
        if (performanceImpact <= 0.0) return visualImpact > 0 ? Double.MAX_VALUE : 1.0;
        return visualImpact / performanceImpact;
    }

    // ---- Adjustment methods ----

    /**
     * Reduces the setting by one step. Returns true if the value actually changed.
     * For Integer: decrements by 1 (clamped to min).
     * For Boolean: sets to false.
     * For Float: decrements by 0.1 (clamped to min).
     * For discrete values: moves to previous entry in the list.
     */
    // [CODE-REVIEW-FIX] M-011: Removed @SuppressWarnings("unchecked") -- no unchecked casts here;
    // the annotation belongs on computeStepDown/computeStepUp where the casts actually occur.
    // [CODE-REVIEW-FIX] M-012: The PlatformAdapter parameter is reserved for future
    // version-specific override hooks (e.g. per-version min/max clamping).
    public boolean stepDown(PlatformAdapter adapter) {
        T current = reader.get();
        T newValue = computeStepDown(current);
        if (newValue == null || newValue.equals(current)) return false;
        applier.accept(newValue);
        return true;
    }

    /**
     * Increases the setting by one step. Returns true if the value actually changed.
     */
    // [CODE-REVIEW-FIX] M-011: Removed @SuppressWarnings("unchecked") -- see stepDown comment.
    // [CODE-REVIEW-FIX] M-012: The PlatformAdapter parameter is reserved for future
    // version-specific override hooks (e.g. per-version min/max clamping).
    public boolean stepUp(PlatformAdapter adapter) {
        T current = reader.get();
        T newValue = computeStepUp(current);
        if (newValue == null || newValue.equals(current)) return false;
        applier.accept(newValue);
        return true;
    }

    /**
     * Reduces the setting by the given percentage of its range. Returns true if changed.
     */
    // Unchecked cast required: generic type T erased at runtime, coercion validated by type field
    @SuppressWarnings("unchecked")
    public boolean reduceByPercent(PlatformAdapter adapter, int percent) {
        T current = reader.get();

        if (discreteValues != null && !discreteValues.isEmpty()) {
            int idx = discreteValues.indexOf(current);
            if (idx < 0) idx = discreteValues.size() - 1;
            int steps = Math.max(1, (int) Math.round(discreteValues.size() * percent / 100.0));
            int newIdx = Math.max(0, idx - steps);
            if (newIdx == idx) return false;
            applier.accept(discreteValues.get(newIdx));
            return true;
        }

        if (type == Integer.class) {
            int cur = (Integer) current;
            int min = (Integer) minValue;
            int max = (Integer) maxValue;
            int range = max - min;
            int reduction = Math.max(1, (int) Math.round(range * percent / 100.0));
            int newVal = Math.max(min, cur - reduction);
            if (newVal == cur) return false;
            applier.accept((T) Integer.valueOf(newVal));
            return true;
        }

        if (type == Float.class) {
            float cur = (Float) current;
            float min = (Float) minValue;
            float max = (Float) maxValue;
            float range = max - min;
            float reduction = Math.max(0.1f, range * percent / 100.0f);
            float newVal = Math.max(min, cur - reduction);
            newVal = Math.round(newVal * 10f) / 10f;
            if (Float.compare(newVal, cur) == 0) return false;
            applier.accept((T) Float.valueOf(newVal));
            return true;
        }

        if (type == Boolean.class) {
            if (Boolean.TRUE.equals(current)) {
                applier.accept((T) Boolean.FALSE);
                return true;
            }
            return false;
        }

        return false;
    }

    /**
     * Increases the setting by the given percentage of its range. Returns true if changed.
     */
    // Unchecked cast required: generic type T erased at runtime, coercion validated by type field
    @SuppressWarnings("unchecked")
    public boolean increaseByPercent(PlatformAdapter adapter, int percent) {
        T current = reader.get();

        if (discreteValues != null && !discreteValues.isEmpty()) {
            int idx = discreteValues.indexOf(current);
            if (idx < 0) idx = 0;
            int steps = Math.max(1, (int) Math.round(discreteValues.size() * percent / 100.0));
            int newIdx = Math.min(discreteValues.size() - 1, idx + steps);
            if (newIdx == idx) return false;
            applier.accept(discreteValues.get(newIdx));
            return true;
        }

        if (type == Integer.class) {
            int cur = (Integer) current;
            int min = (Integer) minValue;
            int max = (Integer) maxValue;
            int range = max - min;
            int increase = Math.max(1, (int) Math.round(range * percent / 100.0));
            int newVal = Math.min(max, cur + increase);
            if (newVal == cur) return false;
            applier.accept((T) Integer.valueOf(newVal));
            return true;
        }

        if (type == Float.class) {
            float cur = (Float) current;
            float max = (Float) maxValue;
            float min = (Float) minValue;
            float range = max - min;
            float increase = Math.max(0.1f, range * percent / 100.0f);
            float newVal = Math.min(max, cur + increase);
            newVal = Math.round(newVal * 10f) / 10f;
            if (Float.compare(newVal, cur) == 0) return false;
            applier.accept((T) Float.valueOf(newVal));
            return true;
        }

        if (type == Boolean.class) {
            if (Boolean.FALSE.equals(current)) {
                applier.accept((T) Boolean.TRUE);
                return true;
            }
            return false;
        }

        return false;
    }

    /**
     * Sets the setting to its minimum value. Returns true if changed.
     */
    // Unchecked cast required: Boolean.FALSE cast to T when type==Boolean, validated by type check
    @SuppressWarnings("unchecked")
    public boolean setToMinimum(PlatformAdapter adapter) {
        T current = reader.get();
        T target;
        if (discreteValues != null && !discreteValues.isEmpty()) {
            target = discreteValues.getFirst();
        } else if (type == Boolean.class) {
            target = (T) Boolean.FALSE;
        } else {
            target = minValue;
        }
        if (target == null || target.equals(current)) return false;
        applier.accept(target);
        return true;
    }

    /**
     * Sets the setting to its maximum value. Returns true if changed.
     */
    // Unchecked cast required: Boolean.TRUE cast to T when type==Boolean, validated by type check
    @SuppressWarnings("unchecked")
    public boolean setToMaximum(PlatformAdapter adapter) {
        T current = reader.get();
        T target;
        if (discreteValues != null && !discreteValues.isEmpty()) {
            target = discreteValues.getLast();
        } else if (type == Boolean.class) {
            target = (T) Boolean.TRUE;
        } else {
            target = maxValue;
        }
        if (target == null || target.equals(current)) return false;
        applier.accept(target);
        return true;
    }

    /**
     * For integer settings, drops the value by the specified amount. Returns true if changed.
     */
    // Unchecked cast required: Integer/Float valueOf cast to T, validated by type field checks
    @SuppressWarnings("unchecked")
    public boolean dropBy(PlatformAdapter adapter, int amount) {
        if (type == Integer.class) {
            int cur = (Integer) reader.get();
            int min = (Integer) minValue;
            int newVal = Math.max(min, cur - amount);
            if (newVal == cur) return false;
            applier.accept((T) Integer.valueOf(newVal));
            return true;
        }

        if (discreteValues != null && !discreteValues.isEmpty()) {
            T current = reader.get();
            int idx = discreteValues.indexOf(current);
            if (idx < 0) idx = discreteValues.size() - 1;
            int newIdx = Math.max(0, idx - amount);
            if (newIdx == idx) return false;
            applier.accept(discreteValues.get(newIdx));
            return true;
        }

        if (type == Float.class) {
            float cur = (Float) reader.get();
            float min = (Float) minValue;
            float newVal = Math.max(min, cur - amount * 0.1f);
            newVal = Math.round(newVal * 10f) / 10f;
            if (Float.compare(newVal, cur) == 0) return false;
            applier.accept((T) Float.valueOf(newVal));
            return true;
        }

        return false;
    }

    // ---- Internal helpers ----

    // Unchecked cast required: Integer/Boolean/Float valueOf cast to T, validated by type field
    @SuppressWarnings("unchecked")
    private T computeStepDown(T current) {
        if (discreteValues != null && !discreteValues.isEmpty()) {
            int idx = discreteValues.indexOf(current);
            if (idx <= 0) return current;
            return discreteValues.get(idx - 1);
        }

        if (type == Integer.class) {
            int cur = (Integer) current;
            int min = (Integer) minValue;
            if (cur <= min) return current;
            return (T) Integer.valueOf(cur - 1);
        }

        if (type == Boolean.class) {
            if (Boolean.TRUE.equals(current)) return (T) Boolean.FALSE;
            return current;
        }

        if (type == Float.class) {
            float cur = (Float) current;
            float min = (Float) minValue;
            float newVal = Math.round((cur - 0.1f) * 10f) / 10f;
            if (newVal < min) return current;
            return (T) Float.valueOf(newVal);
        }

        return current;
    }

    // Unchecked cast required: Integer/Boolean/Float valueOf cast to T, validated by type field
    @SuppressWarnings("unchecked")
    private T computeStepUp(T current) {
        if (discreteValues != null && !discreteValues.isEmpty()) {
            int idx = discreteValues.indexOf(current);
            if (idx < 0 || idx >= discreteValues.size() - 1) return current;
            return discreteValues.get(idx + 1);
        }

        if (type == Integer.class) {
            int cur = (Integer) current;
            int max = (Integer) maxValue;
            if (cur >= max) return current;
            return (T) Integer.valueOf(cur + 1);
        }

        if (type == Boolean.class) {
            if (Boolean.FALSE.equals(current)) return (T) Boolean.TRUE;
            return current;
        }

        if (type == Float.class) {
            float cur = (Float) current;
            float max = (Float) maxValue;
            float newVal = Math.round((cur + 0.1f) * 10f) / 10f;
            if (newVal > max) return current;
            return (T) Float.valueOf(newVal);
        }

        return current;
    }

    @Override
    public String toString() {
        return "SettingDefinition{" + id + " (" + displayName + ") " + category
                + " vi=" + visualImpact + " pi=" + performanceImpact + "}";
    }

    // ---- Builder ----

    public static <T> Builder<T> builder(String id, Class<T> type) {
        return new Builder<>(id, type);
    }

    public static class Builder<T> {
        private final String id;
        private final Class<T> type;
        private String displayName = "";
        private String category = "vanilla";
        private T minValue;
        private T maxValue;
        private T defaultValue;
        private List<T> discreteValues;
        private double visualImpact = 0.5;
        private double performanceImpact = 0.5;
        private Consumer<T> applier;
        private Supplier<T> reader;
        private String benchmarkPhaseId;
        private boolean supportsLiveAdjust = false;
        private int liveAdjustCooldownMs = 3000;
        private String description = "";

        private Builder(String id, Class<T> type) {
            this.id = id;
            this.type = type;
        }

        public Builder<T> displayName(String v) { this.displayName = v; return this; }
        public Builder<T> category(String v) { this.category = v; return this; }
        public Builder<T> min(T v) { this.minValue = v; return this; }
        public Builder<T> max(T v) { this.maxValue = v; return this; }
        public Builder<T> defaultValue(T v) { this.defaultValue = v; return this; }
        public Builder<T> discreteValues(List<T> v) { this.discreteValues = v; return this; }
        public Builder<T> visualImpact(double v) { this.visualImpact = v; return this; }
        public Builder<T> performanceImpact(double v) { this.performanceImpact = v; return this; }
        public Builder<T> applier(Consumer<T> v) { this.applier = v; return this; }
        public Builder<T> reader(Supplier<T> v) { this.reader = v; return this; }
        public Builder<T> benchmarkPhaseId(String v) { this.benchmarkPhaseId = v; return this; }
        public Builder<T> supportsLiveAdjust(boolean v) { this.supportsLiveAdjust = v; return this; }
        public Builder<T> liveAdjustCooldownMs(int v) { this.liveAdjustCooldownMs = v; return this; }
        public Builder<T> description(String v) { this.description = v; return this; }

        public SettingDefinition<T> build() {
            return new SettingDefinition<>(id, displayName, category, type,
                    minValue, maxValue, defaultValue, discreteValues,
                    visualImpact, performanceImpact, applier, reader,
                    benchmarkPhaseId, supportsLiveAdjust, liveAdjustCooldownMs, description);
        }
    }
}
