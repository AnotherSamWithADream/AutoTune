package com.autotune.benchmark.hardware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import javax.management.MBeanServer;
import javax.management.ObjectName;

public final class ThermalDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ThermalDetector.class);

    /**
     * Sentinel value indicating temperature is unavailable.
     */
    public static final double UNAVAILABLE = -1.0;

    private ThermalDetector() {}

    /**
     * Attempts to detect CPU temperature.
     * On Windows, tries WMI (MSAcpi_ThermalZoneTemperature). Usually requires admin privileges.
     * On Linux, tries reading from /sys/class/thermal.
     * Returns -1.0 if unavailable.
     */
    public static double detectCpuTemperature() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("win")) {
            return detectCpuTempWindows();
        } else if (osName.contains("linux")) {
            return detectCpuTempLinux();
        }

        return UNAVAILABLE;
    }

    private static double detectCpuTempWindows() {
        // Method 1: Try JMX MBean (unlikely to work in standard JRE)
        double jmxTemp = detectCpuTempJmx();
        if (jmxTemp > 0) {
            return jmxTemp;
        }

        // Method 2: Try WMI via PowerShell (requires admin privileges)
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-Command",
                "Get-WmiObject MSAcpi_ThermalZoneTemperature -Namespace root/wmi 2>$null | " +
                "Select-Object -First 1 -ExpandProperty CurrentTemperature"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    // WMI returns temperature in tenths of Kelvin
                    double tenthsKelvin = Double.parseDouble(line.trim());
                    double celsius = (tenthsKelvin / 10.0) - 273.15;
                    if (celsius > 0 && celsius < 150) {
                        return celsius;
                    }
                }
            } finally {
                // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("WMI CPU temperature detection failed (likely requires admin)", e);
        }

        return UNAVAILABLE;
    }

    private static double detectCpuTempJmx() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName("com.sun.management:type=DiagnosticCommand");
            // This is a long shot - most JVMs don't expose temperature
            Object temp = mbs.getAttribute(name, "Temperature");
            if (temp instanceof Number num) {
                double celsius = num.doubleValue();
                if (celsius > 0 && celsius < 150) {
                    return celsius;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("JMX CPU temperature not available", e);
        }
        return UNAVAILABLE;
    }

    private static double detectCpuTempLinux() {
        // Try /sys/class/thermal/thermal_zone0/temp
        try {
            ProcessBuilder pb = new ProcessBuilder("cat", "/sys/class/thermal/thermal_zone0/temp");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    // Linux reports in millidegrees Celsius
                    long millidegrees = Long.parseLong(line.trim());
                    double celsius = millidegrees / 1000.0;
                    if (celsius > 0 && celsius < 150) {
                        return celsius;
                    }
                }
            } finally {
                // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Linux thermal zone reading failed", e);
        }
        return UNAVAILABLE;
    }

    /**
     * Attempts to detect GPU temperature.
     * This is generally not possible from Java without native tools.
     * Returns -1.0 if unavailable.
     */
    public static double detectGpuTemperature() {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("win")) {
            return detectGpuTempWindowsNvidiaSmi();
        } else if (osName.contains("linux")) {
            return detectGpuTempLinuxNvidiaSmi();
        }

        return UNAVAILABLE;
    }

    private static double detectGpuTempWindowsNvidiaSmi() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "nvidia-smi", "--query-gpu=temperature.gpu", "--format=csv,noheader,nounits"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    double temp = Double.parseDouble(line.trim());
                    if (temp > 0 && temp < 150) {
                        return temp;
                    }
                }
            } finally {
                // [CODE-REVIEW-FIX] Enforce timeout and destroy process to prevent indefinite hangs
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("nvidia-smi GPU temperature detection failed", e);
        }
        return UNAVAILABLE;
    }

    private static double detectGpuTempLinuxNvidiaSmi() {
        // Same command works on Linux if nvidia-smi is installed
        return detectGpuTempWindowsNvidiaSmi();
    }

    /**
     * Simple heuristic to detect thermal throttling based on temperature readings.
     * If CPU temp > 90C or GPU temp > 85C, consider it likely throttling.
     */
    public static boolean detectThermalThrottling(double cpuTemp, double gpuTemp) {
        return cpuTemp > 90.0 || gpuTemp > 85.0;
    }
}
