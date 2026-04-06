package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.benchmark.FrameTimeSampler;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.benchmark.VRAMMonitor;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 15: VRAM Pressure
 * Tests render distances from 8 to 32 in steps of 2 while monitoring VRAM
 * usage via VRAMMonitor. Records 300 frames at each step along with VRAM
 * readings before and after each sub-test.
 *
 * This phase maps the relationship between render distance and VRAM consumption,
 * identifying the point at which VRAM becomes a bottleneck (visible as sudden
 * FPS drops or increased frame time variance when available VRAM approaches zero).
 */
public class VRAMPressurePhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(VRAMPressurePhase.class);
    private static final String PHASE_ID = "phase_15_vram_pressure";
    private static final String PHASE_NAME = "VRAM Pressure";
    private static final int FRAMES_PER_STEP = BenchmarkConstants.FRAMES_SHORT;

    private static final int RD_START = 8;
    private static final int RD_END = 32;
    private static final int RD_STEP = 2;

    /** Number of render distance steps: (32-8)/2 + 1 = 13 steps. */
    private static final int NUM_STEPS = ((RD_END - RD_START) / RD_STEP) + 1;

    @Override
    public String getId() {
        return PHASE_ID;
    }

    @Override
    public String getName() {
        return PHASE_NAME;
    }

    @Override
    public String getDescription() {
        return "Tests RD 8-32 (step 2) while monitoring VRAM to identify memory pressure thresholds.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_STEP * NUM_STEPS;
    }

    @Override
    public PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                               FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(PHASE_ID, PHASE_NAME, System.currentTimeMillis());

        // Initialize VRAM monitor (safe — Style-B phases run on the render thread)
        VRAMMonitor vramMonitor;
        try {
            vramMonitor = new VRAMMonitor();
        } catch (Exception e) {
            LOGGER.warn("Failed to initialize VRAM monitor, proceeding without VRAM data", e);
            vramMonitor = null;
        }

        boolean vramSupported = vramMonitor != null && vramMonitor.isSupported();
        if (!vramSupported) {
            LOGGER.info("VRAM monitoring not supported on this GPU; frame times will still be recorded");
        }

        // Save original settings
        int originalRd = adapter.getRenderDistance();
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();

        // [CODE-REVIEW-FIX] Moved vramNotes declaration before try block so it's accessible in catch
        StringBuilder vramNotes = new StringBuilder();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;
            if (vramSupported) {
                vramNotes.append("VRAM vendor: ").append(vramMonitor.getVendorName());
                int totalMb = vramMonitor.getTotalVramMb();
                if (totalMb > 0) {
                    vramNotes.append(", total: ").append(totalMb).append(" MB");
                }
                vramNotes.append("\n");
            }

            for (int rd = RD_START; rd <= RD_END; rd += RD_STEP) {
                String label = "vram_rd_" + rd;
                LOGGER.info("Testing VRAM pressure at RD {} ({} frames)", rd, FRAMES_PER_STEP);

                // Snapshot VRAM before applying new render distance
                int vramUsedBefore = vramSupported ? vramMonitor.getUsedVramMb() : -1;
                int vramAvailBefore = vramSupported ? vramMonitor.getCurrentAvailableVramMb() : -1;

                // Apply render distance
                adapter.setRenderDistance(rd);

                // Extended settle to let chunks load and VRAM allocations stabilize
                for (int i = 0; i < BenchmarkConstants.SETTLE_FRAMES * 2; i++) {
                    waitOneFrame(client);
                }

                // Warmup
                for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                    waitOneFrame(client);
                }

                // Snapshot VRAM after settle
                int vramUsedAfter = vramSupported ? vramMonitor.getUsedVramMb() : -1;
                int vramAvailAfter = vramSupported ? vramMonitor.getCurrentAvailableVramMb() : -1;

                // Measurement
                sampler.reset();
                long frameStart;

                // Also sample VRAM periodically during measurement
                int vramPeakUsed = vramUsedAfter;
                int vramMinAvail = vramAvailAfter;

                for (int i = 0; i < FRAMES_PER_STEP; i++) {
                    frameStart = System.nanoTime();
                    waitOneFrame(client);
                    long frameTime = System.nanoTime() - frameStart;
                    sampler.record(frameTime);

                    // Sample VRAM every 60 frames to track peak usage
                    if (vramSupported && i % 60 == 0) {
                        int currentUsed = vramMonitor.getUsedVramMb();
                        int currentAvail = vramMonitor.getCurrentAvailableVramMb();
                        if (currentUsed > vramPeakUsed) {
                            vramPeakUsed = currentUsed;
                        }
                        if (currentAvail >= 0 && (vramMinAvail < 0 || currentAvail < vramMinAvail)) {
                            vramMinAvail = currentAvail;
                        }
                    }
                    // [CODE-REVIEW-FIX] H-023: Move progress callback outside vramSupported conditional
                    // so progress is always reported regardless of VRAM monitoring support.
                    // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                    if (i % 60 == 0 || i == FRAMES_PER_STEP - 1) {
                        callback.onProgress(label, completedFrames + i, totalFrames);
                    }
                }

                long[] samples = sampler.getSamplesSnapshot();
                FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                result.addMeasurement(label, stats);

                // Log VRAM data
                if (vramSupported) {
                    vramNotes.append(String.format("RD %d: used=%dMB->%dMB, peak=%dMB, avail=%dMB->%dMB, min_avail=%dMB\n",
                            rd, vramUsedBefore, vramUsedAfter, vramPeakUsed,
                            vramAvailBefore, vramAvailAfter, vramMinAvail));

                    LOGGER.info("  RD {}: avg={} FPS, VRAM used={}->{}MB (peak {}MB), avail={}MB",
                            rd,
                            String.format("%.1f", stats.avgFps()),
                            vramUsedBefore, vramUsedAfter, vramPeakUsed, vramMinAvail);
                } else {
                    LOGGER.info("  RD {}: avg={} FPS, 1% low={} FPS", rd,
                            String.format("%.1f", stats.avgFps()),
                            String.format("%.1f", stats.p1LowFps()));
                }

                completedFrames += FRAMES_PER_STEP;
            }

            if (!vramNotes.isEmpty()) {
                result.setNotes(vramNotes.toString().trim());
            }

        } catch (Exception e) {
            LOGGER.error("Error during VRAM pressure phase", e);
            // [CODE-REVIEW-FIX] M-014: Prepend error to vramNotes instead of overwriting collected VRAM data
            // [CODE-REVIEW-FIX] L-002: Use e.toString() instead of e.getMessage() to avoid null for NPE
            String existingNotes = vramNotes.toString().trim();
            result.setNotes("Error: " + e + (existingNotes.isEmpty() ? "" : "\n" + existingNotes));
        } finally {
            adapter.setRenderDistance(originalRd);
            adapter.setMaxFps(originalMaxFps);
            adapter.setVsync(originalVsync);
        }

        result.setEndTime(System.currentTimeMillis());
        callback.onProgress("complete", getEstimatedFrames(), getEstimatedFrames());
        return result;
    }

    private void waitOneFrame(MinecraftClient client) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
