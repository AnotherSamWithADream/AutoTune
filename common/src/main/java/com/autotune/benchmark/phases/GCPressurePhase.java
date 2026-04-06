package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.benchmark.GCMonitor;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Phase 16: GC Pressure Phase.
 * Records 3000 frames while monitoring garbage collection activity via GCMonitor.
 * Measures GC pause count and total pause time to quantify GC overhead on frame delivery.
 */
public class GCPressurePhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(GCPressurePhase.class);

    private final GCMonitor gcMonitor = new GCMonitor();

    @Override
    public String getPhaseId() {
        return "phase_16_gc_pressure";
    }

    @Override
    public String getPhaseName() {
        return "GC Pressure";
    }

    @Override
    public int getPhaseNumber() {
        return 16;
    }

    @Override
    public List<String> getSubTestLabels() {
        return List.of("gc_pressure");
    }

    @Override
    public int getFramesPerSubTest() {
        return BenchmarkConstants.FRAMES_STRESS;
    }

    @Override
    public void setupSubTest(String subTestLabel, MinecraftClient client, PlatformAdapter adapter) {
        LOGGER.info("Starting GC pressure measurement: recording {} frames", BenchmarkConstants.FRAMES_STRESS);
        gcMonitor.reset();
    }

    @Override
    public void teardown(MinecraftClient client, PlatformAdapter adapter) {
        GCMonitor.GcSnapshot snapshot = gcMonitor.snapshot();
        // [CODE-REVIEW-FIX] SLF4J does not support {:.2f} format specifiers; use String.format
        LOGGER.info("GC Pressure results: pauses={}, totalPauseTime={}ms, avgPause={}ms",
                snapshot.pauseCount(), snapshot.pauseTimeMs(), String.format("%.2f", snapshot.averagePauseMs()));
    }
}
