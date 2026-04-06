package com.autotune.optimizer;

import com.autotune.benchmark.BenchmarkResult;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.benchmark.hardware.HardwareProfile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Analyzes benchmark results and hardware profiles to identify performance
 * bottlenecks that limit the player's framerate.
 */
public class BottleneckAnalyzer {

    /**
     * The primary bottleneck type.
     */
    public enum Bottleneck {
        GPU,
        CPU,
        MEMORY,
        STORAGE,
        THERMAL,
        BALANCED
    }

    /**
     * Results of bottleneck analysis.
     */
    public record BottleneckResult(
            Bottleneck primaryBottleneck,
            String explanation,
            Map<String, Double> scores
    ) {
        /**
         * Returns the score for a specific component (0.0 - 1.0 where lower = more bottlenecked).
         */
        public double getScore(String component) {
            return scores.getOrDefault(component, 0.5);
        }
    }

    /**
     * Analyzes the benchmark results against hardware to determine the primary bottleneck.
     *
     * @param results  completed benchmark results
     * @param hardware detected hardware profile
     * @return analysis result with bottleneck identification and component scores
     */
    public BottleneckResult analyze(BenchmarkResult results, HardwareProfile hardware) {
        Map<String, Double> scores = new LinkedHashMap<>();

        double gpuScore = analyzeGpuBottleneck(results, hardware);
        double cpuScore = analyzeCpuBottleneck(results, hardware);
        double memoryScore = analyzeMemoryBottleneck(results, hardware);
        double storageScore = analyzeStorageBottleneck(results, hardware);
        double thermalScore = analyzeThermalBottleneck(results, hardware);

        scores.put("gpu", gpuScore);
        scores.put("cpu", cpuScore);
        scores.put("memory", memoryScore);
        scores.put("storage", storageScore);
        scores.put("thermal", thermalScore);

        // Determine primary bottleneck: lowest score = most bottlenecked
        Bottleneck primary = Bottleneck.BALANCED;
        double lowestScore = Double.MAX_VALUE;
        double highestScore = -Double.MAX_VALUE;

        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            if (entry.getValue() < lowestScore) {
                lowestScore = entry.getValue();
                primary = switch (entry.getKey()) {
                    case "gpu" -> Bottleneck.GPU;
                    case "cpu" -> Bottleneck.CPU;
                    case "memory" -> Bottleneck.MEMORY;
                    case "storage" -> Bottleneck.STORAGE;
                    case "thermal" -> Bottleneck.THERMAL;
                    default -> Bottleneck.BALANCED;
                };
            }
            if (entry.getValue() > highestScore) {
                highestScore = entry.getValue();
            }
        }

        // If the spread between lowest and highest is small, it's balanced
        if (highestScore - lowestScore < 0.15) {
            primary = Bottleneck.BALANCED;
        }

        String explanation = buildExplanation(primary, scores, hardware);

