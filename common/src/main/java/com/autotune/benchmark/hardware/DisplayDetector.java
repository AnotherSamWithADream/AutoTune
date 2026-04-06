package com.autotune.benchmark.hardware;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DisplayDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisplayDetector.class);

    private DisplayDetector() {}

    /**
     * Detects the current framebuffer width from the Minecraft window.
     */
    public static int detectWidth(MinecraftClient client) {
        try {
            return client.getWindow().getFramebufferWidth();
        } catch (Exception e) {
            LOGGER.warn("Failed to detect display width", e);
            return 0;
        }
    }

    /**
     * Detects the current framebuffer height from the Minecraft window.
     */
    public static int detectHeight(MinecraftClient client) {
        try {
            return client.getWindow().getFramebufferHeight();
        } catch (Exception e) {
            LOGGER.warn("Failed to detect display height", e);
            return 0;
        }
    }

    /**
     * Returns a formatted resolution string like "1920x1080".
     */
    public static String detectResolution(MinecraftClient client) {
        int w = detectWidth(client);
        int h = detectHeight(client);
        if (w > 0 && h > 0) {
            return w + "x" + h;
        }
        return "Unknown";
    }

    /**
     * Detects the refresh rate of the primary monitor using GLFW.
     * Returns the refresh rate in Hz, or 60 as a fallback.
     */
    public static int detectRefreshRate(MinecraftClient client) {
        try {
            long windowHandle = client.getWindow().getHandle();
            long monitor = GLFW.glfwGetWindowMonitor(windowHandle);

            // If the window is not fullscreen, get the primary monitor
            if (monitor == 0L) {
                monitor = GLFW.glfwGetPrimaryMonitor();
            }

            if (monitor != 0L) {
                GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
                if (vidMode != null) {
                    int refreshRate = vidMode.refreshRate();
                    if (refreshRate > 0) {
                        return refreshRate;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to detect monitor refresh rate", e);
        }
        return 60;
    }

}
