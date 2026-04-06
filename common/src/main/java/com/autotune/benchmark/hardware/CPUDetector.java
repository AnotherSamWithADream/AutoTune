package com.autotune.benchmark.hardware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.TimeUnit;

public final class CPUDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(CPUDetector.class);

    private CPUDetector() {}

    /**
     * Detects the CPU model name.
     * On Windows, reads from WMIC. On Linux, reads from /proc/cpuinfo.
     * Falls back to the OS MXBean name and arch system property.
     */
    public static String detectCpuName() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        // Try platform-specific detection first
        try {
            if (osName.contains("win")) {
                return detectCpuNameWindows();
            } else if (osName.contains("linux")) {
                return detectCpuNameLinux();
            } else if (osName.contains("mac")) {
                return detectCpuNameMac();
            }
        } catch (Exception e) {
            LOGGER.debug("Platform-specific CPU detection failed", e);
        }

        // Fallback: use OperatingSystemMXBean and system properties
        try {
            OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();
            String arch = osMxBean.getArch();
            String name = osMxBean.getName();
            int processors = Runtime.getRuntime().availableProcessors();
            return String.format("%s (%s, %d logical processors)", name, arch, processors);
        } catch (Exception e) {
            LOGGER.warn("Failed to detect CPU name", e);
            return "Unknown CPU";
        }
    }

    private static String detectCpuNameWindows() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("wmic", "cpu", "get", "Name", "/format:list");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Name=")) {
                    String cpuName = line.substring(5).trim();
                    if (!cpuName.isEmpty()) {
                        return cpuName;
                    }
                }
            }
        } finally {
            // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        throw new RuntimeException("WMIC did not return CPU name");
    }

    private static String detectCpuNameLinux() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("cat", "/proc/cpuinfo");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("model name")) {
                    int colonIdx = line.indexOf(':');
                    if (colonIdx >= 0) {
                        String cpuName = line.substring(colonIdx + 1).trim();
                        if (!cpuName.isEmpty()) {
                            return cpuName;
                        }
                    }
                }
            }
        } finally {
            // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        throw new RuntimeException("/proc/cpuinfo did not contain model name");
    }

    private static String detectCpuNameMac() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                return line.trim();
            }
        } finally {
            // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        throw new RuntimeException("sysctl did not return CPU brand string");
    }

    /**
     * Returns the number of logical processors (cores x threads) available to the JVM.
     */
    public static int detectCoreCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    /**
     * Attempts to detect the number of physical cores.
     * Falls back to logical processor count if detection fails.
     */
    public static int detectPhysicalCoreCount() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        try {
            if (osName.contains("win")) {
                return detectPhysicalCoresWindows();
            } else if (osName.contains("linux")) {
                return detectPhysicalCoresLinux();
            } else if (osName.contains("mac")) {
                return detectPhysicalCoresMac();
            }
        } catch (Exception e) {
            LOGGER.debug("Physical core detection failed, falling back to logical count", e);
        }

        // Fallback: assume half the logical processors (common for HT/SMT)
        int logical = detectCoreCount();
        return Math.max(1, logical / 2);
    }

    private static int detectPhysicalCoresWindows() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("wmic", "cpu", "get", "NumberOfCores", "/format:list");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("NumberOfCores=")) {
                    return Integer.parseInt(line.substring(14).trim());
                }
            }
        } finally {
            // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        throw new RuntimeException("WMIC did not return core count");
    }

    private static int detectPhysicalCoresLinux() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("nproc", "--all");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null) {
                return Integer.parseInt(line.trim());
            }
        } finally {
            // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        throw new RuntimeException("nproc did not return core count");
    }

    private static int detectPhysicalCoresMac() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sysctl", "-n", "hw.physicalcpu");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line = reader.readLine();
            if (line != null) {
                return Integer.parseInt(line.trim());
            }
        } finally {
            // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
        throw new RuntimeException("sysctl did not return physical CPU count");
    }
}
