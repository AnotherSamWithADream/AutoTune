package com.autotune.optimizer;

import com.autotune.benchmark.BenchmarkResult;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.questionnaire.PlayerPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Main optimizer orchestrator. Analyzes benchmark results and player preferences
 * to compute the optimal settings for each registered setting. Uses CurveInterpolator
 * for continuous settings, FPSBudgetAllocator for budget-based allocation, and
 * InteractionMatrix for cross-setting corrections.
 */
public class SettingsOptimizer {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoTune/Optimizer");

    private final InteractionMatrix interactionMatrix;
    private final FPSBudgetAllocator budgetAllocator;

    public SettingsOptimizer() {
        this.interactionMatrix = new InteractionMatrix();
        this.budgetAllocator = new FPSBudgetAllocator();
    }

    /**
     * Optimizes all settings based on benchmark results and player preferences.
     *
     * @param results  completed benchmark results
     * @param prefs    player preferences (target FPS, visual priority, etc.)
     * @param registry the settings registry
     * @return computed optimal settings with explanations and predicted FPS
     */
    public OptimalSettings optimize(BenchmarkResult results, PlayerPreferences prefs,
                                     SettingsRegistry registry) {
        LOGGER.info("Starting optimization: target={}fps, mode={}",
                prefs.getTargetFps(), results.getBenchmarkMode());

        OptimalSettings optimal = new OptimalSettings();

        // Step 1: Extract benchmark curves for continuous/integer settings
        Map<String, List<CurveInterpolator.DataPoint>> benchmarkCurves = extractBenchmarkCurves(results, registry);

        // Step 2: Determine baseline FPS (FPS at minimum settings from benchmark)
        double baselineFps = estimateBaselineFps(results);

        // Step 3: Use budget allocator for settings with benchmark curves
        // [CODE-REVIEW-FIX] H-019: Pass registry to allocate so AllocationCandidate.definition is populated
        Map<String, Object> budgetValues = budgetAllocator.allocate(
                baselineFps, prefs.getTargetFps(), benchmarkCurves, prefs, registry);

        // Step 4: Apply budget-allocated values
        for (Map.Entry<String, Object> entry : budgetValues.entrySet()) {
            String settingId = entry.getKey();
            SettingDefinition<?> def = registry.get(settingId);
            if (def == null) continue;

            Object rawValue = entry.getValue();
            Object typedValue = coerceValue(rawValue, def);
            String explanation = buildExplanation(settingId, typedValue, def, benchmarkCurves);
            optimal.put(settingId, typedValue, explanation);
        }

        // Step 5: Handle discrete settings not covered by budget allocation
        optimizeDiscreteSettings(results, prefs, registry, optimal, benchmarkCurves);

        // Step 6: Handle boolean settings
        optimizeBooleanSettings(results, prefs, registry, optimal);

        // Step 7: Handle settings without benchmark data (use heuristics)
        optimizeUnbenchmarkedSettings(prefs, registry, optimal);

        // Step 8: Apply interaction multipliers to adjust predictions
        applyInteractionCorrections(optimal, registry);

        // Step 9: Predict final FPS
        double predictedFps = predictFps(optimal, benchmarkCurves, baselineFps);
        // [CODE-REVIEW-FIX] M-004: Use observed P1-to-average ratio from benchmark data
        // instead of hardcoded 0.70. Falls back to 0.80 (more realistic default) if no data.
        double p1Ratio = estimateP1Ratio(results);
        double predictedP1Low = predictedFps * p1Ratio;
        optimal.setPredictedFps(predictedFps);
        optimal.setPredictedP1LowFps(predictedP1Low);

        LOGGER.info("Optimization complete: {} settings, predicted {}fps (P1 low: {}fps)",
                optimal.size(), String.format("%.1f", predictedFps),
                String.format("%.1f", predictedP1Low));

        return optimal;
    }

    /**
     * Extracts per-setting benchmark curves from phase results.
     * Each curve maps setting value (x) to measured FPS (y).
     */
    private Map<String, List<CurveInterpolator.DataPoint>> extractBenchmarkCurves(
            BenchmarkResult results, SettingsRegistry registry) {

        Map<String, List<CurveInterpolator.DataPoint>> curves = new LinkedHashMap<>();

        for (SettingDefinition<?> def : registry.getAll()) {
            String phaseId = def.benchmarkPhaseId();
            if (phaseId == null) continue;

            PhaseResult phase = results.getPhaseResult(phaseId);
            if (phase == null || phase.isSkipped()) continue;

            List<CurveInterpolator.DataPoint> points = new ArrayList<>();

            for (Map.Entry<String, FrameTimeStatistics> measurement : phase.getMeasurements().entrySet()) {
                String label = measurement.getKey();
                FrameTimeStatistics stats = measurement.getValue();

                double settingValue = extractSettingValueFromLabel(label, def);
                if (!Double.isNaN(settingValue)) {
                    points.add(new CurveInterpolator.DataPoint(settingValue, stats.avgFps()));
                }
            }

            if (points.size() >= 2) {
                curves.put(def.id(), points);
                LOGGER.debug("Extracted {} data points for {}", points.size(), def.id());
            }
        }

        return curves;
    }

