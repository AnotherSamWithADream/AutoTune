package com.autotune.questionnaire.screens;

import com.autotune.questionnaire.PlayerPreferences;
import com.autotune.questionnaire.QuestionnaireManager;
import com.autotune.questionnaire.widgets.RankingWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Screen 1: Drag-to-rank 4 priorities (FPS, Visual Quality, Render Distance, Stability).
 * Uses a RankingWidget for reordering. Has Back and Next buttons.
 */
public class PriorityScreen extends Screen {

    private static final Text TITLE = Text.translatable("autotune.questionnaire.priority.title");
    private static final Text SUBTITLE = Text.translatable("autotune.questionnaire.priority.subtitle");
    private static final Text HINT = Text.translatable("autotune.questionnaire.priority.hint");

    private final QuestionnaireManager manager;
    private RankingWidget rankingWidget;

    public PriorityScreen(QuestionnaireManager manager) {
        super(TITLE);
        this.manager = manager;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int contentWidth = 260;
        int widgetX = centerX - contentWidth / 2;

        // Load current order from preferences
        PlayerPreferences prefs = manager.getPreferences();
        String[] currentOrder = prefs.getRankOrder();

        // Create ranking widget
        rankingWidget = new RankingWidget(widgetX, 80, contentWidth, currentOrder);
        this.addDrawableChild(rankingWidget);

        // Navigation buttons
        int buttonY = this.height - 36;
        int buttonWidth = 100;

        // Back button (cancels on first screen)
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.cancel"),
                button -> manager.cancel()
        ).dimensions(centerX - buttonWidth - 10, buttonY, buttonWidth, 20).build());

        // Next button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.next"),
                button -> {
                    saveSelections();
                    manager.nextScreen();
                }
        ).dimensions(centerX + 10, buttonY, buttonWidth, 20).build());
    }

    private void saveSelections() {
        PlayerPreferences prefs = manager.getPreferences();
        prefs.setRanksFromOrder(rankingWidget.getOrder());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Use solid fill instead of renderBackground() — MC 1.21.11 throws
        // "Can only blur once per frame" if renderBackground applies blur twice
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("AutoTune Setup - Step 1 of " + manager.getTotalScreens()),
                this.width / 2, 10, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                TITLE, this.width / 2, 25, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                SUBTITLE, this.width / 2, 40, 0xFFCCCCCC);

        // Draw hint text
        context.drawCenteredTextWithShadow(this.textRenderer,
                HINT, this.width / 2, 60, 0xFF88AACC);

        // Draw progress bar
        drawProgressBar(context);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawProgressBar(DrawContext context) {
        int barWidth = 200;
        int barHeight = 4;
        int barX = this.width / 2 - barWidth / 2;
        int barY = this.height - 50;

        // Background
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        // Progress fill
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
        // Escape goes back / cancels
        if (keyCode == 256) {
            manager.cancel();
            return true;
        }
        return false;
    }
}
