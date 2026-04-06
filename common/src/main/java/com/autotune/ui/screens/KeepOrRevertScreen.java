package com.autotune.ui.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Post-apply settings screen that asks "Keep these settings?" with a 30-second
 * countdown timer. If the timer expires, settings are automatically reverted.
 * Provides Keep and Revert buttons for manual selection.
 */
public class KeepOrRevertScreen extends Screen {

    private static final int BG_COLOR = 0xFF0F0F23;
    private static final int CARD_BG = 0xFF16213E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int WARNING_COLOR = 0xFFF39C12;
    private static final int DANGER_COLOR = 0xFFE74C3C;
    private static final int SUCCESS_COLOR = 0xFF2ECC71;
    private static final int BAR_BG = 0xFF2C2C2C;

    private static final int TIMEOUT_SECONDS = 30;

    private final Screen parent;
    private final Runnable revertAction;
    private long startTime;
    private boolean reverted;

    /**
     * Creates the keep-or-revert screen.
     *
     * @param parent       the screen to return to after making a choice
     * @param revertAction a runnable that reverts the settings to their prior values
     */
    public KeepOrRevertScreen(Screen parent, Runnable revertAction) {
        super(Text.literal("Keep Settings?"));
        this.parent = parent;
        this.revertAction = revertAction;
        this.reverted = false;
    }

    @Override
    protected void init() {
        startTime = System.currentTimeMillis();

        int centerX = width / 2;
        int btnWidth = 140;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Keep Settings"),
                btn -> keepSettings()
        ).dimensions(centerX - btnWidth - 5, height / 2 + 30, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Revert"),
                btn -> revertSettings()
        ).dimensions(centerX + 5, height / 2 + 30, btnWidth, 20).build());
    }

    @Override
    public void tick() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        if (elapsed >= TIMEOUT_SECONDS && !reverted) {
            revertSettings();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full background
        context.fill(0, 0, width, height, BG_COLOR);

        int centerX = width / 2;
        long elapsedMs = System.currentTimeMillis() - startTime;
        long remaining = Math.max(0, TIMEOUT_SECONDS - elapsedMs / 1000);
        float progress = (float) remaining / TIMEOUT_SECONDS;

        // Card
        int cardWidth = Math.min(340, width - 40);
        int cardHeight = 120;
        int cardX = centerX - cardWidth / 2;
        int cardY = height / 2 - cardHeight / 2;

        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, CARD_BG);

        // Border color changes as time runs out
        int borderColor = remaining > 10 ? CARD_BORDER : (remaining > 5 ? WARNING_COLOR : DANGER_COLOR);
        drawBorder(context, cardX, cardY, cardWidth, cardHeight, borderColor);

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Keep these settings?"),
                centerX, cardY + 10, TITLE_COLOR);

        // Instructions
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("If the screen looks wrong, wait for auto-revert."),
                centerX, cardY + 28, TEXT_COLOR);

        // Countdown text
        int countdownColor;
        if (remaining > 15) {
            countdownColor = TEXT_COLOR;
        } else if (remaining > 5) {
            countdownColor = WARNING_COLOR;
        } else {
            countdownColor = DANGER_COLOR;
        }
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(String.format("Auto-reverting in %d seconds...", remaining)),
                centerX, cardY + 46, countdownColor);

        // Progress bar
        int barWidth = cardWidth - 30;
        int barX = centerX - barWidth / 2;
        int barY = cardY + 62;
        int barHeight = 6;

        context.fill(barX, barY, barX + barWidth, barY + barHeight, BAR_BG);

        int fillWidth = (int) (barWidth * progress);
        if (fillWidth > 0) {
            int barColor;
            if (remaining > 15) {
                barColor = SUCCESS_COLOR;
            } else if (remaining > 5) {
                barColor = WARNING_COLOR;
            } else {
                barColor = DANGER_COLOR;
            }
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, barColor);
        }

        // Tick marks on progress bar
        for (int i = 1; i < 6; i++) {
            int tickX = barX + (int) (barWidth * ((double) i / 6));
            context.fill(tickX, barY, tickX + 1, barY + barHeight, 0x44FFFFFF);
        }

        // Render buttons and other widgets
        super.render(context, mouseX, mouseY, delta);
    }

    private void keepSettings() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void revertSettings() {
        if (!reverted) {
            reverted = true;
            if (revertAction != null) {
                revertAction.run();
            }
            if (client != null) {
                client.setScreen(parent);
            }
        }
    }

    @Override
    public void close() {
        // Closing without choosing = revert
        revertSettings();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }
}
