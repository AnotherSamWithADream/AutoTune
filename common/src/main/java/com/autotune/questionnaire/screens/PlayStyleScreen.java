package com.autotune.questionnaire.screens;

import com.autotune.questionnaire.PlayerPreferences;
import com.autotune.questionnaire.PlayerPreferences.PlayStyle;
import com.autotune.questionnaire.QuestionnaireManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Screen 5: Play style selection with radio buttons and activity toggles.
 * Uses native ButtonWidget for ALL interactions to ensure cross-version compatibility.
 */
public class PlayStyleScreen extends Screen {

    private final QuestionnaireManager manager;
    private PlayStyle selectedStyle;
    private boolean usesElytra, usesRedstone, usesMobFarms;
    private ButtonWidget[] styleButtons;
    private ButtonWidget elytraBtn, redstoneBtn, mobFarmsBtn;

    public PlayStyleScreen(QuestionnaireManager manager) {
        super(Text.literal("Play Style"));
        this.manager = manager;
        PlayerPreferences prefs = manager.getPreferences();
        this.selectedStyle = prefs.getPlayStyle();
        this.usesElytra = prefs.isUsesElytra();
        this.usesRedstone = prefs.isUsesRedstone();
        this.usesMobFarms = prefs.isUsesMobFarms();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int btnWidth = (width - 60) / 2;
        if (btnWidth > 220) btnWidth = 220;

        // Reserve space: nav buttons at bottom, content fills the rest
        int navY = height - 36;
        int contentTop = 68;
        int contentHeight = navY - contentTop - 6;

        // Play style radio buttons — 2-column grid, compact spacing
        PlayStyle[] styles = PlayStyle.values();
        styleButtons = new ButtonWidget[styles.length];
        int rows = (styles.length + 1) / 2;
        int radioSpacing = Math.min(24, contentHeight / (rows + 4)); // Scale spacing to fit

        for (int i = 0; i < styles.length; i++) {
            final PlayStyle style = styles[i];
            int col = i % 2;
            int row = i / 2;
            int btnX = centerX - btnWidth - 5 + col * (btnWidth + 10);
            int btnY = contentTop + row * radioSpacing;

            styleButtons[i] = addDrawableChild(ButtonWidget.builder(
                    getRadioText(style),
                    btn -> {
                        selectedStyle = style;
                        updateRadioButtons();
                    }
            ).dimensions(btnX, btnY, btnWidth, 20).build());
        }

        // Activity toggles below play style buttons
        int actY = contentTop + rows * radioSpacing + 16;
        int toggleWidth = Math.min(width - 40, 300);
        int toggleX = centerX - toggleWidth / 2;
        int toggleSpacing = Math.min(22, (navY - actY - 10) / 3);

        elytraBtn = addDrawableChild(ButtonWidget.builder(
                getCheckText("Elytra Flying", usesElytra),
                btn -> { usesElytra = !usesElytra; btn.setMessage(getCheckText("Elytra Flying", usesElytra)); }
        ).dimensions(toggleX, actY, toggleWidth, 20).build());

        redstoneBtn = addDrawableChild(ButtonWidget.builder(
                getCheckText("Redstone Builds", usesRedstone),
                btn -> { usesRedstone = !usesRedstone; btn.setMessage(getCheckText("Redstone Builds", usesRedstone)); }
        ).dimensions(toggleX, actY + toggleSpacing, toggleWidth, 20).build());

        mobFarmsBtn = addDrawableChild(ButtonWidget.builder(
                getCheckText("Mob Farms", usesMobFarms),
                btn -> { usesMobFarms = !usesMobFarms; btn.setMessage(getCheckText("Mob Farms", usesMobFarms)); }
        ).dimensions(toggleX, actY + toggleSpacing * 2, toggleWidth, 20).build());

        // Navigation at the very bottom
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> manager.previousScreen())
                .dimensions(centerX - 105, navY, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"), btn -> { saveAndNext(); })
                .dimensions(centerX + 5, navY, 100, 20).build());
    }

    private void updateRadioButtons() {
        PlayStyle[] styles = PlayStyle.values();
        for (int i = 0; i < styles.length && i < styleButtons.length; i++) {
            styleButtons[i].setMessage(getRadioText(styles[i]));
        }
    }

    private Text getRadioText(PlayStyle style) {
        return Text.literal((selectedStyle == style ? "\u25C9 " : "\u25CB ") + style.getDisplayName());
    }

    private Text getCheckText(String label, boolean checked) {
        return Text.literal((checked ? "\u2611 " : "\u2610 ") + label);
    }

    private void saveAndNext() {
        PlayerPreferences prefs = manager.getPreferences();
        prefs.setPlayStyle(selectedStyle);
        prefs.setUsesElytra(usesElytra);
        prefs.setUsesRedstone(usesRedstone);
        prefs.setUsesMobFarms(usesMobFarms);
        manager.nextScreen();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101010);

        int centerX = width / 2;
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("AutoTune Setup - Step 5 of " + manager.getTotalScreens()), centerX, 10, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Play Style"), centerX, 28, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("This helps AutoTune prioritize the right settings"), centerX, 44, 0xFF3498DB);

        // Description of selected style
        String desc = switch (selectedStyle) {
            case COMPETITIVE_PVP -> "Maximum FPS, minimal visual effects";
            case BUILDING -> "High visual quality for building and screenshots";
            case EXPLORATION -> "Balanced with focus on render distance";
            case REDSTONE -> "CPU-focused optimization for redstone";
            case SURVIVAL -> "Well-rounded settings for general survival gameplay";
            case MIXED -> "Balanced approach for varied gameplay";
        };
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(desc), centerX, 58, 0xFFF39C12);

        PlayStyle[] styles = PlayStyle.values();
        int actLabelY = 75 + ((styles.length + 1) / 2) * 26 + 8;
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Special Activities"), centerX, actLabelY, 0xFF3498DB);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
