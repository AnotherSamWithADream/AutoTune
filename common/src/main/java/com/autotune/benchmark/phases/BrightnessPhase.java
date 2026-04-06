package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 28: Brightness (Gamma) Phase.
 * Tests gamma values of 0.0, 0.25, 0.5, 0.75, and 1.0.
 * Records 300 frames at each brightness level. While gamma is typically a
 * lightweight setting, extreme values can affect how the lighting engine
 * composites the final image, and some driver implementations may handle
 * gamma correction differently in the shader pipeline.
 */
public class BrightnessPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(BrightnessPhase.class);

    private static final double[] GAMMA_VALUES = {0.0, 0.25, 0.5, 0.75, 1.0};
    private static final String[] GAMMA_LABELS = {
            "gamma_000", "gamma_025", "gamma_050", "gamma_075", "gamma_100"
    };

    private double originalGamma;
    // [CODE-REVIEW-FIX] M-003: Use a boolean guard to save originals on first call,
    // regardless of which sub-test runs first (order-independent).
    private boolean savedOriginals = false;

    @Override
    public String getPhaseId() {
        return "phase_28_brightness";
    }

    @Override
    public String getPhaseName() {
        return "Brightness (Gamma)";
    }

    @Override
    public int getPhaseNumber() {
        return 28;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of(GAMMA_LABELS);
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        int idx = findLabelIndex(subTestLabel);
        if (idx < 0) {
            LOGGER.warn("Unknown brightness sub-test: {}", subTestLabel);
            return;
        }

        if (!savedOriginals) {
            originalGamma = adapter.getGamma();
            savedOriginals = true;
            LOGGER.info("Saving original gamma: {}", originalGamma);
        }

        double gamma = GAMMA_VALUES[idx];
        LOGGER.info("Setting gamma (brightness) to {}", gamma);
        adapter.setGamma(gamma);
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring gamma to {}", originalGamma);
        adapter.setGamma(originalGamma);
        // [CODE-REVIEW-FIX] Reset so a second benchmark run re-captures current values
        savedOriginals = false;
    }

    private int findLabelIndex(String label) {
        for (int i = 0; i < GAMMA_LABELS.length; i++) {
            if (GAMMA_LABELS[i].equals(label)) {
                return i;
            }
        }
        return -1;
    }
}
