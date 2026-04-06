package com.autotune.ui.hud;

import com.autotune.live.AdaptiveState;
import com.autotune.live.AdjustmentHistory;
import com.autotune.live.LiveAdaptiveEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Live mode status overlay rendered in the top-right corner.
 * Shows current FPS (color coded), adaptive state indicator
 * (STABLE/DEGRADING/RECOVERING/BOOSTING/EMERGENCY), the last adjustment,
 * and a context-awareness indicator.
 * Fades to near-transparent after 5 seconds of STABLE state.
 */
public class LiveModeHud {

    private static final int GREEN = 0xFF2ECC71;
    private static final int YELLOW = 0xFFF39C12;
    private static final int ORANGE = 0xFFFF8800;
    private static final int RED = 0xFFE74C3C;
    private static final int BLUE = 0xFF3498DB;
    private static final int DIM = 0xFF888888;
    private static final int TEXT = 0xFFCCCCCC;

    private static final int HUD_WIDTH = 150;
    private static final int HUD_BASE_HEIGHT = 48;
    private static final long FADE_DELAY_MS = 5000;
    private static final long FADE_DURATION_MS = 3000;
    private static final float MIN_ALPHA = 0.15f;

    private final LiveAdaptiveEngine engine;
    private long lastNonStableTime;

    public LiveModeHud(LiveAdaptiveEngine engine) {
        this.engine = engine;
        this.lastNonStableTime = System.currentTimeMillis();
    }

    /**
     * Renders the live mode HUD overlay.
     */
    public void render(DrawContext context) {
        if (!engine.isEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();

        AdaptiveState state = engine.getState();
        long now = System.currentTimeMillis();

        // Track last non-stable time for fade
        if (state != AdaptiveState.STABLE) {
            lastNonStableTime = now;
        }

        // Calculate opacity (fade to near-transparent when stable for 5s)
        float alpha = 1.0f;
        if (state == AdaptiveState.STABLE) {
            long stableMs = now - lastNonStableTime;
            if (stableMs > FADE_DELAY_MS) {
                float fadeProgress = Math.min(1f, (float) (stableMs - FADE_DELAY_MS) / FADE_DURATION_MS);
                alpha = Math.max(MIN_ALPHA, 1.0f - fadeProgress * (1.0f - MIN_ALPHA));
            }
        }

        // Determine HUD height based on content
        AdjustmentHistory.AdjustmentEntry lastAdj = engine.getAdjustmentHistory().getLastAdjustment();
        boolean showAdj = lastAdj != null && (now - lastAdj.timestamp()) < 10_000;
        var ctx = engine.getContextDetector().getLastContext();
        boolean showCtx = ctx != com.autotune.live.ContextDetector.GameplayContext.NORMAL;
        int extraLines = (showAdj ? 1 : 0) + (showCtx ? 1 : 0);
        int hudHeight = HUD_BASE_HEIGHT + extraLines * 11;

        int baseAlpha = (int) (alpha * 200);
        int textAlpha = (int) (alpha * 255);

        int boxX = screenWidth - HUD_WIDTH - 5;
        int boxY = 5;

        // Background
        context.fill(boxX, boxY, boxX + HUD_WIDTH, boxY + hudHeight, (baseAlpha << 24));

        // Border colored by state
        int borderColor = applyAlpha(getStateColor(state), textAlpha);
        drawBorder(context, boxX, boxY, HUD_WIDTH, hudHeight, borderColor);

        int lineY = boxY + 4;

        // Current FPS
        double fps = engine.getCurrentFps();
        int fpsColor = applyAlpha(getFpsColor(fps, engine.getTargetFps(), engine.getFloorFps()), textAlpha);
        String fpsText = String.format("%.0f FPS", fps);
        context.drawText(textRenderer, Text.literal(fpsText), boxX + 4, lineY, fpsColor, true);

        // State indicator (right-aligned on same line)
        String stateText = state.getDisplayName().toUpperCase();
        int stateColor = applyAlpha(getStateColor(state), textAlpha);
        int stateWidth = textRenderer.getWidth(stateText);
        context.drawText(textRenderer, Text.literal(stateText),
                boxX + HUD_WIDTH - 4 - stateWidth, lineY, stateColor, false);

        lineY += 11;

        // 1% low FPS and target
        double lowFps = engine.getCurrent1PercentLow();
        int lowColor = applyAlpha(getFpsColor(lowFps, engine.getTargetFps(), engine.getFloorFps()), textAlpha);
        String lowText = String.format("1%%: %.0f", lowFps);
        context.drawText(textRenderer, Text.literal(lowText), boxX + 4, lineY, lowColor, false);

        String targetText = "Target: " + engine.getTargetFps();
        int dimColor = applyAlpha(DIM, textAlpha);
        int targetWidth = textRenderer.getWidth(targetText);
        context.drawText(textRenderer, Text.literal(targetText),
                boxX + HUD_WIDTH - 4 - targetWidth, lineY, dimColor, false);

        lineY += 11;

        // Adjustments count
        int totalAdj = engine.getAdjustmentHistory().getTotalAdjustments();
        String adjCountText = totalAdj + " adjustments";
        context.drawText(textRenderer, Text.literal(adjCountText), boxX + 4, lineY, dimColor, false);

        lineY += 11;

        // Last adjustment (fades out after 7-10 seconds)
        if (showAdj) {
            long age = now - lastAdj.timestamp();
            float adjAlpha = alpha;
            if (age > 7_000) {
                adjAlpha *= (float) (10_000 - age) / 3000f;
            }
            int adjColor = applyAlpha(TEXT, (int) (adjAlpha * 255));
            String adjText = lastAdj.toDisplayString();
            // Truncate to fit HUD width by pixel measurement
            int maxAdjW = HUD_WIDTH - 8;
            if (textRenderer.getWidth(adjText) > maxAdjW) {
                while (adjText.length() > 3 && textRenderer.getWidth(adjText + "...") > maxAdjW) {
                    adjText = adjText.substring(0, adjText.length() - 1);
                }
                adjText = adjText + "...";
            }
            context.drawText(textRenderer, Text.literal(adjText), boxX + 4, lineY, adjColor, false);
            lineY += 11;
        }

        // Context awareness indicator
        if (showCtx) {
            int ctxColor = applyAlpha(ORANGE, textAlpha);
            context.drawText(textRenderer, Text.literal(ctx.getDisplayName()),
                    boxX + 4, lineY, ctxColor, false);
        }

        // Status indicator dot (bottom-right)
        int dotColor = applyAlpha(getStateColor(state), textAlpha);
        context.fill(boxX + HUD_WIDTH - 8, boxY + hudHeight - 8,
                boxX + HUD_WIDTH - 4, boxY + hudHeight - 4, dotColor);

        // Toast notifications (rendered via the engine's toast manager)
        engine.getToastManager().render(context, screenWidth, client.getWindow().getScaledHeight());
    }

    private int getFpsColor(double fps, int target, int floor) {
        if (fps >= target) return GREEN;
        if (fps >= target * 0.85) return YELLOW;
        if (fps >= floor) return ORANGE;
        return RED;
    }

    private int getStateColor(AdaptiveState state) {
        return switch (state) {
            case STABLE -> GREEN;
            case DEGRADING -> YELLOW;
            case RECOVERING, BOOSTING -> BLUE;
            case EMERGENCY -> RED;
            case LOCKED -> DIM;
        };
    }

    private int applyAlpha(int color, int alpha) {
        alpha = Math.clamp(alpha, 0, 255);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }
}
