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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A widget that displays multiple checkboxes with labels.
 * Supports toggling individual options on and off.
 */
public class MultiSelectWidget implements Drawable, Element, Selectable {

    private static final int ITEM_HEIGHT = 20;
    private static final int CHECKBOX_SIZE = 12;
    private static final int PADDING = 4;

    // Colors
    private static final int COLOR_BACKGROUND = 0x80000000;
    private static final int COLOR_CHECKBOX_BG = 0xFF333333;
    private static final int COLOR_CHECKBOX_CHECKED = 0xFF44AA44;
    private static final int COLOR_CHECKBOX_BORDER = 0xFF888888;
    private static final int COLOR_CHECKBOX_HOVER = 0xFF666666;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_TEXT_DESCRIPTION = 0xFFAAAAAA;
    private static final int COLOR_CHECKMARK = 0xFFFFFFFF;
    private static final int COLOR_BORDER = 0xFF555555;

    private final int x;
    private final int y;
    private final int width;
    private final List<CheckboxEntry> entries;
    private int focusedIndex = -1;
    private boolean focused;

    public MultiSelectWidget(int x, int y, int width) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.entries = new ArrayList<>();
    }

    /**
     * Adds a checkbox option.
     *
     * @param key         a unique key for this option
     * @param label       the display label
     * @param description an optional description shown below the label (can be null)
     * @param checked     initial checked state
     */
    public void addOption(String key, String label, @Nullable String description, boolean checked) {
        entries.add(new CheckboxEntry(key, label, description, checked));
    }

    /**
     * Returns whether the option with the given key is checked.
     */
    public boolean isChecked(String key) {
        for (CheckboxEntry entry : entries) {
            if (entry.key.equals(key)) {
                return entry.checked;
            }
        }
        return false;
    }

    /**
     * Sets the checked state of the option with the given key.
     */
    public void setChecked(String key, boolean checked) {
        for (CheckboxEntry entry : entries) {
            if (entry.key.equals(key)) {
                entry.checked = checked;
                break;
            }
        }
    }

    /**
     * Returns a map of all option keys to their checked states.
     */
    public Map<String, Boolean> getSelections() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (CheckboxEntry entry : entries) {
            result.put(entry.key, entry.checked);
        }
        return result;
    }

    private int getEntryHeight(CheckboxEntry entry) {
        return entry.description != null ? ITEM_HEIGHT + 10 : ITEM_HEIGHT;
    }

    public int getHeight() {
        int total = PADDING * 2;
        for (CheckboxEntry entry : entries) {
            total += getEntryHeight(entry);
        }
        return total;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int totalHeight = getHeight();

        // Draw background
        context.fill(x, y, x + width, y + totalHeight, COLOR_BACKGROUND);
        drawBorder(context,x, y, width, totalHeight, COLOR_BORDER);

        int currentY = y + PADDING;
        for (int i = 0; i < entries.size(); i++) {
            CheckboxEntry entry = entries.get(i);
            int entryHeight = getEntryHeight(entry);

            boolean isHovered = mouseX >= x && mouseX <= x + width
                    && mouseY >= currentY && mouseY < currentY + entryHeight;

            // Draw checkbox
            int cbX = x + PADDING + 4;
            int cbY = currentY + (ITEM_HEIGHT - CHECKBOX_SIZE) / 2;

            int cbBgColor = isHovered ? COLOR_CHECKBOX_HOVER : COLOR_CHECKBOX_BG;
            if (entry.checked) {
                cbBgColor = COLOR_CHECKBOX_CHECKED;
            }

            context.fill(cbX, cbY, cbX + CHECKBOX_SIZE, cbY + CHECKBOX_SIZE, cbBgColor);
            drawBorder(context,cbX, cbY, CHECKBOX_SIZE, CHECKBOX_SIZE, COLOR_CHECKBOX_BORDER);

            // Draw checkmark
            if (entry.checked) {
                context.drawText(textRenderer, "\u2714", cbX + 2, cbY + 1, COLOR_CHECKMARK, false);
            }

            // Draw label
            int textX = cbX + CHECKBOX_SIZE + 6;
            context.drawText(textRenderer, entry.label, textX, currentY + (ITEM_HEIGHT - 8) / 2, COLOR_TEXT, true);

            // Draw description if present
            if (entry.description != null) {
                context.drawText(textRenderer, entry.description, textX + 4, currentY + ITEM_HEIGHT - 2,
                        COLOR_TEXT_DESCRIPTION, false);
            }

            // Draw focus indicator
            if (focused && i == focusedIndex) {
                drawBorder(context,x + PADDING, currentY, width - PADDING * 2, entryHeight, 0xFFFFFFFF);
            }

            currentY += entryHeight;
        }
    }

    // No @Override - signature varies between MC versions
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;

        int currentY = y + PADDING;
        for (int i = 0; i < entries.size(); i++) {
            CheckboxEntry entry = entries.get(i);
            int entryHeight = getEntryHeight(entry);

            if (mouseX >= x && mouseX <= x + width
                    && mouseY >= currentY && mouseY < currentY + entryHeight) {
                entry.checked = !entry.checked;
                focusedIndex = i;
                return true;
            }

            currentY += entryHeight;
        }

        return false;
    }

    // No @Override - signature varies between MC versions
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused || entries.isEmpty()) return false;

        // Up arrow
        if (keyCode == 265 && focusedIndex > 0) {
            focusedIndex--;
            return true;
        }

        // Down arrow
        if (keyCode == 264 && focusedIndex < entries.size() - 1) {
            focusedIndex++;
            return true;
        }

        // Space or Enter to toggle
        if ((keyCode == 32 || keyCode == 257) && focusedIndex >= 0 && focusedIndex < entries.size()) {
            entries.get(focusedIndex).checked = !entries.get(focusedIndex).checked;
            return true;
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        if (focused && focusedIndex < 0 && !entries.isEmpty()) {
            focusedIndex = 0;
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

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
        StringBuilder sb = new StringBuilder("Checkbox options. ");
        for (CheckboxEntry entry : entries) {
            sb.append(entry.label).append(entry.checked ? " checked" : " unchecked").append(". ");
        }
        builder.put(NarrationPart.TITLE, Text.literal(sb.toString()));
    }

    /** Draws a 1px border rectangle, compatible across all MC versions. */
    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);           // top
        context.fill(x, y + h - 1, x + w, y + h, color);   // bottom
        context.fill(x, y, x + 1, y + h, color);           // left
        context.fill(x + w - 1, y, x + w, y + h, color);   // right
    }

    private static class CheckboxEntry {
        final String key;
        final String label;
        final String description;
        boolean checked;

        CheckboxEntry(String key, String label, @Nullable String description, boolean checked) {
            this.key = key;
            this.label = label;
            this.description = description;
            this.checked = checked;
        }
    }
}
