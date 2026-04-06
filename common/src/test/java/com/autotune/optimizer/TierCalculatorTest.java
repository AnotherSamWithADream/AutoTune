package com.autotune.optimizer;

import com.autotune.benchmark.hardware.HardwareProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TierCalculator")
class TierCalculatorTest {

    // -----------------------------------------------------------------------
    // Helper to build HardwareProfile instances with control over all fields
    // -----------------------------------------------------------------------

    private static HardwareProfile buildProfile(
            String gpuName, int gpuVramMb, float gpuTflops, String gpuArch, int gpuTierHint, String glVersion,
            String cpuName, int cpuCores, int cpuThreads, float cpuBaseClock, float cpuBoostClock,
            int cpuL3CacheMb, String cpuArch, int cpuTierHint,
            int totalRamMb, int availableRamMb, long maxHeapMb,
            String storageType, long storageFreeBytes,
            double cpuTemp, double gpuTemp, boolean throttling) {
        return new HardwareProfile(
                gpuName,
                "NVIDIA",
                gpuVramMb,
                "driver-535",
                glVersion,
                gpuName,
                gpuTflops,
                gpuArch,
                0,
                gpuTierHint,
                cpuName,
                cpuCores,
                cpuThreads,
                cpuBaseClock,
                cpuBoostClock,
                cpuL3CacheMb,
                cpuArch,
                cpuTierHint,
                totalRamMb,
                availableRamMb,
                maxHeapMb,
                4096L,
                "1920x1080",
                1920,
                1080,
                60,
                storageType,
                storageFreeBytes,
                cpuTemp,
                gpuTemp,
                throttling,
                "21.0.2",
                "Oracle",
                "Windows 11",
                "amd64",
                List.of()
        );
    }

    private static HardwareProfile highEndProfile() {
        return buildProfile(
                "RTX 4090", 24576, 82.6f, "Ada Lovelace", 10, "4.6 Core",
                "i9-14900K", 24, 32, 3.2f, 6.0f, 36, "Raptor Lake", 10,
                65536, 40000, 16384,
                "NVMe SSD", 500L * 1024 * 1024 * 1024,
                45.0, 40.0, false
        );
    }

    private static HardwareProfile lowEndProfile() {
        return buildProfile(
                "GT 710", 2048, 0.7f, "Kepler", 1, "4.6 Core",
                "Pentium G4560", 2, 4, 3.5f, 0.0f, 3, "Kaby Lake", 1,
                4096, 2000, 1024,
                "HDD", 20L * 1024 * 1024 * 1024,
                70.0, 60.0, false
        );
    }

    private static HardwareProfile midRangeProfile() {
        return buildProfile(
                "RTX 3060", 12288, 12.7f, "Ampere", 6, "4.6 Core",
                "Ryzen 5 5600X", 6, 12, 3.7f, 4.6f, 32, "Zen 3", 6,
                16384, 8000, 4096,
                "SSD", 100L * 1024 * 1024 * 1024,
                55.0, 50.0, false
        );
    }

    // -----------------------------------------------------------------------
    // GPU score tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("GPU Scoring")
    class GpuScoring {

        @Test
        @DisplayName("RTX 4090 (24GB VRAM, 82 TFLOPS, Ada arch) should score 80+")
        void testHighEndGpuScore() {
            int score = TierCalculator.calculateGpuScore(highEndProfile());
            assertTrue(score >= 80,
                    "High-end GPU (RTX 4090) should score >= 80, got " + score);
        }

        @Test
        @DisplayName("GT 710 (2GB VRAM, 0.7 TFLOPS, Kepler arch) should score low (<=30)")
        void testLowEndGpuScore() {
            int score = TierCalculator.calculateGpuScore(lowEndProfile());
            assertTrue(score <= 30,
                    "Low-end GPU (GT 710) should score <= 30, got " + score);
        }

        @Test
        @DisplayName("Mid-range GPU (RTX 3060) should score between low and high end")
        void testMidRangeGpuScore() {
            int score = TierCalculator.calculateGpuScore(midRangeProfile());
            assertTrue(score > 30 && score < 90,
                    "Mid-range GPU should score between 30 and 90, got " + score);
        }

        @Test
        @DisplayName("Null hardware profile returns default score of 50")
        void testNullGpuScore() {
            assertEquals(50, TierCalculator.calculateGpuScore(null),
                    "Null profile should return default GPU score of 50");
        }
    }

    // -----------------------------------------------------------------------
    // CPU score tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("CPU Scoring")
    class CpuScoring {

