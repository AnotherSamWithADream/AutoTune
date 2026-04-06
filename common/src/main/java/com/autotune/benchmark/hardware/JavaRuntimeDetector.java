package com.autotune.benchmark.hardware;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JavaRuntimeDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaRuntimeDetector.class);

    private JavaRuntimeDetector() {}

    /**
     * Returns the Java version string (e.g. "17.0.2").
     */
    public static String detectJavaVersion() {
        try {
            String version = System.getProperty("java.version");
            return version != null ? version : "Unknown";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect Java version", e);
            return "Unknown";
        }
    }

    /**
     * Returns the Java vendor string (e.g. "Eclipse Adoptium").
     */
    public static String detectJavaVendor() {
        try {
            String vendor = System.getProperty("java.vendor");
            return vendor != null ? vendor : "Unknown";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect Java vendor", e);
            return "Unknown";
        }
    }

    /**
     * Returns the OS name (e.g. "Windows 11").
     */
    public static String detectOsName() {
        try {
            String name = System.getProperty("os.name");
            return name != null ? name : "Unknown";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect OS name", e);
            return "Unknown";
        }
    }

    /**
     * Returns the OS architecture (e.g. "amd64").
     */
    public static String detectOsArch() {
        try {
            String arch = System.getProperty("os.arch");
            return arch != null ? arch : "Unknown";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect OS arch", e);
            return "Unknown";
        }
    }

}
