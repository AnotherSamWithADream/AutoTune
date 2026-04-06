package com.autotune.questionnaire.screens;

import com.autotune.questionnaire.PlayerPreferences;
import com.autotune.questionnaire.QuestionnaireManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Screen 7: Setting overrides.
 * List of settings with lock checkboxes. Locked settings won't be changed by optimizer.
 */
public class OverridesScreen extends Screen {

    private static final Text TITLE = Text.translatable("autotune.questionnaire.overrides.title");
    private static final Text SUBTITLE = Text.translatable("autotune.questionnaire.overrides.subtitle");

    private static final int ITEM_HEIGHT = 20;
    private static final int VISIBLE_ITEMS = 8;
    private static final int CHECKBOX_SIZE = 12;

    private final QuestionnaireManager manager;
    private final List<SettingEntry> settingEntries;
    private int scrollOffset = 0;
    private int listX;
    private int listY;
    private int listWidth;

    public OverridesScreen(QuestionnaireManager manager) {
        super(TITLE);
        this.manager = manager;
        this.settingEntries = new ArrayList<>();
        initializeSettings();
    }

    private void initializeSettings() {
        PlayerPreferences prefs = manager.getPreferences();
        Map<String, Object> locked = prefs.getLockedSettings();

        // Common Minecraft video settings that the optimizer might change
        addSetting("graphics", "Graphics Quality", "Fancy, Fast, or Fabulous",
                locked.containsKey("graphics"));
        addSetting("renderDistance", "Render Distance", "How far you can see (in chunks)",
                locked.containsKey("renderDistance"));
        addSetting("simulationDistance", "Simulation Distance", "Chunk simulation range",
                locked.containsKey("simulationDistance"));
        addSetting("vsync", "VSync", "Synchronize with monitor refresh rate",
                locked.containsKey("vsync"));
        addSetting("maxFramerate", "Max Framerate", "Maximum frame rate cap",
                locked.containsKey("maxFramerate"));
        addSetting("viewBobbing", "View Bobbing", "Camera bob while walking",
                locked.containsKey("viewBobbing"));
        addSetting("guiScale", "GUI Scale", "Size of the interface",
                locked.containsKey("guiScale"));
        addSetting("particles", "Particles", "Particle effects level",
                locked.containsKey("particles"));
        addSetting("smoothLighting", "Smooth Lighting", "Ambient occlusion level",
                locked.containsKey("smoothLighting"));
        addSetting("clouds", "Clouds", "Cloud rendering style",
                locked.containsKey("clouds"));
        addSetting("entityShadows", "Entity Shadows", "Shadow rendering for entities",
                locked.containsKey("entityShadows"));
        addSetting("biomeBlendRadius", "Biome Blend", "Biome color blending radius",
                locked.containsKey("biomeBlendRadius"));
        addSetting("entityDistance", "Entity Distance", "Entity render distance scaling",
                locked.containsKey("entityDistance"));
        addSetting("mipmapLevels", "Mipmap Levels", "Texture mipmap level",
                locked.containsKey("mipmapLevels"));
        addSetting("fullscreen", "Fullscreen", "Fullscreen mode",
                locked.containsKey("fullscreen"));
        addSetting("fov", "Field of View", "Camera field of view",
                locked.containsKey("fov"));
    }

