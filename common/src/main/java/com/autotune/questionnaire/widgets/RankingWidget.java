package com.autotune.questionnaire.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.navigation.GuiNavigation;
import net.minecraft.client.gui.navigation.GuiNavigationPath;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A draggable ranking list widget where items can be reordered.
 * Items are displayed as a numbered list with up/down buttons for reordering.
 */
public class RankingWidget implements Drawable, Element, Selectable {

    private static final int ITEM_HEIGHT = 24;
    private static final int BUTTON_SIZE = 16;
    private static final int PADDING = 4;
    private static final int DOWN_BUTTON_OFFSET = BUTTON_SIZE + 2;

    private final int x;
    private final int y;
    private final int width;
    private final List<String> items;
    private int selectedIndex = -1;
    private boolean focused;

    // Colors
    private static final int COLOR_BACKGROUND = 0x80000000;
    private static final int COLOR_ITEM_BG = 0x60333333;
    private static final int COLOR_ITEM_SELECTED = 0x80446688;
    private static final int COLOR_ITEM_HOVER = 0x60555555;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_RANK_NUMBER = 0xFFFFAA00;
    private static final int COLOR_BUTTON = 0xFF888888;
    private static final int COLOR_BUTTON_HOVER = 0xFFAAAAFF;
    private static final int COLOR_BORDER = 0xFF555555;

