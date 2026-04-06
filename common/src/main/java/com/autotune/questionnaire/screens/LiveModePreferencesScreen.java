package com.autotune.questionnaire.screens;

import com.autotune.questionnaire.PlayerPreferences;
import com.autotune.questionnaire.PlayerPreferences.AdjustmentVisibility;
import com.autotune.questionnaire.PlayerPreferences.LiveModePref;
import com.autotune.questionnaire.QuestionnaireManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Screen 8: Live mode preferences.
 * Radio for live mode pref, adjustment visibility, and render distance permission.
 * This is the final screen - "Finish" button instead of "Next".
 */
public class LiveModePreferencesScreen extends Screen {

    private static final Text TITLE = Text.translatable("autotune.questionnaire.livemode.title");
    private static final Text SUBTITLE = Text.translatable("autotune.questionnaire.livemode.subtitle");

    private final QuestionnaireManager manager;
    private LiveModePref selectedLiveMode;
    private AdjustmentVisibility selectedVisibility;
    private boolean allowRenderDistance;

    // Live mode radio buttons
    private ButtonWidget fullModeBtn;
    private ButtonWidget conservativeModeBtn;
    private ButtonWidget staticModeBtn;

    // Visibility radio buttons
    private ButtonWidget stableFpsBtn;
    private ButtonWidget balancedBtn;
    private ButtonWidget doWhateverBtn;

    // Render distance toggle
    private ButtonWidget renderDistBtn;