        return new BottleneckResult(primary, explanation, scores);
    }

    private double analyzeGpuBottleneck(BenchmarkResult results, HardwareProfile hardware) {
        double score = 0.5;

        // Check render distance scaling behavior: if FPS drops steeply with RD increase, GPU-bound
        PhaseResult rdPhase = results.getPhaseResult("render_distance_sweep");
        if (rdPhase != null && !rdPhase.isSkipped()) {
            double rdSensitivity = measureSensitivity(rdPhase, "rd_");
            // High sensitivity to render distance changes suggests GPU bottleneck
            score -= rdSensitivity * 0.3;
        }

        // Check graphics mode sensitivity
        PhaseResult gmPhase = results.getPhaseResult("graphics_mode_sweep");
        if (gmPhase != null && !gmPhase.isSkipped()) {
            double gmSensitivity = measureSensitivity(gmPhase, "");
            score -= gmSensitivity * 0.2;
        }

        // Hardware-based heuristics
        int gpuTier = TierCalculator.calculateGpuScore(hardware);
        if (gpuTier < 30) score -= 0.15;
        else if (gpuTier < 50) score -= 0.05;
        else if (gpuTier > 75) score += 0.15;

        // VRAM check: low VRAM is a GPU constraint
        if (hardware.gpuVramMb() < 2048) score -= 0.15;
        else if (hardware.gpuVramMb() < 4096) score -= 0.05;

        // Resolution factor: higher resolution = more GPU pressure
        int pixels = hardware.displayWidth() * hardware.displayHeight();
        if (pixels > 3840 * 2160) score -= 0.10;
        else if (pixels > 2560 * 1440) score -= 0.05;

        return clamp(score);
    }

    private double analyzeCpuBottleneck(BenchmarkResult results, HardwareProfile hardware) {
        double score = 0.5;

        // Simulation distance is CPU-heavy
        PhaseResult simPhase = results.getPhaseResult("simulation_distance_sweep");
        if (simPhase != null && !simPhase.isSkipped()) {
            double simSensitivity = measureSensitivity(simPhase, "sd_");
            score -= simSensitivity * 0.3;
        }

        // Entity distance scaling is partly CPU
        PhaseResult entityPhase = results.getPhaseResult("entity_distance_sweep");
        if (entityPhase != null && !entityPhase.isSkipped()) {
            double entitySensitivity = measureSensitivity(entityPhase, "ed_");
            score -= entitySensitivity * 0.15;
        }

        // Check for high frame time variance (CPU hitches from GC, chunk loading)
        double avgStdDev = getAverageStdDev(results);
        if (avgStdDev > 10.0) score -= 0.15;
        else if (avgStdDev > 5.0) score -= 0.08;

        // Hardware heuristics
        int cpuTier = TierCalculator.calculateCpuScore(hardware);
        if (cpuTier < 30) score -= 0.15;
        else if (cpuTier < 50) score -= 0.05;
        else if (cpuTier > 75) score += 0.15;

        // Low single-thread clock is a strong CPU bottleneck indicator for MC
        float boostGhz = hardware.cpuBoostClockGhz();
        if (boostGhz > 0 && boostGhz < 3.5f) score -= 0.10;

        return clamp(score);
    }

    private double analyzeMemoryBottleneck(BenchmarkResult results, HardwareProfile hardware) {
        double score = 0.5;

        // Memory pressure indicators
        int memTier = TierCalculator.calculateMemoryScore(hardware);
        if (memTier < 30) score -= 0.20;
        else if (memTier < 50) score -= 0.10;
        else if (memTier > 75) score += 0.15;

        // Check allocated vs max heap
        long allocated = hardware.allocatedHeapMb();
        long maxHeap = hardware.maxHeapMb();
        if (maxHeap > 0) {
            double heapUsageRatio = (double) allocated / maxHeap;
            if (heapUsageRatio > 0.9) score -= 0.20;
            else if (heapUsageRatio > 0.75) score -= 0.10;
            else if (heapUsageRatio < 0.5) score += 0.10;
        }

        // Low total RAM
        if (hardware.totalRamMb() < 4096) score -= 0.15;
        else if (hardware.totalRamMb() < 8192) score -= 0.05;

        // High stutter count could indicate GC pressure (memory)
        double avgStutters = getAverageStutterCount(results);
        if (avgStutters > 10) score -= 0.15;
        else if (avgStutters > 5) score -= 0.08;

        return clamp(score);
    }

    private double analyzeStorageBottleneck(BenchmarkResult results, HardwareProfile hardware) {
        double score = 0.5;

        int storageTier = TierCalculator.calculateStorageScore(hardware);
        if (storageTier < 30) score -= 0.15;
        else if (storageTier < 50) score -= 0.05;
        else if (storageTier > 75) score += 0.15;

        // HDD is a known MC bottleneck for chunk loading
        String storageType = hardware.storageType() != null ? hardware.storageType().toLowerCase() : "";
        if (storageType.contains("hdd") || storageType.contains("mechanical")) {
            score -= 0.20;
        }

        // Low free space can cause I/O slowdowns
        long freeGb = hardware.storageFreeBytes() / (1024L * 1024L * 1024L);
        if (freeGb < 5) score -= 0.10;
        else if (freeGb < 10) score -= 0.05;

        return clamp(score);
    }

    private double analyzeThermalBottleneck(BenchmarkResult results, HardwareProfile hardware) {
        double score = 0.5;

        int thermalTier = TierCalculator.calculateThermalScore(hardware);
        if (thermalTier < 30) score -= 0.25;
        else if (thermalTier < 50) score -= 0.10;
        else if (thermalTier > 75) score += 0.15;

        // Direct thermal throttling detected
        if (hardware.thermalThrottlingDetected()) {
            score -= 0.30;
        }

        // [CODE-REVIEW-FIX] M-005: Trend slope is now normalized to REFERENCE_BUFFER_SIZE (300)
        // in getAverageTrendSlope(), so these thresholds are valid regardless of actual buffer sizes.
        // 100,000 = significant thermal degradation over 300-frame equivalent window.
        // 50,000  = mild degradation over 300-frame equivalent window.
        double avgTrend = getAverageTrendSlope(results);
        if (avgTrend > 100_000) score -= 0.15;  // Getting worse over time = thermal
        else if (avgTrend > 50_000) score -= 0.08;

        // High temps
        if (hardware.cpuTemperature() > 85 || hardware.gpuTemperature() > 85) {
            score -= 0.15;
        }

        return clamp(score);
    }

    /**
     * Measures sensitivity of FPS to setting changes within a benchmark phase.
     * Returns 0.0-1.0 where higher means more sensitive (bigger FPS drops).
     */
    private double measureSensitivity(PhaseResult phase, String labelPrefix) {
        Map<String, FrameTimeStatistics> measurements = phase.getMeasurements();
        if (measurements.size() < 2) return 0.0;

        double maxFps = -Double.MAX_VALUE;
        double minFps = Double.MAX_VALUE;

        for (Map.Entry<String, FrameTimeStatistics> entry : measurements.entrySet()) {
            if (!labelPrefix.isEmpty() && !entry.getKey().startsWith(labelPrefix)) continue;
            double fps = entry.getValue().avgFps();
            if (fps > maxFps) maxFps = fps;
            if (fps < minFps) minFps = fps;
        }

        if (maxFps <= 0) return 0.0;
        double dropRatio = (maxFps - minFps) / maxFps;
        return Math.min(1.0, dropRatio);
    }

    private double getAverageStdDev(BenchmarkResult results) {
        double totalStdDev = 0;
        int count = 0;
        for (PhaseResult phase : results.getPhaseResults().values()) {
            if (phase.isSkipped()) continue;
            for (FrameTimeStatistics stats : phase.getMeasurements().values()) {
                totalStdDev += stats.stdDevMs();
                count++;
            }
        }
        return count > 0 ? totalStdDev / count : 0;
    }

    private double getAverageStutterCount(BenchmarkResult results) {
        double totalStutters = 0;
        int count = 0;
        for (PhaseResult phase : results.getPhaseResults().values()) {
            if (phase.isSkipped()) continue;
            for (FrameTimeStatistics stats : phase.getMeasurements().values()) {
                totalStutters += stats.stutterCount();
                count++;
            }
        }
        return count > 0 ? totalStutters / count : 0;
    }

    // [CODE-REVIEW-FIX] M-005: Normalize trend slopes by sample count so that thresholds
    // are consistent regardless of whether a phase uses 300, 600, or 1000 frames.
    // The raw trendSlope (nanos/frame) is multiplied by sampleCount to get total drift,
    // then divided by REFERENCE_BUFFER_SIZE to normalize to a common basis.
    private static final int REFERENCE_BUFFER_SIZE = 300;

    private double getAverageTrendSlope(BenchmarkResult results) {
        double totalNormalizedSlope = 0;
        int count = 0;
        for (PhaseResult phase : results.getPhaseResults().values()) {
            if (phase.isSkipped()) continue;
            for (FrameTimeStatistics stats : phase.getMeasurements().values()) {
                int samples = stats.sampleCount();
                if (samples > 0) {
                    // Normalize: convert slope to total drift, then scale to reference buffer size
                    double normalizedSlope = stats.trendSlope() * samples / REFERENCE_BUFFER_SIZE;
                    totalNormalizedSlope += normalizedSlope;
                } else {
                    totalNormalizedSlope += stats.trendSlope();
                }
                count++;
            }
        }
        return count > 0 ? totalNormalizedSlope / count : 0;
    }

    private String buildExplanation(Bottleneck primary, Map<String, Double> scores,
                                     HardwareProfile hardware) {
        StringBuilder sb = new StringBuilder();

        switch (primary) {
            case GPU -> {
                sb.append("Your GPU (").append(hardware.gpuName())
                        .append(") appears to be the primary performance bottleneck. ");
                if (hardware.gpuVramMb() < 4096) {
                    sb.append("Limited VRAM (").append(hardware.gpuVramMb())
                            .append(" MB) may cause texture swapping at high settings. ");
                }
                sb.append("Reducing render distance, disabling fancy graphics, ");
                sb.append("and lowering particle effects will have the most impact.");
            }
            case CPU -> {
                sb.append("Your CPU (").append(hardware.cpuName())
                        .append(") appears to be the primary performance bottleneck. ");
                sb.append("Minecraft is heavily single-threaded. ");
                if (hardware.cpuBoostClockGhz() < 4.0f) {
                    sb.append("A boost clock under 4 GHz limits Minecraft performance. ");
                }
                sb.append("Reducing simulation distance, entity counts, and chunk updates ");
                sb.append("will have the most impact.");
            }
            case MEMORY -> {
                sb.append("Memory appears to be a bottleneck. ");
                sb.append("With ").append(hardware.totalRamMb()).append(" MB total RAM ");
                sb.append("and ").append(hardware.maxHeapMb()).append(" MB max heap, ");
                sb.append("GC pauses may cause frame stutters. ");
                sb.append("Consider allocating more RAM to Minecraft or closing background applications.");
            }
            case STORAGE -> {
                sb.append("Storage I/O appears to be a bottleneck. ");
                String storageType = hardware.storageType() != null ? hardware.storageType() : "Unknown";
                sb.append("Storage type: ").append(storageType).append(". ");
                if (storageType.toLowerCase().contains("hdd")) {
                    sb.append("An HDD causes slow chunk loading. Upgrading to an SSD ");
                    sb.append("would significantly improve world loading times.");
                } else {
                    sb.append("Ensure sufficient free space and minimize background disk activity.");
                }
            }
            case THERMAL -> {
                sb.append("Thermal throttling is limiting performance. ");
                if (hardware.thermalThrottlingDetected()) {
                    sb.append("Active thermal throttling was detected during benchmarking. ");
                }
                if (hardware.cpuTemperature() > 85) {
                    sb.append("CPU temperature (").append(String.format("%.0f", hardware.cpuTemperature()))
                            .append("C) is critically high. ");
                }
                if (hardware.gpuTemperature() > 85) {
                    sb.append("GPU temperature (").append(String.format("%.0f", hardware.gpuTemperature()))
                            .append("C) is critically high. ");
                }
                sb.append("Improving cooling or reducing workload will stabilize performance.");
            }
            case BALANCED -> {
                sb.append("No single component is a clear bottleneck. ");
                sb.append("Your system is reasonably balanced for Minecraft. ");
                sb.append("Optimization will focus on finding the best overall settings balance.");
            }
        }

        return sb.toString();
    }

    private static double clamp(double value) {
        return Math.clamp(value, 0.0, 1.0);
    }
}
