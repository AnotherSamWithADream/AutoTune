package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 18: VSync Phase (quickMode).
 * Tests VSync on versus off, recording 300 frames for each configuration.
 * VSync caps frame rate to the monitor refresh rate and prevents tearing,
 * but may introduce input lag or lower average FPS on slower hardware.
 */
public class VSyncPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(VSyncPhase.class);

    private boolean originalVsync;
    private int originalMaxFps;
    // [CODE-REVIEW-FIX] M-003: Use a boolean guard to save originals on first call,
    // regardless of which sub-test runs first (order-independent).
    private boolean savedOriginals = false;

    @Override
    public String getPhaseId() {
        return "phase_18_vsync";
    }

    @Override
    public String getPhaseName() {
        return "VSync Toggle";
    }

    @Override
    public int getPhaseNumber() {
        return 18;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of("vsync_off", "vsync_on");
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        if (!savedOriginals) {
            originalVsync = adapter.getVsync();
            originalMaxFps = adapter.getMaxFps();
            savedOriginals = true;
            LOGGER.info("Saving original vsync={}, maxFps={}", originalVsync, originalMaxFps);
        }

        if (subTestLabel.equals("vsync_off")) {
            LOGGER.info("Testing VSync OFF");
            adapter.setVsync(false);
            adapter.setMaxFps(260);
        } else {
            LOGGER.info("Testing VSync ON");
            adapter.setVsync(true);
            adapter.setMaxFps(260);
        }
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring VSync to {}, maxFps to {}", originalVsync, originalMaxFps);
        adapter.setVsync(originalVsync);
        adapter.setMaxFps(originalMaxFps);
        // [CODE-REVIEW-FIX] Reset so a second benchmark run re-captures current values
        savedOriginals = false;
    }
}
