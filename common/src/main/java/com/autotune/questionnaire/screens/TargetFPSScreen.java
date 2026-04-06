package com.autotune.questionnaire.screens;

import com.autotune.benchmark.hardware.DisplayDetector;
import com.autotune.questionnaire.PlayerPreferences;
import com.autotune.questionnaire.QuestionnaireManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * Screen 2: Target FPS selection.
 * Slider 30-240 for target FPS with common presets and monitor refresh rate reference.
 */
public class TargetFPSScreen extends Screen {

    private static final Text TITLE = Text.translatable("autotune.questionnaire.targetfps.title");
    private static final Text SUBTITLE = Text.translatable("autotune.questionnaire.targetfps.subtitle");

    private static final int MIN_FPS = 30;
    private static final int MAX_FPS = 240;
    private static final int[] PRESETS = {30, 60, 120, 144, 165, 240};

    private final QuestionnaireManager manager;
    private int selectedFps;
    private boolean unlimitedSelected = false;
    private int monitorRefreshRate;
    private FpsSliderWidget fpsSlider;

    public TargetFPSScreen(QuestionnaireManager manager) {
        super(TITLE);
        this.manager = manager;
        this.selectedFps = manager.getPreferences().getTargetFps();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        // Detect monitor refresh rate
        monitorRefreshRate = DisplayDetector.detectRefreshRate(MinecraftClient.getInstance());

        // FPS Slider
        fpsSlider = new FpsSliderWidget(centerX - 100, 90, 200, 20, selectedFps);
        this.addDrawableChild(fpsSlider);

        // Preset buttons
        int presetY = 125;
        int presetBtnWidth = 50;
        int totalPresetsWidth = PRESETS.length * presetBtnWidth + (PRESETS.length - 1) * 4;
        int presetStartX = centerX - totalPresetsWidth / 2;

        for (int i = 0; i < PRESETS.length; i++) {
            final int fps = PRESETS[i];
            int btnX = presetStartX + i * (presetBtnWidth + 4);
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(String.valueOf(fps)),
                    button -> {
                        selectedFps = fps;
                        unlimitedSelected = false;
                        fpsSlider.setFpsValue(fps);
                    }
            ).dimensions(btnX, presetY, presetBtnWidth, 20).build());
        }

        // Unlimited button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.targetfps.unlimited"),
                button -> {
                    unlimitedSelected = !unlimitedSelected;
                    if (unlimitedSelected) {
                        selectedFps = 0;
                    } else {
                        selectedFps = 60;
                        fpsSlider.setFpsValue(60);
                    }
                }
        ).dimensions(centerX - 50, presetY + 26, 100, 20).build());

        // Navigation buttons
        int buttonY = this.height - 36;
        int buttonWidth = 100;

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.back"),
                button -> manager.previousScreen()
        ).dimensions(centerX - buttonWidth - 10, buttonY, buttonWidth, 20).build());

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
        prefs.setTargetFps(unlimitedSelected ? 0 : selectedFps);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;

        // Header
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("AutoTune Setup - Step 2 of " + manager.getTotalScreens()),
                centerX, 10, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                TITLE, centerX, 25, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                SUBTITLE, centerX, 40, 0xFFCCCCCC);

        // Current value display
        String fpsDisplay = unlimitedSelected ? "Unlimited" : selectedFps + " FPS";
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Target: " + fpsDisplay),
                centerX, 70, 0xFF44FF44);

        // Monitor refresh rate reference
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Your monitor: " + monitorRefreshRate + " Hz"),
                centerX, 180, 0xFF88AACC);

        if (!unlimitedSelected && selectedFps > monitorRefreshRate) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Note: Target exceeds your monitor refresh rate"),
                    centerX, 195, 0xFFFFAA44);
        }

        // Draw progress bar
        drawProgressBar(context);

        super.render(context, mouseX, mouseY, delta);
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

    /**
     * Custom slider widget for FPS selection.
     */
    private class FpsSliderWidget extends SliderWidget {

        public FpsSliderWidget(int x, int y, int width, int height, int initialFps) {
            super(x, y, width, height, Text.literal(initialFps + " FPS"),
                    (double) (initialFps - MIN_FPS) / (MAX_FPS - MIN_FPS));
        }

        @Override
        protected void updateMessage() {
            int fps = getFpsFromSlider();
            this.setMessage(Text.literal(fps + " FPS"));
        }

        @Override
        protected void applyValue() {
            selectedFps = getFpsFromSlider();
            unlimitedSelected = false;
        }

        private int getFpsFromSlider() {
            return MIN_FPS + (int) Math.round(this.value * (MAX_FPS - MIN_FPS));
        }

        public void setFpsValue(int fps) {
            this.value = (double) (fps - MIN_FPS) / (MAX_FPS - MIN_FPS);
            this.value = Math.clamp(this.value, 0.0, 1.0);
            this.updateMessage();
        }
    }
}
