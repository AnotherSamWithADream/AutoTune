package com.autotune.live;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * Detects gameplay context to enable proactive performance adjustments.
 */
public class ContextDetector {

    private GameplayContext lastContext = GameplayContext.NORMAL;
    private long lastContextChangeTime;
    private double lastPlayerX, lastPlayerZ;
    private long lastPositionCheckTime;

    public GameplayContext detectContext() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            return GameplayContext.NORMAL;
        }

        ClientPlayerEntity player = client.player;
        long now = System.currentTimeMillis();

        // Check dimension
        RegistryKey<World> dimension = client.world.getRegistryKey();
        if (dimension == World.NETHER) {
            return GameplayContext.NETHER;
        }
        if (dimension == World.END) {
            return GameplayContext.END;
        }

        // Check for fast movement (elytra, minecart, teleport)
        if (isMovingFast(player, now)) {
            return GameplayContext.FAST_MOVEMENT;
        }

        // Check entity count (mob farm detection)
        int nearbyEntities = countNearbyEntities(client);
        if (nearbyEntities > 150) {
            return GameplayContext.HIGH_ENTITY_COUNT;
        }

        return GameplayContext.NORMAL;
    }

    // [CODE-REVIEW-FIX] L-003: The 500ms check below is an intentional debounce/sticky window.
    // Within 500ms of the last position sample, we return the cached movement state rather than
    // re-sampling. This avoids noisy speed calculations from very small time deltas and prevents
    // rapid context flipping between FAST_MOVEMENT and NORMAL during brief pauses in elytra flight.
    private boolean isMovingFast(ClientPlayerEntity player, long now) {
        if (now - lastPositionCheckTime < 500) {
            return lastContext == GameplayContext.FAST_MOVEMENT;
        }

        double dx = player.getX() - lastPlayerX;
        double dz = player.getZ() - lastPlayerZ;
        double distSq = dx * dx + dz * dz;
        double timeDeltaSec = (now - lastPositionCheckTime) / 1000.0;

        lastPlayerX = player.getX();
        lastPlayerZ = player.getZ();
        lastPositionCheckTime = now;

        if (timeDeltaSec <= 0) return false;

        // Speed threshold: ~30 blocks/sec (elytra speed or teleport)
        double speedSq = distSq / (timeDeltaSec * timeDeltaSec);
        return speedSq > 900; // 30^2
    }

    // [CODE-REVIEW-FIX] Filter entities by distance from player (64 blocks) instead of iterating ALL world entities
    private static final double NEARBY_RANGE = 64.0;
    private static final double NEARBY_RANGE_SQ = NEARBY_RANGE * NEARBY_RANGE;

    private int countNearbyEntities(MinecraftClient client) {
        if (client.world == null || client.player == null) return 0;
        double playerX = client.player.getX();
        double playerY = client.player.getY();
        double playerZ = client.player.getZ();
        int count = 0;
        for (Entity entity : client.world.getEntities()) {
            double dx = entity.getX() - playerX;
            double dy = entity.getY() - playerY;
            double dz = entity.getZ() - playerZ;
            if (dx * dx + dy * dy + dz * dz <= NEARBY_RANGE_SQ) {
                count++;
            }
            if (count > 200) break; // Early exit, already high
        }
        return count;
    }

    public GameplayContext getLastContext() {
        return lastContext;
    }

    public void update() {
        GameplayContext newContext = detectContext();
        if (newContext != lastContext) {
            lastContextChangeTime = System.currentTimeMillis();
            lastContext = newContext;
        }
    }

    public long getTimeSinceContextChange() {
        return System.currentTimeMillis() - lastContextChangeTime;
    }

    public enum GameplayContext {
        NORMAL("Normal", 0, 0),
        NETHER("Nether", -2, 0),          // Pre-emptively reduce render distance by 2
        END("The End", -1, 0),             // Minor reduction
        FAST_MOVEMENT("Fast Movement", -3, 0), // Aggressive reduction during chunk loading
        HIGH_ENTITY_COUNT("Mob Farm", 0, -2); // Reduce simulation distance

        private final String displayName;
        private final int renderDistanceOffset;
        private final int simulationDistanceOffset;

        GameplayContext(String displayName, int renderDistanceOffset, int simulationDistanceOffset) {
            this.displayName = displayName;
            this.renderDistanceOffset = renderDistanceOffset;
            this.simulationDistanceOffset = simulationDistanceOffset;
        }

        public String getDisplayName() { return displayName; }
        public int getRenderDistanceOffset() { return renderDistanceOffset; }
        public int getSimulationDistanceOffset() { return simulationDistanceOffset; }
    }
}
