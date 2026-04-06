package com.autotune.optimizer;

import com.autotune.questionnaire.PlayerPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Allocates an FPS budget across settings using a greedy algorithm that maximizes
 * visual quality while meeting the target framerate. Settings with the highest
 * visual-to-performance impact ratio are allocated budget first.
 */
public class FPSBudgetAllocator {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoTune/BudgetAllocator");

    /**
     * Represents a single setting's allocation candidate with its budget cost and visual value.
     */
    private record AllocationCandidate(
            String settingId,
            double impactRatio,
            double performanceCost,
            double visualValue,
            List<CurveInterpolator.DataPoint> curve,
            SettingDefinition<?> definition
    ) implements Comparable<AllocationCandidate> {
        @Override
        public int compareTo(AllocationCandidate other) {
            // Higher ratio = better value, so sort descending
            return Double.compare(other.impactRatio, this.impactRatio);
        }
    }

    /**
     * Allocates FPS budget across settings to maximize visual quality.
     *
     * @param totalBudget     the total FPS budget available (e.g., measured baseline FPS at minimum settings)
     * @param targetFps       the desired target FPS
     * @param benchmarkCurves per-setting benchmark curves mapping setting value (x) to FPS (y)
     * @param prefs           player preferences for weighting visual vs performance
     * @return map of settingId to optimal value
     */
    // [CODE-REVIEW-FIX] H-019: Added SettingsRegistry parameter so AllocationCandidate.definition
    // is populated instead of always null. Callers must pass the registry.
    public Map<String, Object> allocate(double totalBudget, double targetFps,
                                         Map<String, List<CurveInterpolator.DataPoint>> benchmarkCurves,
                                         PlayerPreferences prefs,
                                         SettingsRegistry registry) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (totalBudget <= 0 || targetFps <= 0) {
            LOGGER.warn("Invalid budget ({}) or target FPS ({})", totalBudget, targetFps);
            return result;
        }

        // The "spare budget" is how many FPS we can afford to spend above the target
        double spareBudget = totalBudget - targetFps;
        if (spareBudget < 0) {
            LOGGER.info("Total budget ({}) is below target FPS ({}), all settings at minimum",
                    totalBudget, targetFps);
            return result;
        }

        // Build candidate list from settings that have benchmark data
        List<AllocationCandidate> candidates = buildCandidates(benchmarkCurves, prefs, registry);

        // Sort by impact ratio (highest value-per-cost first)
        Collections.sort(candidates);

        LOGGER.debug("Allocating budget: total={}, target={}, spare={}, candidates={}",
                totalBudget, targetFps, spareBudget, candidates.size());

        // Phase 1: Greedy allocation
        double remainingBudget = spareBudget;
        Map<String, Double> allocatedCosts = new LinkedHashMap<>();

        for (AllocationCandidate candidate : candidates) {
            if (remainingBudget <= 0) break;

            // Estimate the FPS cost of setting this to its maximum quality level
            double maxCost = estimateMaxCost(candidate);
            if (maxCost <= 0) {
                // No measurable cost, set to max
                result.put(candidate.settingId(), getMaxValueForSetting(candidate));
                allocatedCosts.put(candidate.settingId(), 0.0);
                continue;
            }

            if (maxCost <= remainingBudget) {
                // We can afford maximum quality
                result.put(candidate.settingId(), getMaxValueForSetting(candidate));
                allocatedCosts.put(candidate.settingId(), maxCost);
                remainingBudget -= maxCost;
            } else {
                // Partial allocation: find the highest value we can afford
                Object partialValue = findAffordableValue(candidate, remainingBudget);
                if (partialValue != null) {
                    double partialCost = estimateCostForValue(candidate, partialValue);
                    result.put(candidate.settingId(), partialValue);
                    allocatedCosts.put(candidate.settingId(), partialCost);
                    remainingBudget -= partialCost;
                } else {
                    // Cannot afford any increase, keep at minimum
                    result.put(candidate.settingId(), getMinValueForSetting(candidate));
                    allocatedCosts.put(candidate.settingId(), 0.0);
                }
            }
        }

        // Phase 2: Swap refinement pass
        result = refineAllocations(result, candidates, allocatedCosts, spareBudget, prefs);

        // [CODE-REVIEW-FIX] SLF4J does not support {:.1f} format specifiers; use String.format
        LOGGER.info("Budget allocation complete: {} settings configured, {} FPS spare budget remaining",
                result.size(), String.format("%.1f", remainingBudget));

