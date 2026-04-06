package com.autotune.optimizer;

import com.autotune.benchmark.hardware.HardwareProfile;

/**
 * Calculates hardware tier scores (0-100) for each component based on
 * detected hardware specifications. Scores represent approximate capability
 * for running Minecraft with mods.
 */
public class TierCalculator {

    /**
     * Calculates a GPU score from 0-100 based on VRAM, TFLOPS, architecture,
     * and generation hints.
     */
    public static int calculateGpuScore(HardwareProfile hw) {
        if (hw == null) return 50;

        double score = 0.0;

        // VRAM component (0-30 points)
        int vramMb = hw.gpuVramMb();
        if (vramMb >= 12288) score += 30;
        else if (vramMb >= 8192) score += 25;
        else if (vramMb >= 6144) score += 20;
        else if (vramMb >= 4096) score += 15;
        else if (vramMb >= 2048) score += 8;
        else score += 3;

        // TFLOPS component (0-35 points)
        float tflops = hw.gpuTflops();
        if (tflops >= 30.0f) score += 35;
        else if (tflops >= 20.0f) score += 30;
        else if (tflops >= 12.0f) score += 25;
        else if (tflops >= 8.0f) score += 20;
        else if (tflops >= 5.0f) score += 15;
        else if (tflops >= 2.0f) score += 10;
        else if (tflops > 0.0f) score += 5;
        else score += 10; // Unknown, assume mid-range

        // GPU generation/tier hint (0-20 points)
        int tierHint = hw.gpuTierHint();
        if (tierHint > 0) {
            score += Math.min(20, tierHint * 2);
        } else {
            // Estimate from architecture string
            String arch = hw.gpuArchitecture() != null ? hw.gpuArchitecture().toLowerCase() : "";
            if (arch.contains("ada") || arch.contains("rdna3") || arch.contains("rdna 3")) score += 18;
            else if (arch.contains("ampere") || arch.contains("rdna2") || arch.contains("rdna 2")) score += 15;
            else if (arch.contains("turing") || arch.contains("rdna") || arch.contains("navi")) score += 12;
            else if (arch.contains("pascal") || arch.contains("polaris") || arch.contains("vega")) score += 8;
            else score += 6;
        }

        // GL version bonus (0-15 points)
        String glVersion = hw.glVersion() != null ? hw.glVersion() : "";
        if (glVersion.contains("4.6")) score += 15;
        else if (glVersion.contains("4.5")) score += 13;
        else if (glVersion.contains("4.")) score += 10;
        else if (glVersion.contains("3.3")) score += 7;
        else score += 5;

        return clampScore(score);
    }

    /**
     * Calculates a CPU score from 0-100 based on core count, clock speed,
     * cache size, and architecture.
     */
    public static int calculateCpuScore(HardwareProfile hw) {
        if (hw == null) return 50;

        double score = 0.0;

        // Core count (0-20 points) - Minecraft is primarily single-threaded but mods benefit from cores
        int cores = hw.cpuCores();
        if (cores >= 16) score += 20;
        else if (cores >= 12) score += 18;
        else if (cores >= 8) score += 16;
        else if (cores >= 6) score += 13;
        else if (cores >= 4) score += 10;
        else if (cores >= 2) score += 6;
        else score += 3;

        // Boost clock (0-30 points) - critical for MC single-thread perf
        float boostGhz = hw.cpuBoostClockGhz();
        if (boostGhz <= 0) boostGhz = hw.cpuBaseClockGhz();
        if (boostGhz >= 5.5f) score += 30;
        else if (boostGhz >= 5.0f) score += 27;
        else if (boostGhz >= 4.5f) score += 24;
        else if (boostGhz >= 4.0f) score += 20;
        else if (boostGhz >= 3.5f) score += 16;
        else if (boostGhz >= 3.0f) score += 12;
        else if (boostGhz >= 2.0f) score += 8;
        else score += 4;

        // L3 cache (0-20 points)
        int l3Mb = hw.cpuL3CacheMb();
        if (l3Mb >= 64) score += 20;
        else if (l3Mb >= 32) score += 17;
        else if (l3Mb >= 16) score += 14;
        else if (l3Mb >= 8) score += 10;
        else if (l3Mb >= 4) score += 7;
        else score += 3;

        // Architecture bonus (0-15 points)
        String arch = hw.cpuArchitecture() != null ? hw.cpuArchitecture().toLowerCase() : "";
        if (arch.contains("raptor") || arch.contains("zen 4") || arch.contains("zen4")
                || arch.contains("zen 5") || arch.contains("zen5") || arch.contains("meteor")) {
            score += 15;
        } else if (arch.contains("alder") || arch.contains("zen 3") || arch.contains("zen3")) {
            score += 12;
        } else if (arch.contains("rocket") || arch.contains("zen 2") || arch.contains("zen2")
                || arch.contains("comet")) {
            score += 9;
        } else if (arch.contains("coffee") || arch.contains("zen+") || arch.contains("zen 1")) {
            score += 7;
        } else {
            score += 5;
        }

        // Tier hint bonus (0-15 points)
        int tierHint = hw.cpuTierHint();
        if (tierHint > 0) {
            score += Math.min(15, tierHint * 1.5);
        } else {
            score += 7;
        }

        return clampScore(score);
    }

