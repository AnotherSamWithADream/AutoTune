package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 25: FOV Phase.
 * Tests field of view values of 30, 50, 70, 90, and 110 degrees.
 * Records 300 frames at each FOV setting. Higher FOV values cause more geometry
 * to be visible on screen, which can affect rendering performance through
 * increased vertex processing and fill rate demands.
 */
public class FOVPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(FOVPhase.class);

    private static final int[] FOV_VALUES = {30, 50, 70, 90, 110};

    private int originalFov;
    // [CODE-REVIEW-FIX] M-003: Use a boolean guard to save originals on first call,
    // regardless of which sub-test runs first (order-independent).
    private boolean savedOriginals = false;

    @Override
    public String getPhaseId() {
        return "phase_25_fov";
    }

    @Override
    public String getPhaseName() {
        return "Field of View";
    }

    @Override
    public int getPhaseNumber() {
        return 25;
    }

    @Override
    public List<String> getSubTestLabels() {
        List<String> labels = new ArrayList<>();
        for (int fov : FOV_VALUES) {
            labels.add("fov_" + fov);
        }
        return labels;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        int fov = parseFov(subTestLabel);
        if (fov < 0) {
            LOGGER.warn("Could not parse FOV from label: {}", subTestLabel);
            return;
        }

        if (!savedOriginals) {
            originalFov = adapter.getFov();
            savedOriginals = true;
            LOGGER.info("Saving original FOV: {}", originalFov);
        }

        LOGGER.info("Setting FOV to {}", fov);
        adapter.setFov(fov);
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring FOV to {}", originalFov);
        adapter.setFov(originalFov);
        // [CODE-REVIEW-FIX] Reset so a second benchmark run re-captures current values
        savedOriginals = false;
    }

    private int parseFov(String label) {
        if (label.startsWith("fov_")) {
            try {
                return Integer.parseInt(label.substring(4));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
