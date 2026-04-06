package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 27: Entity Distance Phase.
 * Tests entity distance scaling at 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, and 5.0.
 * Records 300 frames at each multiplier. Entity distance scaling controls how
 * far from the player entities are rendered. Higher values increase the number
 * of visible entities (mobs, players, item frames, etc.), affecting both the
 * entity tick and render cost.
 */
public class EntityDistancePhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityDistancePhase.class);

    private static final float[] DISTANCE_VALUES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 5.0f};
    private static final String[] DISTANCE_LABELS = {
            "entity_dist_050", "entity_dist_075", "entity_dist_100",
            "entity_dist_125", "entity_dist_150", "entity_dist_200", "entity_dist_500"
    };

    private float originalEntityDistance;
    // [CODE-REVIEW-FIX] M-003: Use a boolean guard to save originals on first call,
    // regardless of which sub-test runs first (order-independent).
    private boolean savedOriginals = false;

    @Override
    public String getPhaseId() {
        return "phase_27_entity_distance";
    }

    @Override
    public String getPhaseName() {
        return "Entity Distance Scaling";
    }

    @Override
    public int getPhaseNumber() {
        return 27;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of(DISTANCE_LABELS);
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        int idx = findLabelIndex(subTestLabel);
        if (idx < 0) {
            LOGGER.warn("Unknown entity distance sub-test: {}", subTestLabel);
            return;
        }

        if (!savedOriginals) {
            originalEntityDistance = adapter.getEntityDistanceScaling();
            savedOriginals = true;
            LOGGER.info("Saving original entity distance scaling: {}", originalEntityDistance);
        }

        float scaling = DISTANCE_VALUES[idx];
        LOGGER.info("Setting entity distance scaling to {}", scaling);
        adapter.setEntityDistanceScaling(scaling);
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring entity distance scaling to {}", originalEntityDistance);
        adapter.setEntityDistanceScaling(originalEntityDistance);
        // [CODE-REVIEW-FIX] Reset so a second benchmark run re-captures current values
        savedOriginals = false;
    }

    private int findLabelIndex(String label) {
        for (int i = 0; i < DISTANCE_LABELS.length; i++) {
            if (DISTANCE_LABELS[i].equals(label)) {
                return i;
            }
        }
        return -1;
    }
}
