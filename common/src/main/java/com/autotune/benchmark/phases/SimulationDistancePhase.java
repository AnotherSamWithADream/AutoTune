package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 24: Simulation Distance Phase.
 * Tests simulation distances of 5, 8, 10, 12, 16, 20, and 24 chunks.
 * Records 300 frames at each distance. Simulation distance controls how far
 * from the player the game will tick entities and block updates, affecting
 * CPU load from game logic processing.
 */
public class SimulationDistancePhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulationDistancePhase.class);

    private static final int[] SIM_DISTANCES = {5, 8, 10, 12, 16, 20, 24};

    private int originalSimDistance;
    // [CODE-REVIEW-FIX] M-003: Use a boolean guard to save originals on first call,
    // regardless of which sub-test runs first (order-independent).
    private boolean savedOriginals = false;

    @Override
    public String getPhaseId() {
        return "phase_24_simulation_distance";
    }

    @Override
    public String getPhaseName() {
        return "Simulation Distance Ladder";
    }

    @Override
    public int getPhaseNumber() {
        return 24;
    }

    @Override
    public List<String> getSubTestLabels() {
        List<String> labels = new ArrayList<>();
        for (int dist : SIM_DISTANCES) {
            labels.add("sim_" + dist);
        }
        return labels;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        int distance = parseDistance(subTestLabel);
        if (distance < 0) {
            LOGGER.warn("Could not parse simulation distance from label: {}", subTestLabel);
            return;
        }

        if (!savedOriginals) {
            originalSimDistance = adapter.getSimulationDistance();
            savedOriginals = true;
            LOGGER.info("Saving original simulation distance: {}", originalSimDistance);
        }

        LOGGER.info("Setting simulation distance to {}", distance);
        adapter.setSimulationDistance(distance);
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Restoring simulation distance to {}", originalSimDistance);
        adapter.setSimulationDistance(originalSimDistance);
        // [CODE-REVIEW-FIX] Reset so a second benchmark run re-captures current values
        savedOriginals = false;
    }

    private int parseDistance(String label) {
        if (label.startsWith("sim_")) {
            try {
                return Integer.parseInt(label.substring(4));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }
}
