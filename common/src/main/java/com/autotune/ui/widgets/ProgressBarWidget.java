package com.autotune.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.text.Text;

/**
 * Horizontal progress bar with percentage text overlay.
 * Supports smooth animation between values, a custom fill color,
 * and an optional label displayed to the left of the bar.
 */
public class ProgressBarWidget implements Drawable {

    private static final int BAR_BG_COLOR = 0xFF2C2C2C;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int DEFAULT_FILL_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private int x;
    private int y;
    private int width;
    private final int height;
    private float progress;
    private float displayProgress;
    private int fillColor;
    private String label;
    private boolean showPercentage;

    public ProgressBarWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.progress = 0f;
        this.displayProgress = 0f;
        this.fillColor = DEFAULT_FILL_COLOR;
        this.label = null;
        this.showPercentage = true;
    }

    public void setProgress(float progress) {
        this.progress = Math.clamp(progress, 0f, 1f);
    }

    public float getProgress() {
        return progress;
    }

    public void setFillColor(int color) {
        this.fillColor = color;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setShowPercentage(boolean show) {
        this.showPercentage = show;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Call each tick to smoothly interpolate the displayed progress.
     */
    public void tick() {
        float diff = progress - displayProgress;
        if (Math.abs(diff) < 0.001f) {
            displayProgress = progress;
        } else {
            displayProgress += diff * 0.15f;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        int barX = x;
        int barWidth = width;

        // Draw optional label
        if (label != null && !label.isEmpty()) {
            int labelWidth = textRenderer.getWidth(label) + 6;
            int labelY = y + (height - 8) / 2;
            context.drawText(textRenderer, Text.literal(label), x, labelY, 0xFFCCCCCC, false);
            barX = x + labelWidth;
            barWidth = width - labelWidth;
        }

        // Background
        context.fill(barX, y, barX + barWidth, y + height, BAR_BG_COLOR);

        // Border
        drawBorder(context, barX, y, barWidth, height, BORDER_COLOR);

        // Filled portion (use smooth displayProgress for animation)
        int fillW = (int) ((barWidth - 2) * displayProgress);
        if (fillW > 0) {
            context.fill(barX + 1, y + 1, barX + 1 + fillW, y + height - 1, fillColor);
        }

        // Percentage text centered on bar
        if (showPercentage) {
            int pct = Math.round(displayProgress * 100f);
            String pctText = pct + "%";
            int textWidth = textRenderer.getWidth(pctText);
            int textX = barX + (barWidth - textWidth) / 2;
            int textY = y + (height - 8) / 2;
            context.drawText(textRenderer, Text.literal(pctText), textX, textY, TEXT_COLOR, true);
        }
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }
}