        return result;
    }

    private List<AllocationCandidate> buildCandidates(
            Map<String, List<CurveInterpolator.DataPoint>> benchmarkCurves,
            PlayerPreferences prefs,
            SettingsRegistry registry) {

        List<AllocationCandidate> candidates = new ArrayList<>();
        double visualWeight = prefs != null ? prefs.getVisualWeight() : 0.5;
        double performanceWeight = 1.0 - visualWeight;

        for (Map.Entry<String, List<CurveInterpolator.DataPoint>> entry : benchmarkCurves.entrySet()) {
            String settingId = entry.getKey();
            List<CurveInterpolator.DataPoint> curve = entry.getValue();

            if (curve == null || curve.size() < 2) continue;

            // Estimate visual and performance impact from the curve
            double fpsRange = computeFpsRange(curve);
            double xRange = computeXRange(curve);

            // Cost per unit of x
            double costPerUnit = xRange > 0 ? fpsRange / xRange : 0;

            // Adjust impact ratio based on player preferences
            double adjustedRatio = (visualWeight * xRange + 0.1) / (performanceWeight * fpsRange + 0.1);

            // [CODE-REVIEW-FIX] H-019 / M-002: Look up and pass the SettingDefinition from the registry
            // instead of always passing null, so downstream code can use definition metadata.
            SettingDefinition<?> def = registry != null ? registry.get(settingId) : null;
            candidates.add(new AllocationCandidate(
                    settingId, adjustedRatio, fpsRange, xRange, curve, def));
        }

        return candidates;
    }

    private double computeFpsRange(List<CurveInterpolator.DataPoint> curve) {
        double maxFps = curve.stream().mapToDouble(CurveInterpolator.DataPoint::y).max().orElse(0);
        double minFps = curve.stream().mapToDouble(CurveInterpolator.DataPoint::y).min().orElse(0);
        return Math.abs(maxFps - minFps);
    }

    private double computeXRange(List<CurveInterpolator.DataPoint> curve) {
        double maxX = curve.stream().mapToDouble(CurveInterpolator.DataPoint::x).max().orElse(0);
        double minX = curve.stream().mapToDouble(CurveInterpolator.DataPoint::x).min().orElse(0);
        return maxX - minX;
    }

    private double estimateMaxCost(AllocationCandidate candidate) {
        // [CODE-REVIEW-FIX] H-019: Use the actual FPS range from the curve to compute max cost.
        // The baseline FPS is the MAX y-value (best FPS at lowest quality), and the cost is
        // the difference between baseline and the FPS at highest quality setting.
        List<CurveInterpolator.DataPoint> curve = candidate.curve();
        if (curve == null || curve.size() < 2) return candidate.performanceCost();
        double baselineFps = curve.stream()
                .mapToDouble(CurveInterpolator.DataPoint::y).max().orElse(0);
        double maxX = curve.stream()
                .mapToDouble(CurveInterpolator.DataPoint::x).max().orElse(0);
        double fpsAtMax = CurveInterpolator.evaluateAt(curve, maxX);
        return Math.abs(baselineFps - fpsAtMax);
    }

    private Object getMaxValueForSetting(AllocationCandidate candidate) {
        List<CurveInterpolator.DataPoint> curve = candidate.curve();
        if (curve.isEmpty()) return null;
        // Max x value = highest quality
        return curve.stream().mapToDouble(CurveInterpolator.DataPoint::x).max().orElse(0);
    }

    private Object getMinValueForSetting(AllocationCandidate candidate) {
        List<CurveInterpolator.DataPoint> curve = candidate.curve();
        if (curve.isEmpty()) return null;
        // Min x value = lowest quality (fastest)
        return curve.stream().mapToDouble(CurveInterpolator.DataPoint::x).min().orElse(0);
    }

    /**
     * Finds the highest setting value whose FPS cost is within the given budget.
     * Uses the benchmark curve to interpolate.
     */
    private Object findAffordableValue(AllocationCandidate candidate, double budget) {
        List<CurveInterpolator.DataPoint> curve = candidate.curve();
        if (curve.isEmpty()) return null;

        // Sort curve by x ascending (setting value ascending = quality ascending)
        List<CurveInterpolator.DataPoint> sorted = new ArrayList<>(curve);
        sorted.sort(Comparator.comparingDouble(CurveInterpolator.DataPoint::x));

        // [CODE-REVIEW-FIX] H-019: Compute baselineFps as the MAX y-value across all curve points,
        // not just the first point. The lowest quality setting should yield the highest FPS,
        // but it may not be the first point in the sorted-by-x list.
        double baselineFps = sorted.stream()
                .mapToDouble(CurveInterpolator.DataPoint::y).max().orElse(60);
        if (baselineFps <= 0) baselineFps = 60;

        // Walk from highest quality down to find where cost fits budget
        for (int i = sorted.size() - 1; i >= 0; i--) {
            double cost = Math.abs(baselineFps - sorted.get(i).y());
            if (cost <= budget) {
                return sorted.get(i).x();
            }
        }

        return sorted.getFirst().x();
    }

    private double estimateCostForValue(AllocationCandidate candidate, Object value) {
        if (!(value instanceof Number numValue)) return 0;

        List<CurveInterpolator.DataPoint> curve = candidate.curve();
        if (curve.isEmpty()) return 0;

        // [CODE-REVIEW-FIX] Use stream max y-value for baseline, consistent with
        // estimateMaxCost() and findAffordableValue() which also use max y-value.
        // Previously used curve's min-x point via evaluateAt, which could differ.
        double baselineFps = curve.stream().mapToDouble(CurveInterpolator.DataPoint::y).max().orElse(60);

        // FPS at the target value
        double fpsAtValue = CurveInterpolator.evaluateAt(curve, numValue.doubleValue());

        return Math.abs(baselineFps - fpsAtValue);
    }

    /**
     * Refinement pass: tries swapping pairs of settings to see if a different allocation
     * improves total visual quality within the same budget.
     */
    private Map<String, Object> refineAllocations(
            Map<String, Object> current,
            List<AllocationCandidate> candidates,
            Map<String, Double> allocatedCosts,
            double totalBudget,
            PlayerPreferences prefs) {

        Map<String, Object> best = new LinkedHashMap<>(current);
        double bestScore = computeTotalVisualScore(best, candidates);
        boolean improved = true;
        int maxIterations = 10;
        int iteration = 0;

        while (improved && iteration < maxIterations) {
            improved = false;
            iteration++;

            for (int i = 0; i < candidates.size(); i++) {
                for (int j = i + 1; j < candidates.size(); j++) {
                    AllocationCandidate a = candidates.get(i);
                    AllocationCandidate b = candidates.get(j);

                    String idA = a.settingId();
                    String idB = b.settingId();

                    if (!best.containsKey(idA) || !best.containsKey(idB)) continue;

                    double costA = allocatedCosts.getOrDefault(idA, 0.0);
                    double costB = allocatedCosts.getOrDefault(idB, 0.0);

                    // Try increasing A and decreasing B
                    double swapBudget = costB * 0.5;
                    Object newA = findAffordableValue(a, costA + swapBudget);
                    Object newB = findAffordableValue(b, costB - swapBudget);

                    if (newA != null && newB != null) {
                        Map<String, Object> trial = new LinkedHashMap<>(best);
                        trial.put(idA, newA);
                        trial.put(idB, newB);

                        double newCostA = estimateCostForValue(a, newA);
                        double newCostB = estimateCostForValue(b, newB);

                        if (newCostA + newCostB <= costA + costB + 0.5) {
                            double trialScore = computeTotalVisualScore(trial, candidates);
                            if (trialScore > bestScore) {
                                best = trial;
                                bestScore = trialScore;
                                allocatedCosts.put(idA, newCostA);
                                allocatedCosts.put(idB, newCostB);
                                improved = true;
                            }
                        }
                    }
                }
            }
        }

        if (iteration > 1) {
            LOGGER.debug("Refinement completed after {} iterations", iteration);
        }

        return best;
    }

    /**
     * Computes a total visual quality score for a given allocation.
     */
    private double computeTotalVisualScore(Map<String, Object> allocation,
                                            List<AllocationCandidate> candidates) {
        double totalScore = 0;

        for (AllocationCandidate candidate : candidates) {
            Object value = allocation.get(candidate.settingId());
            if (value == null) continue;

            List<CurveInterpolator.DataPoint> curve = candidate.curve();
            if (curve.isEmpty()) continue;

            double minX = curve.stream().mapToDouble(CurveInterpolator.DataPoint::x).min().orElse(0);
            double maxX = curve.stream().mapToDouble(CurveInterpolator.DataPoint::x).max().orElse(1);
            double range = maxX - minX;

            if (range <= 0) continue;

            double numValue = value instanceof Number n ? n.doubleValue() : 0;
            double normalized = (numValue - minX) / range;
            totalScore += normalized * candidate.visualValue();
        }

        return totalScore;
    }
}
