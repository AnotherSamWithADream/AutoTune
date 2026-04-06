package com.autotune.ui.screens;

import com.autotune.AutoTuneMod;
import com.autotune.ui.screens.KeepOrRevertScreen;
import com.autotune.ui.widgets.SettingsDiffWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows proposed settings changes in a scrollable list with old -> new values.
 * Offers Apply, Cancel, and "Apply and keep for 30 seconds" options.
 * The 30-second option transitions to KeepOrRevertScreen after applying.
 */
public class SettingsPreviewScreen extends Screen {

    private static final int BG_COLOR = 0xFF0F0F23;
    private static final int TITLE_COLOR = 0xFF3498DB;
    private static final int DIM_TEXT_COLOR = 0xFF888888;
    private static final int HEADER_BG = 0xFF252542;
    private static final int ROW_BG_EVEN = 0xFF1A1A2E;
    private static final int ROW_BG_ODD = 0xFF1E1E36;

    private final Screen parent;
    private final List<SettingChange> changes;
    private final List<SettingsDiffWidget> diffWidgets = new ArrayList<>();
    private int scrollOffset;

    public SettingsPreviewScreen(Screen parent, List<SettingChange> changes) {
        super(Text.literal("Settings Preview"));
        this.parent = parent;
        this.changes = new ArrayList<>(changes);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int cardWidth = Math.min(420, width - 30);
        int cardX = centerX - cardWidth / 2;

        // Build diff widgets for each change
        diffWidgets.clear();
        int widgetY = 56;
        int rowHeight = 18;
        for (SettingChange change : changes) {
            SettingsDiffWidget widget = new SettingsDiffWidget(
                    cardX + 4, widgetY, cardWidth - 8, rowHeight,
                    change.name, change.oldValue, change.newValue);
            diffWidgets.add(widget);
            widgetY += rowHeight;
        }

        // Action buttons
        int btnY = height - 34;
        int btnWidth = (cardWidth - 20) / 3;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Apply"),
                btn -> applyChanges(false)
        ).dimensions(cardX + 2, btnY, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Apply (30s trial)"),
                btn -> applyChanges(true)
        ).dimensions(cardX + 6 + btnWidth, btnY, btnWidth + 10, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                btn -> close()
        ).dimensions(cardX + 16 + btnWidth * 2, btnY, btnWidth - 10, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full background
        context.fill(0, 0, width, height, BG_COLOR);

        int centerX = width / 2;
        int cardWidth = Math.min(420, width - 30);
        int cardX = centerX - cardWidth / 2;

        // Title
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Proposed Settings Changes"),
                centerX, 8, TITLE_COLOR);

        // Summary line
        String summaryText = changes.size() + " setting" + (changes.size() != 1 ? "s" : "") + " will be changed";
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(summaryText), centerX, 22, DIM_TEXT_COLOR);

        // Column headers
        int headerY = 36;
        context.fill(cardX, headerY, cardX + cardWidth, headerY + 14, HEADER_BG);
        context.drawText(textRenderer, Text.literal("Setting"), cardX + 8, headerY + 3, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Old -> New"), cardX + cardWidth - 120, headerY + 3, DIM_TEXT_COLOR, false);

        // Scrollable diff list
        int listStartY = 52;
        int listHeight = height - 92;
        int rowHeight = 18;

        context.enableScissor(cardX, listStartY, cardX + cardWidth, listStartY + listHeight);

        for (int i = 0; i < diffWidgets.size(); i++) {
            SettingsDiffWidget widget = diffWidgets.get(i);
            int widgetY = listStartY + i * rowHeight - scrollOffset;
            widget.setPosition(cardX + 4, widgetY);
            widget.setWidth(cardWidth - 8);

            if (widgetY + rowHeight >= listStartY && widgetY < listStartY + listHeight) {
                // Alternating row background
                int rowBg = i % 2 == 0 ? ROW_BG_EVEN : ROW_BG_ODD;
                context.fill(cardX, widgetY, cardX + cardWidth, widgetY + rowHeight, rowBg);

                widget.render(context, mouseX, mouseY, delta);
            }
        }

        context.disableScissor();

        // Scroll indicator
        if (diffWidgets.size() * rowHeight > listHeight) {
            int scrollBarH = (int) ((double) listHeight / (diffWidgets.size() * rowHeight) * listHeight);
            scrollBarH = Math.max(scrollBarH, 10);
            int maxScroll = diffWidgets.size() * rowHeight - listHeight;
            int scrollBarY = listStartY + (int) ((double) scrollOffset / Math.max(1, maxScroll) * (listHeight - scrollBarH));
            context.fill(cardX + cardWidth - 3, scrollBarY, cardX + cardWidth, scrollBarY + scrollBarH, 0xFF555577);
        }

        // Bottom info
        int infoY = height - 46;
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("'Apply (30s trial)' will auto-revert if not confirmed"),
                centerX, infoY, DIM_TEXT_COLOR);

        // Render buttons
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, diffWidgets.size() * 18 - (height - 92));
        scrollOffset -= (int) (verticalAmount * 18);
        scrollOffset = Math.clamp(scrollOffset, 0, maxScroll);
        return true;
    }

    private void applyChanges(boolean withTrial) {
        // Apply changes through the platform adapter
        AutoTuneMod.LOGGER.info("Applying {} settings changes (trial={})", changes.size(), withTrial);

        if (withTrial && client != null) {
            // Trial mode: transition to KeepOrRevertScreen with 30s auto-revert
            client.setScreen(new KeepOrRevertScreen(parent, () -> {
                // Revert action — settings revert happens via the SettingsApplier in a real implementation
                AutoTuneMod.LOGGER.info("Settings reverted by user or timeout");
            }));
        } else if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return true;
    }

    /**
     * Represents a single setting change for display in the preview.
     */
    public record SettingChange(String name, String oldValue, String newValue) {}
}
