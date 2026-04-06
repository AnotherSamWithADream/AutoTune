package com.autotune.benchmark.hardware;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GPUDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(GPUDetector.class);

    /**
     * NV_X extension constant: GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX
     */
    private static final int GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX = 0x9048;

    /**
     * ATI extension constant: GL_TEXTURE_FREE_MEMORY_ATI
     */
    private static final int GL_TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    private GPUDetector() {}

    public static String detectGpuName() {
        try {
            String renderer = GL11.glGetString(GL11.GL_RENDERER);
            return renderer != null ? renderer.trim() : "Unknown GPU";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect GPU name", e);
            return "Unknown GPU";
        }
    }

    public static String detectGpuVendor() {
        try {
            String vendor = GL11.glGetString(GL11.GL_VENDOR);
            return vendor != null ? vendor.trim() : "Unknown Vendor";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect GPU vendor", e);
            return "Unknown Vendor";
        }
    }

    public static String detectGpuDriver() {
        try {
            String version = GL11.glGetString(GL11.GL_VERSION);
            return version != null ? version.trim() : "Unknown Driver";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect GPU driver version", e);
            return "Unknown Driver";
        }
    }

    public static String detectGlVersion() {
        try {
            String version = GL11.glGetString(GL11.GL_VERSION);
            return version != null ? version.trim() : "Unknown";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect GL version", e);
            return "Unknown";
        }
    }

    public static String detectGlRenderer() {
        try {
            String renderer = GL11.glGetString(GL11.GL_RENDERER);
            return renderer != null ? renderer.trim() : "Unknown";
        } catch (Exception e) {
            LOGGER.warn("Failed to detect GL renderer", e);
            return "Unknown";
        }
    }

    /**
     * Attempts to detect VRAM in megabytes.
     * Uses NVX extension for NVIDIA, ATI extension for AMD, or falls back to the GPU database.
     */
    public static int detectVram(String glRenderer, GPUDatabase gpuDatabase) {
        // Try NVIDIA NVX extension
        int nvxVram = detectVramNvx();
        if (nvxVram > 0) {
            return nvxVram;
        }

        // Try AMD ATI extension
        int atiVram = detectVramAti();
        if (atiVram > 0) {
            return atiVram;
        }

        // Fallback to GPU database
        if (gpuDatabase != null && glRenderer != null) {
            return gpuDatabase.match(glRenderer)
                .map(GPUDatabase.GPUEntry::vramMb)
                .orElse(0);
        }

        return 0;
    }

    private static int detectVramNvx() {
        try {
            List<String> extensions = detectGlExtensions();
            if (extensions.contains("GL_NVX_gpu_memory_info")) {
                int totalKb = GL11.glGetInteger(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX);
                if (totalKb > 0) {
                    return totalKb / 1024;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NVX VRAM detection not available", e);
        }
        return -1;
    }

    private static int detectVramAti() {
        try {
            List<String> extensions = detectGlExtensions();
            if (extensions.contains("GL_ATI_meminfo")) {
                int[] params = new int[4];
                GL11.glGetIntegerv(GL_TEXTURE_FREE_MEMORY_ATI, params);
                int totalKb = params[0];
                if (totalKb > 0) {
                    return totalKb / 1024;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("ATI VRAM detection not available", e);
        }
        return -1;
    }

    /**
     * Enumerates all supported GL extensions.
     */
    public static List<String> detectGlExtensions() {
        try {
            int numExtensions = GL11.glGetInteger(GL30.GL_NUM_EXTENSIONS);
            if (numExtensions <= 0) {
                return Collections.emptyList();
            }
            List<String> extensions = new ArrayList<>(numExtensions);
            for (int i = 0; i < numExtensions; i++) {
                String ext = GL30.glGetStringi(GL11.GL_EXTENSIONS, i);
                if (ext != null) {
                    extensions.add(ext);
                }
            }
            return Collections.unmodifiableList(extensions);
        } catch (Exception e) {
            LOGGER.warn("Failed to enumerate GL extensions", e);
            return Collections.emptyList();
        }
    }

    /**
     * Normalizes a vendor string to a simple vendor name.
     */
    public static String normalizeVendor(String rawVendor) {
        if (rawVendor == null) return "Unknown";
        String lower = rawVendor.toLowerCase();
        if (lower.contains("nvidia")) return "NVIDIA";
        if (lower.contains("amd") || lower.contains("ati")) return "AMD";
        if (lower.contains("intel")) return "Intel";
        if (lower.contains("apple")) return "Apple";
        return rawVendor;
    }
}
