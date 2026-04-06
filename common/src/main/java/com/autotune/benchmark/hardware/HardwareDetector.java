package com.autotune.benchmark.hardware;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Main hardware detection orchestrator.
 * Calls all individual detectors and enriches results with database data
 * to produce a complete {@link HardwareProfile}.
 */
public final class HardwareDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(HardwareDetector.class);

    private final GPUDatabase gpuDatabase;
    private final CPUDatabase cpuDatabase;

    public HardwareDetector() {
        this.gpuDatabase = GPUDatabase.load();
        this.cpuDatabase = CPUDatabase.load();
    }

    public HardwareDetector(GPUDatabase gpuDatabase, CPUDatabase cpuDatabase) {
        this.gpuDatabase = gpuDatabase;
        this.cpuDatabase = cpuDatabase;
    }

    /**
     * Performs full hardware detection and returns a complete profile.
     * All individual detection steps are wrapped in error handling;
     * failures in one detector do not prevent others from running.
     */
    public HardwareProfile detect(MinecraftClient client) {
        LOGGER.info("Starting hardware detection...");

        // --- GPU Detection ---
        String gpuName = safeCall("GPU name", GPUDetector::detectGpuName, "Unknown GPU");
        String gpuVendor = safeCall("GPU vendor", GPUDetector::detectGpuVendor, "Unknown Vendor");
        String gpuDriver = safeCall("GPU driver", GPUDetector::detectGpuDriver, "Unknown Driver");
        String glVersion = safeCall("GL version", GPUDetector::detectGlVersion, "Unknown");
        String glRenderer = safeCall("GL renderer", GPUDetector::detectGlRenderer, "Unknown");
        List<String> glExtensions = safeCall("GL extensions", GPUDetector::detectGlExtensions, Collections.emptyList());

        String normalizedVendor = GPUDetector.normalizeVendor(gpuVendor);

        // VRAM detection with database fallback
        int gpuVramMb = safeCall("GPU VRAM", () -> GPUDetector.detectVram(glRenderer, gpuDatabase), 0);

        // Enrich GPU data from database
        float gpuTflops = 0.0f;
        String gpuArchitecture = "Unknown";
        int gpuGeneration = 0;
        int gpuTierHint = 0;

        try {
            var gpuEntry = gpuDatabase.match(glRenderer);
            if (gpuEntry.isPresent()) {
                GPUDatabase.GPUEntry entry = gpuEntry.get();
                gpuTflops = entry.tflopsFp32();
                gpuArchitecture = entry.arch();
                gpuGeneration = entry.generation();
                gpuTierHint = entry.tierHint();
                // If VRAM wasn't detected via GL extensions, use database value
                if (gpuVramMb <= 0) {
                    gpuVramMb = entry.vramMb();
                }
                LOGGER.info("Matched GPU in database: {} -> arch={}, gen={}, tier={}",
                    glRenderer, gpuArchitecture, gpuGeneration, gpuTierHint);
            } else {
                LOGGER.info("GPU not found in database: {}", glRenderer);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to enrich GPU data from database", e);
        }

        // --- CPU Detection ---
        String cpuName = safeCall("CPU name", CPUDetector::detectCpuName, "Unknown CPU");
        int cpuLogicalCores = safeCall("CPU core count", CPUDetector::detectCoreCount, 1);
        int cpuPhysicalCores = safeCall("CPU physical cores", CPUDetector::detectPhysicalCoreCount, cpuLogicalCores);

        // Enrich CPU data from database
        float cpuBaseClockGhz = 0.0f;
        float cpuBoostClockGhz = 0.0f;
        int cpuL3CacheMb = 0;
        String cpuArchitecture = "Unknown";
        int cpuTierHint = 0;

        try {
            var cpuEntry = cpuDatabase.match(cpuName);
            if (cpuEntry.isPresent()) {
                CPUDatabase.CPUEntry entry = cpuEntry.get();
                cpuBaseClockGhz = entry.baseClockGhz();
                cpuBoostClockGhz = entry.boostClockGhz();
                cpuL3CacheMb = entry.l3CacheMb();
                cpuArchitecture = entry.architecture();
                cpuTierHint = entry.tierHint();
                // Use database physical core count if our detection returned a dubious value
                if (entry.coresPhysical() > 0 && cpuPhysicalCores <= 0) {
                    cpuPhysicalCores = entry.coresPhysical();
                }
                LOGGER.info("Matched CPU in database: {} -> arch={}, tier={}",
                    cpuName, cpuArchitecture, cpuTierHint);
            } else {
                LOGGER.info("CPU not found in database: {}", cpuName);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to enrich CPU data from database", e);
        }

        // --- Memory Detection ---
        int totalRamMb = safeCall("total RAM", MemoryDetector::detectTotalRam, -1);
        int availableRamMb = safeCall("available RAM", MemoryDetector::detectAvailableRam, -1);
        long maxHeapMb = safeCall("max heap", MemoryDetector::detectMaxHeap, 0L);
        long allocatedHeapMb = safeCall("allocated heap", MemoryDetector::detectAllocatedHeap, 0L);

        // --- Display Detection ---
        String displayResolution = safeCall("display resolution", () -> DisplayDetector.detectResolution(client), "Unknown");
        int displayWidth = safeCall("display width", () -> DisplayDetector.detectWidth(client), 0);
        int displayHeight = safeCall("display height", () -> DisplayDetector.detectHeight(client), 0);
        int displayRefreshRate = safeCall("display refresh rate", () -> DisplayDetector.detectRefreshRate(client), 60);

        // --- Storage Detection ---
        String storageType = safeCall("storage type", () -> StorageDetector.detectStorageType(client), "Unknown");
        long storageFreeBytes = safeCall("storage free space", () -> StorageDetector.detectFreeSpace(client), -1L);

        // --- Thermal Detection ---
        double cpuTemperature = safeCall("CPU temperature", ThermalDetector::detectCpuTemperature, ThermalDetector.UNAVAILABLE);
        double gpuTemperature = safeCall("GPU temperature", ThermalDetector::detectGpuTemperature, ThermalDetector.UNAVAILABLE);
        boolean thermalThrottling = ThermalDetector.detectThermalThrottling(cpuTemperature, gpuTemperature);

        // --- Java Runtime Detection ---
        String javaVersion = safeCall("Java version", JavaRuntimeDetector::detectJavaVersion, "Unknown");
        String javaVendor = safeCall("Java vendor", JavaRuntimeDetector::detectJavaVendor, "Unknown");
        String osName = safeCall("OS name", JavaRuntimeDetector::detectOsName, "Unknown");
        String osArch = safeCall("OS arch", JavaRuntimeDetector::detectOsArch, "Unknown");

        LOGGER.info("Hardware detection complete.");
        LOGGER.info("GPU: {} ({}, {} MB VRAM, tier {})", gpuName, normalizedVendor, gpuVramMb, gpuTierHint);
        LOGGER.info("CPU: {} ({} cores / {} threads, tier {})", cpuName, cpuPhysicalCores, cpuLogicalCores, cpuTierHint);
        LOGGER.info("RAM: {} MB total, {} MB max heap", totalRamMb, maxHeapMb);
        LOGGER.info("Display: {} @ {} Hz", displayResolution, displayRefreshRate);
        LOGGER.info("Storage: {} ({} bytes free)", storageType, storageFreeBytes);

        return new HardwareProfile(
            gpuName,
            normalizedVendor,
            gpuVramMb,
            gpuDriver,
            glVersion,
            glRenderer,
            gpuTflops,
            gpuArchitecture,
            gpuGeneration,
            gpuTierHint,
            cpuName,
            cpuPhysicalCores,
            cpuLogicalCores,
            cpuBaseClockGhz,
            cpuBoostClockGhz,
            cpuL3CacheMb,
            cpuArchitecture,
            cpuTierHint,
            totalRamMb,
            availableRamMb,
            maxHeapMb,
            allocatedHeapMb,
            displayResolution,
            displayWidth,
            displayHeight,
            displayRefreshRate,
            storageType,
            storageFreeBytes,
            cpuTemperature,
            gpuTemperature,
            thermalThrottling,
            javaVersion,
            javaVendor,
            osName,
            osArch,
            glExtensions
        );
    }

    /**
     * Safely calls a supplier, returning the fallback value if any exception occurs.
     */
    private static <T> T safeCall(String label, ThrowingSupplier<T> supplier, T fallback) {
        try {
            T result = supplier.get();
            return result != null ? result : fallback;
        } catch (Exception e) {
            LOGGER.warn("Hardware detection failed for '{}', using fallback: {}", label, fallback, e);
            return fallback;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
