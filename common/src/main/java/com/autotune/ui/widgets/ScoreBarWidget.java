package com.autotune.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.text.Text;

/**
 * Horizontal color-coded score bar that displays a value from 0 to 100.
 * Red (0-30), Yellow (30-60), Green (60-100).
 * Shows a label on the left and the numeric value on the right.
 */
public class ScoreBarWidget implements Drawable {

    private static final int COLOR_RED = 0xFFE74C3C;
    private static final int COLOR_YELLOW = 0xFFF39C12;
    private static final int COLOR_GREEN = 0xFF2ECC71;
    private static final int BAR_BG_COLOR = 0xFF2C2C2C;
    private static final int BORDER_COLOR = 0xFF555555;

    private int x;
    private int y;
    private int width;
    private final int height;
    private String label;
    private int score;

    public ScoreBarWidget(int x, int y, int width, int height, String label, int score) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.score = Math.clamp(score, 0, 100);
    }

    public void setScore(int score) {
        this.score = Math.clamp(score, 0, 100);
    }

    public int getScore() {
        return score;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int labelWidth = textRenderer.getWidth(label);
        int valueText = score;
        String valueStr = String.valueOf(valueText);
        int valueWidth = textRenderer.getWidth(valueStr);

        int labelAreaWidth = Math.max(labelWidth + 4, 70);
        int valueAreaWidth = Math.max(valueWidth + 6, 30);
        int barX = x + labelAreaWidth + 4;
        int barWidth = width - labelAreaWidth - valueAreaWidth - 8;
        int barHeight = height;

        // Draw label
        int labelY = y + (barHeight - 8) / 2;
        context.drawText(textRenderer, Text.literal(label), x, labelY, 0xFFCCCCCC, false);

        // Draw bar background
        context.fill(barX, y, barX + barWidth, y + barHeight, BAR_BG_COLOR);

        // Draw bar border
        drawBorder(context, barX, y, barWidth, barHeight, BORDER_COLOR);

        // Draw filled portion
        int filledWidth = (int) ((barWidth - 2) * (score / 100.0));
        if (filledWidth > 0) {
            int fillColor = getColorForScore(score);
            context.fill(barX + 1, y + 1, barX + 1 + filledWidth, y + barHeight - 1, fillColor);
        }

        // Draw value text
        int valueX = barX + barWidth + 4;
        context.drawText(textRenderer, Text.literal(valueStr), valueX, labelY, getColorForScore(score), false);
    }

    private int getColorForScore(int score) {
        if (score <= 30) return COLOR_RED;
        if (score <= 60) return COLOR_YELLOW;
        return COLOR_GREEN;
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }
}