    private void addSetting(String key, String name, String description, boolean locked) {
        settingEntries.add(new SettingEntry(key, name, description, locked));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        listWidth = 300;
        listX = centerX - listWidth / 2;
        listY = 78;

        // Lock All / Unlock All buttons
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.overrides.lockall"),
                button -> {
                    for (SettingEntry entry : settingEntries) {
                        entry.locked = true;
                    }
                }
        ).dimensions(centerX - 110, listY + VISIBLE_ITEMS * ITEM_HEIGHT + 8, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.overrides.unlockall"),
                button -> {
                    for (SettingEntry entry : settingEntries) {
                        entry.locked = false;
                    }
                }
        ).dimensions(centerX + 10, listY + VISIBLE_ITEMS * ITEM_HEIGHT + 8, 100, 20).build());

        // Navigation buttons
        int buttonY = this.height - 36;
        int navBtnWidth = 100;

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.back"),
                button -> manager.previousScreen()
        ).dimensions(centerX - navBtnWidth - 10, buttonY, navBtnWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.next"),
                button -> {
                    saveSelections();
                    manager.nextScreen();
                }
        ).dimensions(centerX + 10, buttonY, navBtnWidth, 20).build());
    }

    private void saveSelections() {
        PlayerPreferences prefs = manager.getPreferences();
        Map<String, Object> locked = new LinkedHashMap<>();
        for (SettingEntry entry : settingEntries) {
            if (entry.locked) {
                locked.put(entry.key, true);
            }
        }
        prefs.setLockedSettings(locked);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;

        // Header
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("AutoTune Setup - Step 7 of " + manager.getTotalScreens()),
                centerX, 10, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                TITLE, centerX, 25, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                SUBTITLE, centerX, 40, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Locked settings will not be changed by the optimizer."),
                centerX, 55, 0xFF88AACC);

        // Draw scrollable list background
        int listHeight = VISIBLE_ITEMS * ITEM_HEIGHT;
        context.fill(listX - 2, listY - 2, listX + listWidth + 2, listY + listHeight + 2, 0x80000000);
        // Draw border (compatible across MC versions)
        drawBorder(context, listX - 2, listY - 2, listWidth + 4, listHeight + 4, 0xFF555555);

        // Draw visible items
        int maxVisible = Math.min(VISIBLE_ITEMS, settingEntries.size() - scrollOffset);
        for (int i = 0; i < maxVisible; i++) {
            int entryIndex = scrollOffset + i;
            SettingEntry entry = settingEntries.get(entryIndex);
            int itemY = listY + i * ITEM_HEIGHT;

            boolean isHovered = mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;

            // Item background
            int bgColor = isHovered ? 0x60555555 : (i % 2 == 0 ? 0x40333333 : 0x40222222);
            context.fill(listX, itemY, listX + listWidth, itemY + ITEM_HEIGHT, bgColor);

            // Lock checkbox
            int cbX = listX + 4;
            int cbY = itemY + (ITEM_HEIGHT - CHECKBOX_SIZE) / 2;
            int cbColor = entry.locked ? 0xFFDD4444 : 0xFF333333;
            context.fill(cbX, cbY, cbX + CHECKBOX_SIZE, cbY + CHECKBOX_SIZE, cbColor);
            drawBorder(context, cbX, cbY, CHECKBOX_SIZE, CHECKBOX_SIZE, 0xFF888888);

            if (entry.locked) {
                context.drawText(this.textRenderer, "\uD83D\uDD12", cbX + 1, cbY + 1, 0xFFFFFFFF, false);
            }

            // Setting name
            int textColor = entry.locked ? 0xFFFF8888 : 0xFFFFFFFF;
            context.drawText(this.textRenderer, entry.name, cbX + CHECKBOX_SIZE + 6,
                    itemY + (ITEM_HEIGHT - 8) / 2, textColor, true);

            // Lock status indicator on right
            String status = entry.locked ? "LOCKED" : "";
            if (!status.isEmpty()) {
                int statusWidth = this.textRenderer.getWidth(status);
                context.drawText(this.textRenderer, status,
                        listX + listWidth - statusWidth - 6,
                        itemY + (ITEM_HEIGHT - 8) / 2, 0xFFFF6666, false);
            }
        }

        // Scroll indicators
        if (scrollOffset > 0) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("\u25B2 Scroll up"), centerX, listY - 12, 0xFF888888);
        }
        if (scrollOffset + VISIBLE_ITEMS < settingEntries.size()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("\u25BC Scroll down"), centerX, listY + listHeight + 2, 0xFF888888);
        }

        // Count display
        long lockedCount = settingEntries.stream().filter(e -> e.locked).count();
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(lockedCount + " of " + settingEntries.size() + " settings locked"),
                centerX, listY + listHeight + 34, 0xFFAAAAAA);

        // Tooltip for hovered item
        if (mouseX >= listX && mouseX <= listX + listWidth) {
            for (int i = 0; i < maxVisible; i++) {
                int itemY = listY + i * ITEM_HEIGHT;
                if (mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                    SettingEntry entry = settingEntries.get(scrollOffset + i);
                    context.drawCenteredTextWithShadow(this.textRenderer,
                            Text.literal(entry.description),
                            centerX, this.height - 60, 0xFF88AACC);
                    break;
                }
            }
        }

        // Draw progress bar
        drawProgressBar(context);

        super.render(context, mouseX, mouseY, delta);
    }

    // No @Override - signature varies between MC versions
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Check if clicking within the list
            int listHeight = VISIBLE_ITEMS * ITEM_HEIGHT;
            if (mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= listY && mouseY < listY + listHeight) {
                int relativeY = (int) mouseY - listY;
                int itemIndex = relativeY / ITEM_HEIGHT;
                int entryIndex = scrollOffset + itemIndex;

                if (entryIndex >= 0 && entryIndex < settingEntries.size()) {
                    settingEntries.get(entryIndex).locked = !settingEntries.get(entryIndex).locked;
                    return true;
                }
            }
        }
        return false;
    }

    // No @Override - signature varies between MC versions (3 args in 1.21, 4 args in 1.21.4+)
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listX && mouseX <= listX + listWidth) {
            if (verticalAmount > 0 && scrollOffset > 0) {
                scrollOffset--;
                return true;
            } else if (verticalAmount < 0 && scrollOffset + VISIBLE_ITEMS < settingEntries.size()) {
                scrollOffset++;
                return true;
            }
        }
        return false;
    }

    private void drawProgressBar(DrawContext context) {
        int barWidth = 200;
        int barHeight = 4;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height - 50;

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        float progress = manager.getProgress();
        int fillWidth = (int) (barWidth * progress);
        context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF44AA44);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // No @Override - signature varies between MC versions
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            manager.previousScreen();
            return true;
        }
        return false;
    }

    /** Draws a 1px border rectangle, compatible across all MC versions. */
    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);           // top
        context.fill(x, y + h - 1, x + w, y + h, color);   // bottom
        context.fill(x, y, x + 1, y + h, color);           // left
        context.fill(x + w - 1, y, x + w, y + h, color);   // right
    }

    private static class SettingEntry {
        final String key;
        final String name;
        final String description;
        boolean locked;

        SettingEntry(String key, String name, String description, boolean locked) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.locked = locked;
        }
    }
}