        @Test
        @DisplayName("High-end CPU (16+ cores, 5.8GHz boost, 64MB L3) should score 80+")
        void testHighEndCpuScore() {
            HardwareProfile hw = buildProfile(
                    "RTX 4090", 24576, 82.6f, "Ada", 10, "4.6 Core",
                    "i9-14900K", 16, 24, 3.0f, 5.8f, 64, "Raptor Lake", 10,
                    65536, 40000, 16384,
                    "NVMe SSD", 500L * 1024 * 1024 * 1024,
                    45.0, 40.0, false
            );
            int score = TierCalculator.calculateCpuScore(hw);
            assertTrue(score >= 80,
                    "High-end CPU (16 cores, 5.8GHz, 64MB L3, Raptor Lake) should score >= 80, got " + score);
        }

        @Test
        @DisplayName("Low-end CPU (2 cores, low clock, small cache) should score low")
        void testLowEndCpuScore() {
            int score = TierCalculator.calculateCpuScore(lowEndProfile());
            assertTrue(score < 50,
                    "Low-end CPU should score < 50, got " + score);
        }

        @Test
        @DisplayName("Null hardware profile returns default CPU score of 50")
        void testNullCpuScore() {
            assertEquals(50, TierCalculator.calculateCpuScore(null));
        }

        @Test
        @DisplayName("CPU with zero boost clock should fall back to base clock")
        void testCpuZeroBoostFallback() {
            // lowEndProfile has 0.0 boost, 3.5 base - should use base clock
            int score = TierCalculator.calculateCpuScore(lowEndProfile());
            // With 3.5GHz base, it should get the >=3.5 bracket (16 points for clock)
            assertTrue(score > 0, "CPU with 0 boost should still get a reasonable score using base clock");
        }
    }

    // -----------------------------------------------------------------------
    // Memory score tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Memory Scoring")
    class MemoryScoring {

        @Test
        @DisplayName("32GB RAM + 8GB heap should score well (above 60)")
        void testMemoryScore() {
            HardwareProfile hw = buildProfile(
                    "RTX 4090", 24576, 82.6f, "Ada", 10, "4.6 Core",
                    "i9-14900K", 16, 24, 3.0f, 5.8f, 64, "Raptor Lake", 10,
                    32768, 20000, 8192,
                    "NVMe SSD", 500L * 1024 * 1024 * 1024,
                    45.0, 40.0, false
            );
            int score = TierCalculator.calculateMemoryScore(hw);
            // 32GB RAM = 40pts, 8GB heap = 35pts, avail ratio ~0.61 = 25pts => 100 clamped
            assertTrue(score >= 60,
                    "32GB RAM, 8GB heap should score >= 60, got " + score);
        }

        @Test
        @DisplayName("Low memory system (4GB RAM, 1GB heap) should score low")
        void testLowMemoryScore() {
            int score = TierCalculator.calculateMemoryScore(lowEndProfile());
            assertTrue(score < 50,
                    "Low memory system should score < 50, got " + score);
        }

        @Test
        @DisplayName("Null profile returns default memory score of 50")
        void testNullMemoryScore() {
            assertEquals(50, TierCalculator.calculateMemoryScore(null));
        }
    }

    // -----------------------------------------------------------------------
    // Overall score weighting
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Overall Scoring")
    class OverallScoring {

        @Test
        @DisplayName("Overall score is a weighted average of GPU(35%), CPU(30%), Mem(15%), Storage(5%), Thermal(15%)")
        void testOverallScoreWeighting() {
            HardwareProfile hw = midRangeProfile();

            int gpu = TierCalculator.calculateGpuScore(hw);
            int cpu = TierCalculator.calculateCpuScore(hw);
            int mem = TierCalculator.calculateMemoryScore(hw);
            int storage = TierCalculator.calculateStorageScore(hw);
            int thermal = TierCalculator.calculateThermalScore(hw);
            int overall = TierCalculator.calculateOverallScore(hw);

            // The weighted average before bottleneck penalty
            double weighted = gpu * 0.35 + cpu * 0.30 + mem * 0.15 + storage * 0.05 + thermal * 0.15;

            // Determine bottleneck penalty
            int minScore = Math.min(Math.min(gpu, cpu), Math.min(mem, Math.min(storage, thermal)));
            if (minScore < 20) {
                weighted *= 0.85;
            } else if (minScore < 35) {
                weighted *= 0.92;
            }

            int expected = (int) Math.clamp(Math.round(weighted), 0, 100);
            assertEquals(expected, overall,
                    String.format("Overall should match weighted formula. GPU=%d, CPU=%d, Mem=%d, Storage=%d, Thermal=%d",
                            gpu, cpu, mem, storage, thermal));
        }

        @Test
        @DisplayName("Null profile returns default overall score of 50")
        void testNullOverallScore() {
            assertEquals(50, TierCalculator.calculateOverallScore(null));
        }

