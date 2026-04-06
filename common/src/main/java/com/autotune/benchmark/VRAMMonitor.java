package com.autotune.benchmark;

import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors VRAM usage using vendor-specific OpenGL extensions.
 * Supports NVIDIA (GL_NVX_gpu_memory_info) and AMD (GL_ATI_meminfo).
 */
public class VRAMMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(VRAMMonitor.class);

    // NVIDIA NVX extension constants
    private static final int GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX = 0x9048;
    private static final int GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;

    // AMD ATI extension constants
    private static final int GL_TEXTURE_FREE_MEMORY_ATI = 0x87FC;

    private final VendorType vendorType;
    private final int totalVramKb;

    private enum VendorType {
        NVIDIA,
        AMD,
        UNSUPPORTED
    }

    public VRAMMonitor() {
        VendorType detected = VendorType.UNSUPPORTED;
        int detectedTotalKb = 0;

        try {
            String vendor = GL11.glGetString(GL11.GL_VENDOR);
            if (vendor == null) vendor = "";
            String extensions = "";
            try {
                // Collect extensions to check for memory query support
                int numExt = GL11.glGetInteger(0x821D); // GL_NUM_EXTENSIONS
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < numExt; i++) {
                    String ext = org.lwjgl.opengl.GL30.glGetStringi(GL11.GL_EXTENSIONS, i);
                    if (ext != null) {
                        sb.append(ext).append(' ');
                    }
                }
                extensions = sb.toString();
            } catch (Exception e) {
                LOGGER.debug("Could not enumerate GL extensions for VRAM detection", e);
            }

            if (extensions.contains("GL_NVX_gpu_memory_info")) {
                detected = VendorType.NVIDIA;
                detectedTotalKb = GL11.glGetInteger(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX);
                if (detectedTotalKb <= 0) {
                    detected = VendorType.UNSUPPORTED;
                    detectedTotalKb = 0;
                }
                LOGGER.debug("VRAM monitor: NVIDIA detected, total={}KB", detectedTotalKb);
            } else if (extensions.contains("GL_ATI_meminfo")) {
                detected = VendorType.AMD;
                // ATI meminfo returns 4 values: [total free, largest block, total aux, largest aux block]
                // We query it once to get the initial free amount as an approximation of total
                int[] params = new int[4];
                GL11.glGetIntegerv(GL_TEXTURE_FREE_MEMORY_ATI, params);
                detectedTotalKb = params[0];
                if (detectedTotalKb <= 0) {
                    detected = VendorType.UNSUPPORTED;
                    detectedTotalKb = 0;
                }
                LOGGER.debug("VRAM monitor: AMD detected, approx total={}KB", detectedTotalKb);
            } else {
                LOGGER.debug("VRAM monitor: No supported extension found (vendor={})", vendor);
            }
        } catch (Exception e) {
            LOGGER.warn("VRAM monitor initialization failed", e);
        }

        this.vendorType = detected;
        this.totalVramKb = detectedTotalKb;
    }

    /**
     * Returns whether VRAM monitoring is supported on this system.
     */
    public boolean isSupported() {
        return vendorType != VendorType.UNSUPPORTED;
    }

    /**
     * Returns the currently available (free) VRAM in megabytes.
     * Returns -1 if not supported.
     */
    public int getCurrentAvailableVramMb() {
        if (!isSupported()) return -1;

        try {
            switch (vendorType) {
                case NVIDIA: {
                    int availableKb = GL11.glGetInteger(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX);
                    return availableKb > 0 ? availableKb / 1024 : -1;
                }
                case AMD: {
                    int[] params = new int[4];
                    GL11.glGetIntegerv(GL_TEXTURE_FREE_MEMORY_ATI, params);
                    return params[0] > 0 ? params[0] / 1024 : -1;
                }
                default:
                    return -1;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to query available VRAM", e);
            return -1;
        }
    }

    /**
     * Returns the total VRAM in megabytes as detected at initialization.
     * Returns -1 if not supported.
     */
    public int getTotalVramMb() {
        if (!isSupported()) return -1;
        return totalVramKb / 1024;
    }

    /**
     * Returns the estimated used VRAM in megabytes (total - available).
     * Returns -1 if not supported.
     */
    public int getUsedVramMb() {
        if (!isSupported()) return -1;
        int available = getCurrentAvailableVramMb();
        int total = getTotalVramMb();
        if (available < 0 || total < 0) return -1;
        return total - available;
    }

    /**
     * Returns the vendor type detected for this monitor.
     */
    public String getVendorName() {
        return vendorType.name();
    }
}