    public RankingWidget(int x, int y, int width, String[] initialItems) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.items = new ArrayList<>();
        Collections.addAll(items, initialItems);
    }

    /**
     * Returns the current order of items, from rank 1 (index 0) to rank N.
     */
    public String[] getOrder() {
        return items.toArray(new String[0]);
    }

    /**
     * Sets the order of items.
     */
    public void setOrder(String[] order) {
        items.clear();
        Collections.addAll(items, order);
    }

    private void moveUp(int index) {
        if (index > 0 && index < items.size()) {
            String temp = items.get(index);
            items.set(index, items.get(index - 1));
            items.set(index - 1, temp);
            selectedIndex = index - 1;
        }
    }

    private void moveDown(int index) {
        if (index >= 0 && index < items.size() - 1) {
            String temp = items.get(index);
            items.set(index, items.get(index + 1));
            items.set(index + 1, temp);
            selectedIndex = index + 1;
        }
    }

    public int getHeight() {
        return items.size() * ITEM_HEIGHT + PADDING * 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // Draw background
        int totalHeight = getHeight();
        context.fill(x, y, x + width, y + totalHeight, COLOR_BACKGROUND);

        // Draw border
        drawBorder(context,x, y, width, totalHeight, COLOR_BORDER);

        for (int i = 0; i < items.size(); i++) {
            int itemY = y + PADDING + i * ITEM_HEIGHT;
            int itemX = x + PADDING;
            int itemWidth = width - PADDING * 2;

            // Determine item background color
            boolean isHovered = mouseX >= x && mouseX <= x + width
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            int bgColor;
            if (i == selectedIndex) {
                bgColor = COLOR_ITEM_SELECTED;
            } else if (isHovered) {
                bgColor = COLOR_ITEM_HOVER;
            } else {
                bgColor = COLOR_ITEM_BG;
            }

            // Draw item background
            context.fill(itemX, itemY, itemX + itemWidth, itemY + ITEM_HEIGHT - 2, bgColor);

            // Draw rank number
            String rankStr = (i + 1) + ".";
            context.drawText(textRenderer, rankStr, itemX + 4, itemY + (ITEM_HEIGHT - 10) / 2, COLOR_RANK_NUMBER, true);

            // Draw item text
            context.drawText(textRenderer, items.get(i), itemX + 24, itemY + (ITEM_HEIGHT - 10) / 2, COLOR_TEXT, true);

            // Draw up/down buttons on the right side
            int btnX = itemX + itemWidth - DOWN_BUTTON_OFFSET - BUTTON_SIZE - 4;

            // Up button (triangle pointing up)
            if (i > 0) {
                boolean upHover = mouseX >= btnX && mouseX <= btnX + BUTTON_SIZE
                        && mouseY >= itemY + 2 && mouseY <= itemY + 2 + BUTTON_SIZE;
                int upColor = upHover ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
                drawUpArrow(context, btnX, itemY + 2, BUTTON_SIZE, upColor);
            }

            // Down button (triangle pointing down)
            int downBtnX = btnX + BUTTON_SIZE + 2;
            if (i < items.size() - 1) {
                boolean downHover = mouseX >= downBtnX && mouseX <= downBtnX + BUTTON_SIZE
                        && mouseY >= itemY + 2 && mouseY <= itemY + 2 + BUTTON_SIZE;
                int downColor = downHover ? COLOR_BUTTON_HOVER : COLOR_BUTTON;
                drawDownArrow(context, downBtnX, itemY + 2, BUTTON_SIZE, downColor);
            }
        }
    }

    private void drawUpArrow(DrawContext context, int x, int y, int size, int color) {
        // Draw a simple up arrow using filled rectangles
        context.fill(x, y, x + size, y + size, 0x40000000);
        drawBorder(context,x, y, size, size, 0xFF444444);
        // Arrow character
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer, "\u25B2", x + 4, y + 3, color, false);
    }

    private void drawDownArrow(DrawContext context, int x, int y, int size, int color) {
        context.fill(x, y, x + size, y + size, 0x40000000);
        drawBorder(context,x, y, size, size, 0xFF444444);
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        context.drawText(textRenderer, "\u25BC", x + 4, y + 3, color, false);
    }

    // No @Override - signature varies between MC versions
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        for (int i = 0; i < items.size(); i++) {
            int itemY = y + PADDING + i * ITEM_HEIGHT;
            int itemX = x + PADDING;
            int itemWidth = width - PADDING * 2;

            if (mouseX < x || mouseX > x + width || mouseY < itemY || mouseY >= itemY + ITEM_HEIGHT) {
                continue;
            }

            int btnX = itemX + itemWidth - DOWN_BUTTON_OFFSET - BUTTON_SIZE - 4;
            int downBtnX = btnX + BUTTON_SIZE + 2;

            // Check up button
            if (i > 0 && mouseX >= btnX && mouseX <= btnX + BUTTON_SIZE
                    && mouseY >= itemY + 2 && mouseY <= itemY + 2 + BUTTON_SIZE) {
                moveUp(i);
                return true;
            }

            // Check down button
            if (i < items.size() - 1 && mouseX >= downBtnX && mouseX <= downBtnX + BUTTON_SIZE
                    && mouseY >= itemY + 2 && mouseY <= itemY + 2 + BUTTON_SIZE) {
                moveDown(i);
                return true;
            }

            // Clicked on the item itself - select it
            selectedIndex = i;
            return true;
        }

        return false;
    }

    // No @Override - signature varies between MC versions
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || selectedIndex < 0) return false;

        // Up arrow key - move item up
        if (keyCode == 265) { // GLFW_KEY_UP
            if ((modifiers & 1) != 0) { // Shift held
                moveUp(selectedIndex);
                return true;
            } else if (selectedIndex > 0) {
                selectedIndex--;
                return true;
            }
        }

        // Down arrow key - move item down
        if (keyCode == 264) { // GLFW_KEY_DOWN
            if ((modifiers & 1) != 0) { // Shift held
                moveDown(selectedIndex);
                return true;
            } else if (selectedIndex < items.size() - 1) {
                selectedIndex++;
                return true;
            }
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused && selectedIndex < 0 && !items.isEmpty()) {
            selectedIndex = 0;
        }
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Nullable
    @Override
    public GuiNavigationPath getNavigationPath(GuiNavigation navigation) {
        return Element.super.getNavigationPath(navigation);
    }

    @Override
    public SelectionType getType() {
        if (focused) {
            return SelectionType.FOCUSED;
        }
        return SelectionType.NONE;
    }

    /** Draws a 1px border rectangle, compatible across all MC versions. */
    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);           // top
        context.fill(x, y + h - 1, x + w, y + h, color);   // bottom
        context.fill(x, y, x + 1, y + h, color);           // left
        context.fill(x + w - 1, y, x + w, y + h, color);   // right
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
        StringBuilder sb = new StringBuilder("Priority ranking list. ");
        for (int i = 0; i < items.size(); i++) {
            sb.append("Rank ").append(i + 1).append(": ").append(items.get(i));
            if (i < items.size() - 1) {
                sb.append(", ");
            }
        }
        builder.put(NarrationPart.TITLE, Text.literal(sb.toString()));
    }
}