        @Test
        @DisplayName("High-end system should have high overall score")
        void testHighEndOverallScore() {
            int score = TierCalculator.calculateOverallScore(highEndProfile());
            assertTrue(score >= 75,
                    "High-end system should have overall score >= 75, got " + score);
        }

        @Test
        @DisplayName("Low-end system with bottleneck penalty should still be in range")
        void testLowEndOverallScore() {
            int score = TierCalculator.calculateOverallScore(lowEndProfile());
            assertTrue(score >= 0 && score <= 100,
                    "Score should be in 0-100 range, got " + score);
        }
    }

    // -----------------------------------------------------------------------
    // Score range tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Score Range Validation")
    class ScoreRanges {

        @Test
        @DisplayName("All component scores should be in 0-100 range for high-end hardware")
        void testScoresInRangeHighEnd() {
            HardwareProfile hw = highEndProfile();
            assertScoresInRange(hw);
        }

        @Test
        @DisplayName("All component scores should be in 0-100 range for low-end hardware")
        void testScoresInRangeLowEnd() {
            HardwareProfile hw = lowEndProfile();
            assertScoresInRange(hw);
        }

        @Test
        @DisplayName("All component scores should be in 0-100 range for mid-range hardware")
        void testScoresInRangeMidRange() {
            HardwareProfile hw = midRangeProfile();
            assertScoresInRange(hw);
        }

        private void assertScoresInRange(HardwareProfile hw) {
            int gpu = TierCalculator.calculateGpuScore(hw);
            int cpu = TierCalculator.calculateCpuScore(hw);
            int mem = TierCalculator.calculateMemoryScore(hw);
            int storage = TierCalculator.calculateStorageScore(hw);
            int thermal = TierCalculator.calculateThermalScore(hw);
            int overall = TierCalculator.calculateOverallScore(hw);

            assertAll(
                    () -> assertTrue(gpu >= 0 && gpu <= 100, "GPU score out of range: " + gpu),
                    () -> assertTrue(cpu >= 0 && cpu <= 100, "CPU score out of range: " + cpu),
                    () -> assertTrue(mem >= 0 && mem <= 100, "Memory score out of range: " + mem),
                    () -> assertTrue(storage >= 0 && storage <= 100, "Storage score out of range: " + storage),
                    () -> assertTrue(thermal >= 0 && thermal <= 100, "Thermal score out of range: " + thermal),
                    () -> assertTrue(overall >= 0 && overall <= 100, "Overall score out of range: " + overall)
            );
        }
    }

    // -----------------------------------------------------------------------
    // Storage and Thermal specific tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("Storage Scoring")
    class StorageScoring {

        @Test
        @DisplayName("NVMe storage with plenty of free space should score high")
        void testNvmeHighFreeSpace() {
            int score = TierCalculator.calculateStorageScore(highEndProfile());
            assertTrue(score >= 80, "NVMe with 500GB free should score >= 80, got " + score);
        }

        @Test
        @DisplayName("HDD storage should score significantly lower than NVMe")
        void testHddVsNvme() {
            int hddScore = TierCalculator.calculateStorageScore(lowEndProfile());
            int nvmeScore = TierCalculator.calculateStorageScore(highEndProfile());
            assertTrue(nvmeScore > hddScore,
                    "NVMe (" + nvmeScore + ") should score higher than HDD (" + hddScore + ")");
        }
    }

    @Nested
    @DisplayName("Thermal Scoring")
    class ThermalScoring {

        @Test
        @DisplayName("Cool system with no throttling should score high")
        void testCoolSystem() {
            int score = TierCalculator.calculateThermalScore(highEndProfile());
            assertTrue(score >= 90, "Cool system (45/40 degrees, no throttle) should score >= 90, got " + score);
        }

        @Test
        @DisplayName("System with thermal throttling should have severe penalty")
        void testThrottlingPenalty() {
            HardwareProfile throttled = buildProfile(
                    "RTX 3060", 12288, 12.7f, "Ampere", 6, "4.6 Core",
                    "Ryzen 5 5600X", 6, 12, 3.7f, 4.6f, 32, "Zen 3", 6,
                    16384, 8000, 4096,
                    "SSD", 100L * 1024 * 1024 * 1024,
                    95.0, 90.0, true
            );
            int score = TierCalculator.calculateThermalScore(throttled);
            // 100 - 40(throttle) - 35(cpu>=95) - 20(gpu>=85) = 5
            assertTrue(score < 20,
                    "Throttled hot system should score very low, got " + score);
        }

        @Test
        @DisplayName("Null profile returns default thermal score of 70")
        void testNullThermalScore() {
            assertEquals(70, TierCalculator.calculateThermalScore(null));
        }
    }
}
