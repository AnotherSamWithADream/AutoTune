package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 26: GUI Scale Phase.
 * Tests GUI scale values of 1, 2, 3, 4, and 0 (auto).
 * Records 300 frames at each scale. GUI scale affects the resolution at which
 * HUD elements, inventory screens, and text are rendered. Different scales
 * may impact the GUI rendering pass overhead differently depending on
 * the GPU's fill rate and text rendering performance.
 */
public class GUIScalePhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GUIScalePhase.class);

    private static final int[] GUI_SCALES = {1, 2, 3, 4, 0};
    private static final String[] GUI_LABELS = {"gui_1", "gui_2", "gui_3", "gui_4", "gui_auto"};

    private int originalGuiScale;
    // [CODE-REVIEW-FIX] M-003: Use a boolean guard to save originals on first call,
    // regardless of which sub-test runs first (order-independent).
    private boolean savedOriginals = false;

    @Override
    public String getPhaseId() {
        return "phase_26_gui_scale";
    }

    @Override
    public String getPhaseName() {
        return "GUI Scale";
    }

    @Override
    public int getPhaseNumber() {
        return 26;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of(GUI_LABELS);
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        int idx = findLabelIndex(subTestLabel);
        if (idx < 0) {
            LOGGER.warn("Unknown GUI scale sub-test: {}", subTestLabel);
            return;
        }

        if (!savedOriginals) {
            originalGuiScale = adapter.getGuiScale();
            savedOriginals = true;
            LOGGER.info("Saving original GUI scale: {}", originalGuiScale);
        }

        int scale = GUI_SCALES[idx];
        LOGGER.info("Setting GUI scale to {} ({})", scale, scale == 0 ? "auto" : scale);
        adapter.setGuiScale(scale);
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring GUI scale to {}", originalGuiScale);
        adapter.setGuiScale(originalGuiScale);
        // [CODE-REVIEW-FIX] Reset so a second benchmark run re-captures current values
        savedOriginals = false;
    }

    private int findLabelIndex(String label) {
        for (int i = 0; i < GUI_LABELS.length; i++) {
            if (GUI_LABELS[i].equals(label)) {
                return i;
            }
        }
        return -1;
    }
}
