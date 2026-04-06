package com.autotune.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.text.Text;

/**
 * Displays an old -> new value change for a single setting.
 * The old value is shown in red/dim, an arrow separator, then the new value in green.
 */
public class SettingsDiffWidget implements Drawable {

    private static final int OLD_VALUE_COLOR = 0xFFE74C3C;
    private static final int NEW_VALUE_COLOR = 0xFF2ECC71;
    private static final int ARROW_COLOR = 0xFFAAAAAA;
    private static final int LABEL_COLOR = 0xFFDDDDDD;
    private static final int BG_COLOR = 0xFF1A1A2E;
    private static final int BG_HOVER_COLOR = 0xFF252542;

    private int x;
    private int y;
    private int width;
    private final int height;
    private final String settingName;
    private final String oldValue;
    private final String newValue;

    public SettingsDiffWidget(int x, int y, int width, int height,
                              String settingName, String oldValue, String newValue) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.settingName = settingName;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public String getSettingName() {
        return settingName;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

        // Background
        int bgColor = hovered ? BG_HOVER_COLOR : BG_COLOR;
        context.fill(x, y, x + width, y + height, bgColor);

        // Bottom separator line
        context.fill(x, y + height - 1, x + width, y + height, 0xFF333355);

        int textY = y + (height - 8) / 2;
        int padding = 6;

        // Setting name (left aligned)
        context.drawText(textRenderer, Text.literal(settingName), x + padding, textY, LABEL_COLOR, false);

        // Values (right aligned): oldValue -> newValue
        String arrow = " -> ";
        int arrowWidth = textRenderer.getWidth(arrow);
        int oldWidth = textRenderer.getWidth(oldValue);
        int newWidth = textRenderer.getWidth(newValue);
        int totalValueWidth = oldWidth + arrowWidth + newWidth;

        int valueStartX = x + width - padding - totalValueWidth;
        context.drawText(textRenderer, Text.literal(oldValue), valueStartX, textY, OLD_VALUE_COLOR, false);
        context.drawText(textRenderer, Text.literal(arrow), valueStartX + oldWidth, textY, ARROW_COLOR, false);
        context.drawText(textRenderer, Text.literal(newValue), valueStartX + oldWidth + arrowWidth, textY, NEW_VALUE_COLOR, false);
    }
}