    public LiveModePreferencesScreen(QuestionnaireManager manager) {
        super(TITLE);
        this.manager = manager;

        PlayerPreferences prefs = manager.getPreferences();
        this.selectedLiveMode = prefs.getLiveModePref();
        this.selectedVisibility = prefs.getAdjustVisibility();
        this.allowRenderDistance = prefs.isAllowLiveRenderDistance();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int buttonWidth = Math.min(220, width - 40);
        int buttonX = centerX - buttonWidth / 2;

        // Adaptive layout: 8 buttons + nav must fit between Y=56 and height-28
        int navY = height - 36;
        int contentTop = 56;
        int availableHeight = navY - contentTop - 4;
        // 8 interactive buttons total, need spacing between groups
        int btnSpacing = Math.min(22, (availableHeight - 20) / 8);

        int y = contentTop;

        // --- Live Mode Section (3 buttons) ---
        fullModeBtn = addDrawableChild(ButtonWidget.builder(
                getLiveModeRadioText(LiveModePref.FULL),
                button -> selectLiveMode(LiveModePref.FULL)
        ).dimensions(buttonX, y, buttonWidth, 20).build());
        y += btnSpacing;

        conservativeModeBtn = addDrawableChild(ButtonWidget.builder(
                getLiveModeRadioText(LiveModePref.CONSERVATIVE),
                button -> selectLiveMode(LiveModePref.CONSERVATIVE)
        ).dimensions(buttonX, y, buttonWidth, 20).build());
        y += btnSpacing;

        staticModeBtn = addDrawableChild(ButtonWidget.builder(
                getLiveModeRadioText(LiveModePref.STATIC),
                button -> selectLiveMode(LiveModePref.STATIC)
        ).dimensions(buttonX, y, buttonWidth, 20).build());
        y += btnSpacing + 4; // gap before next section

        // --- Adjustment Visibility Section (3 buttons) ---
        stableFpsBtn = addDrawableChild(ButtonWidget.builder(
                getVisibilityRadioText(AdjustmentVisibility.PREFER_STABLE_FPS),
                button -> selectVisibility(AdjustmentVisibility.PREFER_STABLE_FPS)
        ).dimensions(buttonX, y, buttonWidth, 20).build());
        y += btnSpacing;

        balancedBtn = addDrawableChild(ButtonWidget.builder(
                getVisibilityRadioText(AdjustmentVisibility.BALANCED),
                button -> selectVisibility(AdjustmentVisibility.BALANCED)
        ).dimensions(buttonX, y, buttonWidth, 20).build());
        y += btnSpacing;

        doWhateverBtn = addDrawableChild(ButtonWidget.builder(
                getVisibilityRadioText(AdjustmentVisibility.DO_WHATEVER),
                button -> selectVisibility(AdjustmentVisibility.DO_WHATEVER)
        ).dimensions(buttonX, y, buttonWidth, 20).build());
        y += btnSpacing + 4; // gap before checkbox

        // --- Render Distance Permission (1 checkbox) ---
        renderDistBtn = addDrawableChild(ButtonWidget.builder(
                getRenderDistText(),
                button -> {
                    allowRenderDistance = !allowRenderDistance;
                    renderDistBtn.setMessage(getRenderDistText());
                }
        ).dimensions(buttonX, y, buttonWidth, 20).build());

        updateVisibilitySectionState();

        // Navigation buttons at bottom
        int buttonY = navY;
        int navBtnWidth = 100;

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.back"),
                button -> manager.previousScreen()
        ).dimensions(centerX - navBtnWidth - 10, buttonY, navBtnWidth, 20).build());

        // Finish button (last screen)
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.finish"),
                button -> {
                    saveSelections();
                    manager.onComplete(manager.getPreferences());
                }
        ).dimensions(centerX + 10, buttonY, navBtnWidth, 20).build());
    }

    private void selectLiveMode(LiveModePref mode) {
        selectedLiveMode = mode;
        updateLiveModeButtons();
        updateVisibilitySectionState();
    }

    private void selectVisibility(AdjustmentVisibility vis) {
        selectedVisibility = vis;
        updateVisibilityButtons();
    }

    private void updateLiveModeButtons() {
        fullModeBtn.setMessage(getLiveModeRadioText(LiveModePref.FULL));
        conservativeModeBtn.setMessage(getLiveModeRadioText(LiveModePref.CONSERVATIVE));
        staticModeBtn.setMessage(getLiveModeRadioText(LiveModePref.STATIC));
    }

    private void updateVisibilityButtons() {
        stableFpsBtn.setMessage(getVisibilityRadioText(AdjustmentVisibility.PREFER_STABLE_FPS));
        balancedBtn.setMessage(getVisibilityRadioText(AdjustmentVisibility.BALANCED));
        doWhateverBtn.setMessage(getVisibilityRadioText(AdjustmentVisibility.DO_WHATEVER));
    }

    private void updateVisibilitySectionState() {
        boolean enabled = selectedLiveMode != LiveModePref.STATIC;
        stableFpsBtn.active = enabled;
        balancedBtn.active = enabled;
        doWhateverBtn.active = enabled;
        renderDistBtn.active = enabled;
    }

    private Text getLiveModeRadioText(LiveModePref mode) {
        String prefix = (selectedLiveMode == mode) ? "\u25C9 " : "\u25CB ";
        return Text.literal(prefix + mode.getDisplayName());
    }

    private Text getVisibilityRadioText(AdjustmentVisibility vis) {
        String prefix = (selectedVisibility == vis) ? "\u25C9 " : "\u25CB ";
        return Text.literal(prefix + vis.getDisplayName());
    }

    private Text getRenderDistText() {
        String prefix = allowRenderDistance ? "\u2611 " : "\u2610 ";
        return Text.literal(prefix + "Allow live render distance adjustments");
    }

    private void saveSelections() {
        PlayerPreferences prefs = manager.getPreferences();
        prefs.setLiveModePref(selectedLiveMode);
        prefs.setAdjustVisibility(selectedVisibility);
        prefs.setAllowLiveRenderDistance(allowRenderDistance);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;

        // Header only — buttons handle the interactive content
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("AutoTune Setup - Step 8 of " + manager.getTotalScreens()),
                centerX, 6, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                TITLE, centerX, 18, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                SUBTITLE, centerX, 32, 0xFFCCCCCC);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Live Adjustment Mode"), centerX, 46, 0xFFFFAA00);

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
}
