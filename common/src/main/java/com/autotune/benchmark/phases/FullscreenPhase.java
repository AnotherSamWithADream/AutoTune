package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 17: Fullscreen Phase.
 * Tests performance in windowed mode versus fullscreen mode, recording 300 frames each.
 * Fullscreen can affect GPU scheduling, V-Blank timing, and compositor overhead.
 */
public class FullscreenPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(FullscreenPhase.class);

    private boolean originalFullscreen;
    // [CODE-REVIEW-FIX] M-003: Use a boolean guard to save originals on first call,
    // regardless of which sub-test runs first (order-independent).
    private boolean savedOriginals = false;

    @Override
    public String getPhaseId() {
        return "phase_17_fullscreen";
    }

    @Override
    public String getPhaseName() {
        return "Fullscreen Toggle";
    }

    @Override
    public int getPhaseNumber() {
        return 17;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of("windowed", "fullscreen");
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        if (!savedOriginals) {
            originalFullscreen = adapter.getFullscreen();
            savedOriginals = true;
            LOGGER.info("Saving original fullscreen={}", originalFullscreen);
        }

        if (subTestLabel.equals("windowed")) {
            LOGGER.info("Testing windowed mode");
            adapter.setFullscreen(false);
        } else {
            LOGGER.info("Testing fullscreen mode");
            adapter.setFullscreen(true);
        }
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring fullscreen to {}", originalFullscreen);
        adapter.setFullscreen(originalFullscreen);
        // [CODE-REVIEW-FIX] Reset so a second benchmark run re-captures current values
        savedOriginals = false;
    }
}
