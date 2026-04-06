package com.autotune.ui.screens;

import com.autotune.AutoTuneMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Pre-benchmark screen that explains what the benchmark does,
 * offers Full (~15 min) vs Quick (~3 min) selection,
 * Start/Cancel buttons, and warns about gameplay interruption.
 */
public class BenchmarkIntroScreen extends Screen {

    private static final int BG_COLOR = 0xFF0F0F23;
    private static final int CARD_BG = 0xFF16213E;
    private static final int CARD_BORDER = 0xFF333355;
    private static final int TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_TEXT_COLOR = 0xFF888888;
    private static final int WARNING_COLOR = 0xFFF39C12;
    private static final int SELECTED_COLOR = 0xFF2ECC71;

    private final Screen parent;
    private boolean fullSelected = true; // Default to full benchmark

    public BenchmarkIntroScreen(Screen parent) {
        super(Text.literal("AutoTune Benchmark"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int cardWidth = Math.min(360, width - 40);
        int cardX = centerX - cardWidth / 2;
        int btnWidth = (cardWidth - 20) / 2;

        // Mode selection buttons
        int modeY = height / 2 - 10;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Full Benchmark"),
                btn -> fullSelected = true
        ).dimensions(cardX + 5, modeY, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Quick Benchmark"),
                btn -> fullSelected = false
        ).dimensions(cardX + 10 + btnWidth, modeY, btnWidth, 20).build());

        // Action buttons at bottom
        int actionY = height / 2 + 60;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Start Benchmark"),
                btn -> startBenchmark()
        ).dimensions(centerX - btnWidth - 5, actionY, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(
                Text.literal("Cancel"),
                btn -> close()
        ).dimensions(centerX + 5, actionY, btnWidth, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Full background
        context.fill(0, 0, width, height, BG_COLOR);

        int centerX = width / 2;
        int cardWidth = Math.min(360, width - 40);
        int cardHeight = 180;
        int cardX = centerX - cardWidth / 2;
        int cardY = height / 2 - cardHeight / 2 - 10;

        // Card background
        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, CARD_BG);
        drawBorder(context, cardX, cardY, cardWidth, cardHeight, CARD_BORDER);

        // Title
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("AutoTune Benchmark"),
                centerX, cardY + 8, TITLE_COLOR);

        // Explanation text
        int textY = cardY + 26;
        int lineHeight = 12;

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("The benchmark analyzes your system's performance"),
                centerX, textY, TEXT_COLOR);
        textY += lineHeight;
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("by cycling through graphics settings and measuring FPS."),
                centerX, textY, TEXT_COLOR);
        textY += lineHeight;
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("All settings will be restored afterward."),
                centerX, textY, DIM_TEXT_COLOR);

        // Mode description
        int descY = height / 2 + 16;
        if (fullSelected) {
            // Highlight the full button area
            context.fill(cardX + 3, height / 2 - 12, cardX + 5 + (cardWidth - 20) / 2, height / 2 + 10, 0x333498DB);

            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Full: Tests all 30 phases (~15 minutes)"),
                    centerX, descY, SELECTED_COLOR);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Most accurate results for optimal settings"),
                    centerX, descY + 12, DIM_TEXT_COLOR);
        } else {
            // Highlight the quick button area
            int qX = cardX + 10 + (cardWidth - 20) / 2;
            context.fill(qX - 2, height / 2 - 12, qX + (cardWidth - 20) / 2 + 2, height / 2 + 10, 0x333498DB);

            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Quick: Tests key phases (~3 minutes)"),
                    centerX, descY, SELECTED_COLOR);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Faster overview, good for repeat benchmarks"),
                    centerX, descY + 12, DIM_TEXT_COLOR);
        }

        // Warning
        int warningY = height / 2 + 42;
        context.fill(cardX + 10, warningY - 2, cardX + cardWidth - 10, warningY + 12, 0x44F39C12);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("! Gameplay will be interrupted during the benchmark"),
                centerX, warningY, WARNING_COLOR);

        // Render widgets on top
        super.render(context, mouseX, mouseY, delta);
    }

    private void startBenchmark() {
        // [CODE-REVIEW-FIX] Null guard for getInstance() and getBenchmarkRunner()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        var runner = mod.getBenchmarkRunner();
        if (runner == null) return;
        if (fullSelected) {
            runner.startFull();
        } else {
            runner.startQuick();
        }
        close();
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

    private void drawBorder(DrawContext context, int bx, int by, int bw, int bh, int color) {
        context.fill(bx, by, bx + bw, by + 1, color);
        context.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        context.fill(bx, by, bx + 1, by + bh, color);
        context.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }
}
