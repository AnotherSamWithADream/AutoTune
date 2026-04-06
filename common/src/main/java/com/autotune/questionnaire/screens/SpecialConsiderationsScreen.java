package com.autotune.questionnaire.screens;

import com.autotune.questionnaire.PlayerPreferences;
import com.autotune.questionnaire.QuestionnaireManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Screen 6: Special considerations.
 * Toggle buttons for laptop, thermal limit, recording, streaming.
 * Uses native ButtonWidget for cross-version compatibility.
 */
public class SpecialConsiderationsScreen extends Screen {

    private final QuestionnaireManager manager;
    private boolean isLaptop, limitThermals, recordsVideo, streams;

    public SpecialConsiderationsScreen(QuestionnaireManager manager) {
        super(Text.literal("Special Considerations"));
        this.manager = manager;
        PlayerPreferences prefs = manager.getPreferences();
        this.isLaptop = prefs.isLaptop();
        this.limitThermals = prefs.isLimitThermals();
        this.recordsVideo = prefs.isRecordsVideo();
        this.streams = prefs.isStreams();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int btnWidth = Math.min(width - 40, 320);
        int btnX = centerX - btnWidth / 2;
        int startY = 80;
        int spacing = 28;

        addDrawableChild(ButtonWidget.builder(checkText("I'm on a Laptop", isLaptop), btn -> {
            isLaptop = !isLaptop;
            btn.setMessage(checkText("I'm on a Laptop", isLaptop));
        }).dimensions(btnX, startY, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(checkText("Limit Thermals / Fan Noise", limitThermals), btn -> {
            limitThermals = !limitThermals;
            btn.setMessage(checkText("Limit Thermals / Fan Noise", limitThermals));
        }).dimensions(btnX, startY + spacing, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(checkText("I Record Gameplay Videos", recordsVideo), btn -> {
            recordsVideo = !recordsVideo;
            btn.setMessage(checkText("I Record Gameplay Videos", recordsVideo));
        }).dimensions(btnX, startY + spacing * 2, btnWidth, 20).build());

        addDrawableChild(ButtonWidget.builder(checkText("I Livestream", streams), btn -> {
            streams = !streams;
            btn.setMessage(checkText("I Livestream", streams));
        }).dimensions(btnX, startY + spacing * 3, btnWidth, 20).build());

        // Navigation
        int navY = height - 36;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> manager.previousScreen())
                .dimensions(centerX - 105, navY, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Next"), btn -> {
            saveAndNext();
        }).dimensions(centerX + 5, navY, 100, 20).build());
    }

    private Text checkText(String label, boolean checked) {
        return Text.literal((checked ? "\u2611 " : "\u2610 ") + label);
    }

    private void saveAndNext() {
        PlayerPreferences prefs = manager.getPreferences();
        prefs.setLaptop(isLaptop);
        prefs.setLimitThermals(limitThermals);
        prefs.setRecordsVideo(recordsVideo);
        prefs.setStreams(streams);
        manager.nextScreen();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xC0101010);
        int centerX = width / 2;

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("AutoTune Setup - Step 6 of " + manager.getTotalScreens()), centerX, 10, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Special Considerations"), centerX, 28, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Select any that apply to your setup"), centerX, 44, 0xFF3498DB);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("These help AutoTune make smarter decisions"), centerX, 60, 0xFF88AACC);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
