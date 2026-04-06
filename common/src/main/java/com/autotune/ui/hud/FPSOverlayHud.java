package com.autotune.ui.hud;

import com.autotune.AutoTuneMod;
import com.autotune.live.RollingFrameBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Simple FPS counter HUD overlay rendered in the top-left corner.
 * Displays current FPS and 1% low FPS, color-coded:
 * green (at or above target), yellow (above floor), red (below floor).
 * Updates every 250ms for smooth, non-flickering display.
 */
public class FPSOverlayHud {

    private static final int BG_COLOR = 0x88000000;
    private static final int GREEN = 0xFF2ECC71;
    private static final int YELLOW = 0xFFF39C12;
    private static final int ORANGE = 0xFFFF8800;
    private static final int RED = 0xFFE74C3C;
    private static final int HUD_X = 4;
    private static final int HUD_Y = 4;
    private static final int PADDING = 3;

    private long lastUpdateTime;
    private double displayFps;
    private double displayP1Low;

    /**
     * Renders the FPS overlay in the top-left corner.
     */
    public void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        TextRenderer textRenderer = client.textRenderer;
        long now = System.currentTimeMillis();

        // Update values every 250ms for smooth, non-flickering display
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        if (now - lastUpdateTime > 250) {
            lastUpdateTime = now;
            var engine = mod.getLiveEngine();
            if (engine != null) {
                RollingFrameBuffer buffer = engine.getFrameBuffer();
                displayFps = buffer.getAverageFps();
                displayP1Low = buffer.get1PercentLowFps();
            }
        }

        // [CODE-REVIEW-FIX] Null guard for getConfig()
        var config = mod.getConfig();
        if (config == null) return;
        int targetFps = config.getTargetFps();
        int floorFps = config.getFloorFps();

        int fpsColor = getFpsColor(displayFps, targetFps, floorFps);
        int lowColor = getFpsColor(displayP1Low, targetFps, floorFps);

        String fpsText = String.format("%.0f FPS", displayFps);
        String lowText = String.format("1%%: %.0f", displayP1Low);

        int fpsWidth = textRenderer.getWidth(fpsText);
        int lowWidth = textRenderer.getWidth(lowText);
        int maxTextWidth = Math.max(fpsWidth, lowWidth);

        int hudWidth = maxTextWidth + PADDING * 2 + 2;
        int hudHeight = 24;

        // Background
        context.fill(HUD_X, HUD_Y, HUD_X + hudWidth, HUD_Y + hudHeight, BG_COLOR);

        // FPS value (prominent, with shadow for readability)
        context.drawText(textRenderer, Text.literal(fpsText),
                HUD_X + PADDING, HUD_Y + PADDING, fpsColor, true);

        // 1% low value (below)
        context.drawText(textRenderer, Text.literal(lowText),
                HUD_X + PADDING, HUD_Y + PADDING + 11, lowColor, false);
    }

    private int getFpsColor(double fps, int target, int floor) {
        if (fps >= target) return GREEN;
        if (fps >= target * 0.85) return YELLOW;
        if (fps >= floor) return ORANGE;
        return RED;
    }
}
