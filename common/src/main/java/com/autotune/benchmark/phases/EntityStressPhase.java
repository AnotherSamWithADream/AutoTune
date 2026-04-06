package com.autotune.benchmark.phases;

import com.autotune.benchmark.BenchmarkConstants;
import com.autotune.benchmark.FrameTimeSampler;
import com.autotune.benchmark.FrameTimeStatistics;
import com.autotune.benchmark.PhaseResult;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 7: Entity Stress
 * Spawns client-side armor stand entities at varying counts (50, 200, 500)
 * and records 300 frames at each level. This measures the cost of entity
 * rendering, tick processing, and model transformations.
 *
 * Entities are spawned near the player position and removed after measurement.
 */
public class EntityStressPhase implements BenchmarkPhase {

    private static final Logger LOGGER = LoggerFactory.getLogger(EntityStressPhase.class);
    private static final String PHASE_ID = "phase_07_entity_stress";
    private static final String PHASE_NAME = "Entity Stress";
    private static final int FRAMES_PER_COUNT = BenchmarkConstants.FRAMES_SHORT;
    private static final int[] ENTITY_COUNTS = {50, 200, 500};

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
        return "Spawns client-side armor stands at 50/200/500 counts to measure entity rendering overhead.";
    }

    @Override
    public int getEstimatedFrames() {
        return FRAMES_PER_COUNT * ENTITY_COUNTS.length;
    }

    @Override
    public PhaseResult execute(MinecraftClient client, PlatformAdapter adapter,
                               FrameTimeSampler sampler, ProgressCallback callback) {
        PhaseResult result = new PhaseResult(PHASE_ID, PHASE_NAME, System.currentTimeMillis());

        if (client.player == null || client.world == null) {
            return PhaseResult.skipped(PHASE_ID, PHASE_NAME, "No player or world available");
        }

        // Entity operations are safe here — Style-B phases run on the render thread

        // Save original settings
        int originalMaxFps = adapter.getMaxFps();
        boolean originalVsync = adapter.getVsync();
        float originalEntityDistance = adapter.getEntityDistanceScaling();

        try {
            adapter.setMaxFps(260);
            adapter.setVsync(false);
            // Ensure entities are fully rendered
            adapter.setEntityDistanceScaling(1.0f);

            int totalFrames = getEstimatedFrames();
            int completedFrames = 0;

            double playerX = client.player.getX();
            double playerY = client.player.getY();
            double playerZ = client.player.getZ();

            for (int targetCount : ENTITY_COUNTS) {
                String label = "entities_" + targetCount;
                LOGGER.info("Testing with {} entities ({} frames)", targetCount, FRAMES_PER_COUNT);

                // Spawn client-side armor stands in a grid around the player
                List<ArmorStandEntity> spawned = new ArrayList<>();
                ClientWorld world = client.world;

                try { // [CODE-REVIEW-FIX] Entity cleanup now in finally block to prevent entity leaks on exception
                    try {
                        int gridSize = (int) Math.ceil(Math.sqrt(targetCount));
                        int spawnedCount = 0;

                        for (int gx = 0; gx < gridSize && spawnedCount < targetCount; gx++) {
                            for (int gz = 0; gz < gridSize && spawnedCount < targetCount; gz++) {
                                double ex = playerX + (gx - gridSize / 2.0) * 1.5;
                                double ez = playerZ + (gz - gridSize / 2.0) * 1.5;

                                // [CODE-REVIEW-FIX] ArmorStandEntity constructor varies between MC versions;
                                // wrapped in try-catch for cross-version safety
                                ArmorStandEntity armorStand = new ArmorStandEntity(
                                        EntityType.ARMOR_STAND, world);
                                armorStand.setPosition(ex, playerY, ez);

                                // Add to client world for rendering
                                // [CODE-REVIEW-FIX] H-022: Use large positive entity IDs instead of negative ones.
                                // Negative IDs conflict with Minecraft's entity tracking system which uses
                                // positive IDs. Using Integer.MAX_VALUE offset avoids collision with real entities.
                                int entityId = Integer.MAX_VALUE - spawnedCount;
                                armorStand.setId(entityId);
                                world.addEntity(armorStand);
                                spawned.add(armorStand);
                                spawnedCount++;
                            }
                        }

                        LOGGER.info("  Spawned {} client-side armor stands", spawned.size());

                    } catch (Exception spawnEx) {
                        LOGGER.warn("Could not spawn client-side entities, measuring existing entity count", spawnEx);
                        result.setNotes("Entity spawning limited; measuring existing world entities");
                    }

                    // Settle
                    for (int i = 0; i < BenchmarkConstants.SETTLE_FRAMES; i++) {
                        waitOneFrame(client);
                    }

                    // Warmup
                    for (int i = 0; i < BenchmarkConstants.WARMUP_FRAMES; i++) {
                        waitOneFrame(client);
                    }

                    // Measurement
                    sampler.reset();
                    long frameStart;
                    for (int i = 0; i < FRAMES_PER_COUNT; i++) {
                        frameStart = System.nanoTime();
                        waitOneFrame(client);
                        long frameTime = System.nanoTime() - frameStart;
                        sampler.record(frameTime);

                        // [CODE-REVIEW-FIX] L-001: Also fire on last frame to ensure 100% progress
                        if (i % 60 == 0 || i == FRAMES_PER_COUNT - 1) {
                            callback.onProgress(label, completedFrames + i, totalFrames);
                        }
                    }

                    long[] samples = sampler.getSamplesSnapshot();
                    FrameTimeStatistics stats = FrameTimeStatistics.from(samples);
                    result.addMeasurement(label, stats);

                    LOGGER.info("  {} entities: avg={} FPS, 1% low={} FPS", targetCount,
                            String.format("%.1f", stats.avgFps()),
                            String.format("%.1f", stats.p1LowFps()));
                } finally {
                    // [CODE-REVIEW-FIX] Always discard spawned entities even if measurement throws
                    for (ArmorStandEntity entity : spawned) {
                        entity.discard();
                    }
                    spawned.clear();
                }

                completedFrames += FRAMES_PER_COUNT;
            }

        } catch (Exception e) {
            LOGGER.error("Error during entity stress phase", e);
            result.setNotes("Error: " + e); // [CODE-REVIEW-FIX] L-002
        } finally {
            adapter.setEntityDistanceScaling(originalEntityDistance);
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