    /**
     * Extracts the setting value from a measurement label.
     * Labels follow patterns like "rd_12", "sd_8", "FAST", "FANCY", "ed_1.5".
     */
    private double extractSettingValueFromLabel(String label, SettingDefinition<?> def) {
        if (label == null || label.isEmpty()) return Double.NaN;

        // Try extracting numeric value after underscore (e.g., "rd_12" -> 12)
        int underscoreIdx = label.lastIndexOf('_');
        if (underscoreIdx >= 0 && underscoreIdx < label.length() - 1) {
            String numPart = label.substring(underscoreIdx + 1);
            try {
                return Double.parseDouble(numPart);
            } catch (NumberFormatException ignored) {
                // Not a numeric suffix
            }
        }

        // Try matching discrete values by label name
        if (def.discreteValues() != null) {
            switch (label.toUpperCase()) {
                case "FAST", "OFF", "MINIMAL" -> { return 0; }
                case "FANCY", "DECREASED", "ON" -> { return 1; }
                case "FABULOUS", "ALL" -> { return 2; }
                default -> {}
            }
        }

        // Try parsing the whole label as a number
        try {
            return Double.parseDouble(label);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private double estimateBaselineFps(BenchmarkResult results) {
        // Look for the render distance sweep phase at the lowest RD
        PhaseResult rdPhase = results.getPhaseResult("render_distance_sweep");
        if (rdPhase != null && !rdPhase.isSkipped()) {
            double maxFps = 0;
            for (FrameTimeStatistics stats : rdPhase.getMeasurements().values()) {
                if (stats.avgFps() > maxFps) maxFps = stats.avgFps();
            }
            if (maxFps > 0) return maxFps;
        }

        // Fallback: find the highest FPS across all phases
        double maxFps = 60;
        for (PhaseResult phase : results.getPhaseResults().values()) {
            if (phase.isSkipped()) continue;
            for (FrameTimeStatistics stats : phase.getMeasurements().values()) {
                if (stats.avgFps() > maxFps) maxFps = stats.avgFps();
            }
        }
        return maxFps;
    }

    // [CODE-REVIEW-FIX] M-004: Extract observed P1-to-average FPS ratio from benchmark data.
    // This replaces the old hardcoded 0.70 multiplier with actual measurement data.
    // Returns a ratio between 0.5 and 0.95; defaults to 0.80 if no usable data.
    private double estimateP1Ratio(BenchmarkResult results) {
        double totalRatio = 0;
        int count = 0;

        for (PhaseResult phase : results.getPhaseResults().values()) {
            if (phase.isSkipped()) continue;
            for (FrameTimeStatistics stats : phase.getMeasurements().values()) {
                double avg = stats.avgFps();
                double p1 = stats.p1LowFps();
                if (avg > 1.0 && p1 > 0) {
                    double ratio = p1 / avg;
                    // Sanity-check: ratios outside [0.3, 1.0] are likely measurement noise
                    if (ratio >= 0.3 && ratio <= 1.0) {
                        totalRatio += ratio;
                        count++;
                    }
                }
            }
        }

        if (count > 0) {
            double observedRatio = totalRatio / count;
            // Clamp to a reasonable range
            return Math.clamp(observedRatio, 0.50, 0.95);
        }

        // Default: 0.80 is a more realistic fallback than the old 0.70
        return 0.80;
    }

    /**
     * Optimizes discrete settings (enums with fixed option lists) that may not have
     * been allocated by the budget allocator.
     */
    private void optimizeDiscreteSettings(BenchmarkResult results, PlayerPreferences prefs,
                                           SettingsRegistry registry, OptimalSettings optimal,
                                           Map<String, List<CurveInterpolator.DataPoint>> curves) {
        for (SettingDefinition<?> def : registry.getAll()) {
            if (optimal.containsSetting(def.id())) continue;
            if (def.discreteValues() == null || def.discreteValues().isEmpty()) continue;

            String phaseId = def.benchmarkPhaseId();
            List<CurveInterpolator.DataPoint> curve = curves.get(def.id());

            if (curve != null && curve.size() >= 2) {
                // Use interpolation to find the best discrete value
                double targetFps = prefs.getTargetFps();
                double bestX = CurveInterpolator.interpolate(curve, targetFps);

                // Snap to nearest discrete value
                Object bestDiscrete = snapToNearestDiscrete(bestX, def);
                String explanation = "Set to " + bestDiscrete + " based on benchmark curve (target " + targetFps + " FPS)";
                optimal.put(def.id(), bestDiscrete, explanation);
            } else {
                // No benchmark data, use heuristic based on preference weight
                double visualWeight = prefs.getVisualWeight();
                List<?> values = def.discreteValues();
                int idx = (int) Math.round(visualWeight * (values.size() - 1));
                idx = Math.clamp(idx, 0, values.size() - 1);
                Object value = values.get(idx);
                String explanation = "Set based on visual preference weight (" +
                        String.format("%.0f%%", visualWeight * 100) + ")";
                optimal.put(def.id(), value, explanation);
            }
        }
    }

    /**
     * Optimizes boolean settings based on benchmark A/B test results or heuristics.
     */
    private void optimizeBooleanSettings(BenchmarkResult results, PlayerPreferences prefs,
                                          SettingsRegistry registry, OptimalSettings optimal) {
        for (SettingDefinition<?> def : registry.getAll()) {
            if (optimal.containsSetting(def.id())) continue;
            if (def.type() != Boolean.class) continue;

            String phaseId = def.benchmarkPhaseId();
            if (phaseId != null) {
                PhaseResult phase = results.getPhaseResult(phaseId);
                if (phase != null && !phase.isSkipped() && phase.getMeasurements().size() >= 2) {
                    // A/B test: compare on vs off
                    double fpsOn = 0, fpsOff = 0;
                    for (Map.Entry<String, FrameTimeStatistics> m : phase.getMeasurements().entrySet()) {
                        String label = m.getKey().toLowerCase();
                        if (label.contains("on") || label.contains("true") || label.contains("enabled")) {
                            fpsOn = m.getValue().avgFps();
                        } else if (label.contains("off") || label.contains("false") || label.contains("disabled")) {
                            fpsOff = m.getValue().avgFps();
                        }
                    }

                    if (fpsOn > 0 && fpsOff > 0) {
                        double fpsCost = fpsOff - fpsOn;
                        double costPercent = (fpsCost / fpsOff) * 100;

                        // If enabling costs more than 5% FPS and visual weight is low, disable
                        boolean enable = !(fpsCost > 0 && costPercent > 5 && prefs.getVisualWeight() < 0.5)
                                && !(fpsCost > 0 && costPercent > 15);

                        String explanation = enable
                                ? "Enabled: FPS cost " + String.format("%.1f%%", costPercent) + " is acceptable"
                                : "Disabled: saves " + String.format("%.1f", fpsCost) + " FPS (" +
                                  String.format("%.1f%%", costPercent) + " improvement)";
                        optimal.put(def.id(), enable, explanation);
                        continue;
                    }
                }
            }

            // Heuristic: enable if visual impact outweighs performance impact
            boolean enable = def.visualImpact() >= def.performanceImpact() * (1.0 - prefs.getVisualWeight());
            String explanation = enable
                    ? "Enabled by default: visual impact (" + def.visualImpact() + ") justifies cost"
                    : "Disabled: performance impact (" + def.performanceImpact() + ") exceeds visual benefit";
            optimal.put(def.id(), enable, explanation);
        }
    }

    /**
     * Handles settings with no benchmark data using hardware tier and preference heuristics.
     */
    private void optimizeUnbenchmarkedSettings(PlayerPreferences prefs, SettingsRegistry registry,
                                                OptimalSettings optimal) {
        for (SettingDefinition<?> def : registry.getAll()) {
            if (optimal.containsSetting(def.id())) continue;

            // Use default value with explanation
            Object value = def.defaultValue();
            if (value != null) {
                optimal.put(def.id(), value,
                        "Using default value (no benchmark data available for this setting)");
            }
        }
    }

    /**
     * Applies interaction matrix corrections to the predicted FPS.
     * Adjusts explanations when interactions significantly affect the result.
     */
    private void applyInteractionCorrections(OptimalSettings optimal, SettingsRegistry registry) {
        Map<String, Object> values = optimal.getValues();
        Set<String> noted = new HashSet<>();

        for (String settingId : values.keySet()) {
            Map<String, Double> interactions = interactionMatrix.getInteractionsFor(settingId, values);
            for (Map.Entry<String, Double> interaction : interactions.entrySet()) {
                String otherId = interaction.getKey();
                double mult = interaction.getValue();

                String pairKey = settingId.compareTo(otherId) < 0
                        ? settingId + "+" + otherId : otherId + "+" + settingId;
                if (noted.contains(pairKey)) continue;
                noted.add(pairKey);

                if (Math.abs(mult - 1.0) > 0.1) {
                    SettingDefinition<?> def1 = registry.get(settingId);
                    SettingDefinition<?> def2 = registry.get(otherId);
                    String name1 = def1 != null ? def1.displayName() : settingId;
                    String name2 = def2 != null ? def2.displayName() : otherId;

                    if (mult > 1.0) {
                        LOGGER.debug("Interaction penalty: {} + {} = {}x cost", name1, name2, mult);
                    } else {
                        LOGGER.debug("Interaction bonus: {} + {} = {}x cost", name1, name2, mult);
                    }
                }
            }
        }
    }

    /**
     * Predicts the final FPS based on optimal settings and benchmark curves.
     */
    private double predictFps(OptimalSettings optimal,
                               Map<String, List<CurveInterpolator.DataPoint>> curves,
                               double baselineFps) {
        double totalCost = 0;

        for (Map.Entry<String, Object> entry : optimal.getValues().entrySet()) {
            String settingId = entry.getKey();
            Object value = entry.getValue();

            List<CurveInterpolator.DataPoint> curve = curves.get(settingId);
            if (curve == null || curve.size() < 2) continue;

            // Cost = difference from baseline (minimum setting FPS)
            double minX = curve.stream().mapToDouble(CurveInterpolator.DataPoint::x).min().orElse(0);
            double baseAtMin = CurveInterpolator.evaluateAt(curve, minX);

            double valueX = value instanceof Number n ? n.doubleValue() : 0;
            double fpsAtValue = CurveInterpolator.evaluateAt(curve, valueX);

            double cost = Math.max(0, baseAtMin - fpsAtValue);
            totalCost += cost;
        }

        // Apply interaction multiplier estimate (average 5% interaction overhead)
        double interactionFactor = 1.05;
        totalCost *= interactionFactor;

        double predicted = baselineFps - totalCost;
        return Math.max(1.0, predicted);
    }

    private Object snapToNearestDiscrete(double target, SettingDefinition<?> def) {
        List<?> values = def.discreteValues();
        if (values == null || values.isEmpty()) return target;

        Object closest = values.getFirst();
        double closestDist = Double.MAX_VALUE;

        for (Object v : values) {
            double numV;
            if (v instanceof Number n) numV = n.doubleValue();
            else continue;

            double dist = Math.abs(numV - target);
            if (dist < closestDist) {
                closestDist = dist;
                closest = v;
            }
        }

        return closest;
    }

    /**
     * Coerces a raw value (often Double from the interpolator) to the correct type
     * for the setting definition.
     */
    private Object coerceValue(Object raw, SettingDefinition<?> def) {
        if (raw == null) return def.defaultValue();

        Class<?> type = def.type();

        if (type == Integer.class) {
            if (raw instanceof Number n) {
                int value = (int) Math.round(n.doubleValue());
                if (def.minValue() instanceof Integer min && def.maxValue() instanceof Integer max) {
                    value = Math.clamp(value, min, max);
                }
                return value;
            }
        }

        if (type == Float.class) {
            if (raw instanceof Number n) {
                float value = Math.round(n.floatValue() * 10f) / 10f;
                if (def.minValue() instanceof Float min && def.maxValue() instanceof Float max) {
                    value = Math.clamp(value, min, max);
                }
                return value;
            }
        }

        if (type == Boolean.class) {
            if (raw instanceof Boolean) return raw;
            if (raw instanceof Number n) return n.doubleValue() >= 0.5;
        }

        return raw;
    }

    private String buildExplanation(String settingId, Object value, SettingDefinition<?> def,
                                     Map<String, List<CurveInterpolator.DataPoint>> curves) {
        List<CurveInterpolator.DataPoint> curve = curves.get(settingId);
        if (curve == null) {
            return "Set to " + value + " (default recommendation)";
        }

        double fpsAtValue = CurveInterpolator.evaluateAt(curve,
                value instanceof Number n ? n.doubleValue() : 0);

        return "Set to " + value + " (predicted " + String.format("%.0f", fpsAtValue)
                + " FPS from benchmark data)";
    }
}
