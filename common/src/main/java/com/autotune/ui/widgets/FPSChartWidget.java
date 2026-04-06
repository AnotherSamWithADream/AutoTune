package com.autotune.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.text.Text;

/**
 * Simple rolling line chart plotting the last 60 seconds of FPS data.
 * Draws a horizontal line at the target FPS and colors the graph
 * green above target, red below.
 */
public class FPSChartWidget implements Drawable {

    private static final int BG_COLOR = 0xFF1A1A2E;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int GRID_COLOR = 0xFF333344;
    private static final int TARGET_LINE_COLOR = 0xAAF39C12;
    private static final int COLOR_ABOVE = 0xFF2ECC71;
    private static final int COLOR_BELOW = 0xFFE74C3C;
    private static final int AXIS_TEXT_COLOR = 0xFF999999;
    private static final int LABEL_COLOR = 0xFFCCCCCC;

    private static final int MAX_SAMPLES = 360; // 60 seconds at ~6 samples/sec

    private int x;
    private int y;
    private int width;
    private int height;
    private int targetFps;
    private final double[] fpsHistory;
    private int writeIndex;
    private int sampleCount;

    public FPSChartWidget(int x, int y, int width, int height, int targetFps) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.targetFps = targetFps;
        this.fpsHistory = new double[MAX_SAMPLES];
        this.writeIndex = 0;
        this.sampleCount = 0;
    }

    /**
     * Records a new FPS sample. Call this periodically (every ~166ms for 6/sec).
     */
    public void recordFps(double fps) {
        fpsHistory[writeIndex] = fps;
        writeIndex = (writeIndex + 1) % MAX_SAMPLES;
        if (sampleCount < MAX_SAMPLES) {
            sampleCount++;
        }
    }

    public void setTargetFps(int targetFps) {
        this.targetFps = targetFps;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void clear() {
        sampleCount = 0;
        writeIndex = 0;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        int chartLeft = x + 30; // Leave space for Y-axis labels
        int chartTop = y + 12;  // Leave space for title
        int chartWidth = width - 34;
        int chartHeight = height - 24; // Bottom for X-axis label

        // Background
        context.fill(x, y, x + width, y + height, BG_COLOR);
        drawBorder(context, x, y, width, height, BORDER_COLOR);

        // Title
        context.drawText(textRenderer, Text.literal("FPS History (60s)"), chartLeft, y + 2, LABEL_COLOR, false);

        // Chart area border
        context.fill(chartLeft, chartTop, chartLeft + chartWidth, chartTop + chartHeight, 0xFF111122);
        drawBorder(context, chartLeft, chartTop, chartWidth, chartHeight, BORDER_COLOR);

        // Calculate Y-axis scale
        double maxFps = targetFps * 2.0;
        if (sampleCount > 0) {
            for (int i = 0; i < sampleCount; i++) {
                int idx = ((writeIndex - sampleCount + i) + MAX_SAMPLES) % MAX_SAMPLES;
                if (fpsHistory[idx] > maxFps) {
                    maxFps = fpsHistory[idx] * 1.1;
                }
            }
        }
        maxFps = Math.max(maxFps, 10);

        // Horizontal grid lines
        int gridLines = 4;
        for (int i = 1; i < gridLines; i++) {
            int gridY = chartTop + (int) (chartHeight * ((double) i / gridLines));
            context.fill(chartLeft + 1, gridY, chartLeft + chartWidth - 1, gridY + 1, GRID_COLOR);
            int fpsLabel = (int) (maxFps * (1.0 - (double) i / gridLines));
            context.drawText(textRenderer, Text.literal(String.valueOf(fpsLabel)),
                    x + 2, gridY - 4, AXIS_TEXT_COLOR, false);
        }

        // Target FPS horizontal line
        double targetRatio = 1.0 - (targetFps / maxFps);
        int targetY = chartTop + (int) (chartHeight * targetRatio);
        if (targetY > chartTop && targetY < chartTop + chartHeight) {
            for (int px = chartLeft + 1; px < chartLeft + chartWidth - 1; px += 4) {
                int dashEnd = Math.min(px + 2, chartLeft + chartWidth - 1);
                context.fill(px, targetY, dashEnd, targetY + 1, TARGET_LINE_COLOR);
            }
            context.drawText(textRenderer, Text.literal(targetFps + " FPS"),
                    x + 2, targetY - 4, 0xFFF39C12, false);
        }

        // Plot FPS line
        if (sampleCount >= 2) {
            int pointsToDraw = Math.min(sampleCount, chartWidth);
            double sampleStep = (double) sampleCount / pointsToDraw;

            int prevPixelY = -1;
            for (int px = 0; px < pointsToDraw; px++) {
                int sampleIdx = (int) (px * sampleStep);
                int bufferIdx = ((writeIndex - sampleCount + sampleIdx) + MAX_SAMPLES) % MAX_SAMPLES;
                double fps = fpsHistory[bufferIdx];

                double ratio = 1.0 - (fps / maxFps);
                ratio = Math.clamp(ratio, 0, 1);
                int pixelY = chartTop + 1 + (int) ((chartHeight - 2) * ratio);

                int lineColor = fps >= targetFps ? COLOR_ABOVE : COLOR_BELOW;
                int pixelX = chartLeft + 1 + (int) ((chartWidth - 2) * ((double) px / pointsToDraw));

                if (prevPixelY >= 0) {
                    // Draw vertical line segment between previous and current Y
                    int minY = Math.min(prevPixelY, pixelY);
                    int maxY = Math.max(prevPixelY, pixelY);
                    for (int ly = minY; ly <= maxY; ly++) {
                        if (ly >= chartTop + 1 && ly < chartTop + chartHeight - 1) {
                            context.fill(pixelX, ly, pixelX + 1, ly + 1, lineColor);
                        }
                    }
                }

                // Draw current point
                if (pixelY >= chartTop + 1 && pixelY < chartTop + chartHeight - 1) {
                    context.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, lineColor);
                }

                prevPixelY = pixelY;
            }
        }

        // Bottom label
        String timeLabel = "60s ago";
        String nowLabel = "now";
        context.drawText(textRenderer, Text.literal(timeLabel), chartLeft, chartTop + chartHeight + 2, AXIS_TEXT_COLOR, false);
        int nowWidth = textRenderer.getWidth(nowLabel);
        context.drawText(textRenderer, Text.literal(nowLabel), chartLeft + chartWidth - nowWidth, chartTop + chartHeight + 2, AXIS_TEXT_COLOR, false);
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }
}
