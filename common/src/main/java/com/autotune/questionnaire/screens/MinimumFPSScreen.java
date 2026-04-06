package com.autotune.questionnaire.screens;

import com.autotune.questionnaire.PlayerPreferences;
import com.autotune.questionnaire.QuestionnaireManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * Screen 3: Minimum (floor) FPS selection.
 * Slider for floor FPS (minimum acceptable). Default = target/2.
 */
public class MinimumFPSScreen extends Screen {

    private static final Text TITLE = Text.translatable("autotune.questionnaire.minfps.title");
    private static final Text SUBTITLE = Text.translatable("autotune.questionnaire.minfps.subtitle");
    private static final Text EXPLANATION = Text.translatable("autotune.questionnaire.minfps.explanation");

    private static final int ABSOLUTE_MIN = 15;
    private static final int ABSOLUTE_MAX = 120;

    private final QuestionnaireManager manager;
    private int selectedFloorFps;
    private final int targetFps;
    private FloorFpsSliderWidget fpsSlider;

    public MinimumFPSScreen(QuestionnaireManager manager) {
        super(TITLE);
        this.manager = manager;

        PlayerPreferences prefs = manager.getPreferences();
        this.targetFps = prefs.getTargetFps();

        // Default floor is target/2, but respect previously set value
        int existingFloor = prefs.getFloorFps();
        if (existingFloor > 0 && existingFloor != 30) {
            this.selectedFloorFps = existingFloor;
        } else if (targetFps > 0) {
            this.selectedFloorFps = Math.max(ABSOLUTE_MIN, targetFps / 2);
        } else {
            // Unlimited target FPS: default floor to 30
            this.selectedFloorFps = 30;
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int maxSlider = targetFps > 0 ? Math.min(targetFps - 1, ABSOLUTE_MAX) : ABSOLUTE_MAX;

        // Floor FPS Slider
        fpsSlider = new FloorFpsSliderWidget(centerX - 100, 105, 200, 20,
                selectedFloorFps, ABSOLUTE_MIN, maxSlider);
        this.addDrawableChild(fpsSlider);

        // Quick preset buttons
        int presetY = 140;
        int[] presets;
        if (targetFps > 0) {
            presets = new int[]{ABSOLUTE_MIN, targetFps / 4, targetFps / 2, (int) (targetFps * 0.75)};
        } else {
            presets = new int[]{15, 30, 45, 60};
        }

        int presetBtnWidth = 50;
        int totalWidth = presets.length * presetBtnWidth + (presets.length - 1) * 4;
        int startX = centerX - totalWidth / 2;

        for (int i = 0; i < presets.length; i++) {
            final int fps = Math.clamp(presets[i], ABSOLUTE_MIN, maxSlider);
            int btnX = startX + i * (presetBtnWidth + 4);
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal(String.valueOf(fps)),
                    button -> {
                        selectedFloorFps = fps;
                        fpsSlider.setFpsValue(fps);
                    }
            ).dimensions(btnX, presetY, presetBtnWidth, 20).build());
        }

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
        prefs.setFloorFps(selectedFloorFps);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;

        // Header
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("AutoTune Setup - Step 3 of " + manager.getTotalScreens()),
                centerX, 10, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                TITLE, centerX, 25, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                SUBTITLE, centerX, 40, 0xFFCCCCCC);

        // Explanation
        context.drawCenteredTextWithShadow(this.textRenderer,
                EXPLANATION, centerX, 58, 0xFF88AACC);

        // Current value display
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Minimum acceptable: " + selectedFloorFps + " FPS"),
                centerX, 80, 0xFF44FF44);

        // Reference info
        String targetStr = targetFps > 0 ? targetFps + " FPS" : "Unlimited";
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Your target: " + targetStr),
                centerX, 175, 0xFF88AACC);

        // Warning if floor is very low
        if (selectedFloorFps < 20) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Warning: Very low minimum FPS may cause uncomfortable gameplay"),
                    centerX, 195, 0xFFFFAA44);
        }

        // Warning if floor is close to target
        if (targetFps > 0 && selectedFloorFps > targetFps * 0.8) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Note: Floor is close to target, limiting optimization range"),
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
     * Slider widget for floor FPS selection with configurable min/max.
     */
    private class FloorFpsSliderWidget extends SliderWidget {

        private final int minFps;
        private final int maxFps;

        public FloorFpsSliderWidget(int x, int y, int width, int height,
                                    int initialFps, int minFps, int maxFps) {
            super(x, y, width, height, Text.literal(initialFps + " FPS"),
                    (double) (initialFps - minFps) / (maxFps - minFps));
            this.minFps = minFps;
            this.maxFps = maxFps;
        }

        @Override
        protected void updateMessage() {
            int fps = getFpsFromSlider();
            this.setMessage(Text.literal(fps + " FPS"));
        }

        @Override
        protected void applyValue() {
            selectedFloorFps = getFpsFromSlider();
        }

        private int getFpsFromSlider() {
            return minFps + (int) Math.round(this.value * (maxFps - minFps));
        }

        public void setFpsValue(int fps) {
            this.value = (double) (fps - minFps) / (maxFps - minFps);
            this.value = Math.clamp(this.value, 0.0, 1.0);
            this.updateMessage();
        }
    }
}