    /**
     * Calculates a memory score from 0-100 based on total RAM and available heap.
     */
    public static int calculateMemoryScore(HardwareProfile hw) {
        if (hw == null) return 50;

        double score = 0.0;

        // Total system RAM (0-40 points)
        int totalRamMb = hw.totalRamMb();
        if (totalRamMb >= 32768) score += 40;
        else if (totalRamMb >= 16384) score += 35;
        else if (totalRamMb >= 12288) score += 28;
        else if (totalRamMb >= 8192) score += 20;
        else if (totalRamMb >= 4096) score += 10;
        else score += 5;

        // Max heap available (0-35 points)
        long maxHeapMb = hw.maxHeapMb();
        if (maxHeapMb >= 8192) score += 35;
        else if (maxHeapMb >= 6144) score += 30;
        else if (maxHeapMb >= 4096) score += 25;
        else if (maxHeapMb >= 3072) score += 20;
        else if (maxHeapMb >= 2048) score += 15;
        else if (maxHeapMb >= 1024) score += 8;
        else score += 4;

        // Available RAM headroom (0-25 points)
        int availRamMb = hw.availableRamMb();
        double availRatio = totalRamMb > 0 ? (double) availRamMb / totalRamMb : 0.5;
        if (availRatio >= 0.5) score += 25;
        else if (availRatio >= 0.35) score += 20;
        else if (availRatio >= 0.25) score += 15;
        else if (availRatio >= 0.15) score += 10;
        else score += 5;

        return clampScore(score);
    }

    /**
     * Calculates a storage score from 0-100 based on storage type and free space.
     */
    public static int calculateStorageScore(HardwareProfile hw) {
        if (hw == null) return 50;

        double score = 0.0;

        // Storage type (0-60 points)
        String storageType = hw.storageType() != null ? hw.storageType().toLowerCase() : "unknown";
        if (storageType.contains("nvme") || storageType.contains("pcie")) {
            score += 60;
        } else if (storageType.contains("ssd") || storageType.contains("solid")) {
            score += 45;
        } else if (storageType.contains("hdd") || storageType.contains("mechanical")
                || storageType.contains("spinning")) {
            score += 20;
        } else {
            score += 35; // Unknown, assume SSD-ish
        }

        // Free space (0-40 points)
        long freeGb = hw.storageFreeBytes() / (1024L * 1024L * 1024L);
        if (freeGb >= 100) score += 40;
        else if (freeGb >= 50) score += 35;
        else if (freeGb >= 20) score += 25;
        else if (freeGb >= 10) score += 15;
        else if (freeGb >= 5) score += 8;
        else score += 3;

        return clampScore(score);
    }

    /**
     * Calculates a thermal score from 0-100. Lower temperatures and no throttling = higher score.
     */
    public static int calculateThermalScore(HardwareProfile hw) {
        if (hw == null) return 70;

        double score = 100.0;

        // Thermal throttling is a severe penalty
        if (hw.thermalThrottlingDetected()) {
            score -= 40;
        }

        // CPU temperature penalty
        double cpuTemp = hw.cpuTemperature();
        if (cpuTemp > 0) {
            if (cpuTemp >= 95) score -= 35;
            else if (cpuTemp >= 85) score -= 25;
            else if (cpuTemp >= 75) score -= 15;
            else if (cpuTemp >= 65) score -= 8;
            else if (cpuTemp >= 55) score -= 3;
            // Below 55 is excellent, no penalty
        }

        // GPU temperature penalty
        double gpuTemp = hw.gpuTemperature();
        if (gpuTemp > 0) {
            if (gpuTemp >= 95) score -= 30;
            else if (gpuTemp >= 85) score -= 20;
            else if (gpuTemp >= 75) score -= 10;
            else if (gpuTemp >= 65) score -= 5;
            // Below 65 is fine
        }

        return clampScore(score);
    }

    /**
     * Calculates a weighted overall score from all component scores.
     * GPU is weighted highest for Minecraft rendering, followed by CPU.
     */
    public static int calculateOverallScore(HardwareProfile hw) {
        if (hw == null) return 50;

        int gpu = calculateGpuScore(hw);
        int cpu = calculateCpuScore(hw);
        int mem = calculateMemoryScore(hw);
        int storage = calculateStorageScore(hw);
        int thermal = calculateThermalScore(hw);

        // Weights: GPU 35%, CPU 30%, Memory 15%, Storage 5%, Thermal 15%
        double weighted = gpu * 0.35 + cpu * 0.30 + mem * 0.15 + storage * 0.05 + thermal * 0.15;

        // Apply a penalty if any single component is very low (bottleneck effect)
        int minScore = Math.min(Math.min(gpu, cpu), Math.min(mem, Math.min(storage, thermal)));
        if (minScore < 20) {
            weighted *= 0.85;
        } else if (minScore < 35) {
            weighted *= 0.92;
        }

        return clampScore(weighted);
    }

    private static int clampScore(double score) {
        return (int) Math.clamp(Math.round(score), 0, 100);
    }
}
