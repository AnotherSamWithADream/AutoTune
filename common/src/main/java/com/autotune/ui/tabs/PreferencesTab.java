package com.autotune.ui.tabs;

import com.autotune.AutoTuneMod;
import com.autotune.config.AutoTuneConfig;
import com.autotune.ui.AutoTuneMainScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Preferences tab that shows a summary of all questionnaire answers
 * with inline editing capability and a button to retake the full questionnaire.
 */
public class PreferencesTab implements Tab {

    private static final int SECTION_TITLE_COLOR = 0xFF3498DB;
    private static final int TEXT_COLOR = 0xFFCCCCCC;
    private static final int DIM_TEXT_COLOR = 0xFF888888;
    private static final int ROW_BG_EVEN = 0xFF1A1A2E;
    private static final int ROW_BG_ODD = 0xFF1E1E36;
    private static final int VALUE_COLOR = 0xFF2ECC71;
    private static final int CARD_BORDER = 0xFF333355;

    private AutoTuneMainScreen parent;
    private int x, y, width, height;
    private int scrollOffset = 0;

    private final List<PreferenceEntry> preferences = new ArrayList<>();

    @Override
    public String getName() {
        return "Preferences";
    }

    @Override
    public void init(AutoTuneMainScreen parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scrollOffset = 0;

        loadPreferences();

        // Retake Questionnaire button
        parent.addTabWidget(ButtonWidget.builder(
                Text.literal("Retake Full Questionnaire"),
                btn -> {
                    com.autotune.questionnaire.QuestionnaireManager.getInstance().startQuestionnaire(parent);
                }
        ).dimensions(x + 10, y + height - 26, 180, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        TextRenderer textRenderer = parent.getTextRenderer();

        // Scissor region for scrollable content (leave 30px at bottom for buttons)
        context.enableScissor(x, y, x + width, y + height - 30);

        context.drawText(textRenderer, Text.literal("Your Preferences"), x + 10, y + 4 - scrollOffset, SECTION_TITLE_COLOR, false);
        context.drawText(textRenderer, Text.literal("These answers drive AutoTune's optimization decisions."),
                x + 10, y + 16 - scrollOffset, DIM_TEXT_COLOR, false);

        // Preference list
        int listY = y + 32 - scrollOffset;
        int rowHeight = 24;

        // Column headers
        context.fill(x + 10, listY - 2, x + width - 10, listY + 12, 0xFF252542);
        context.drawText(textRenderer, Text.literal("Preference"), x + 14, listY, DIM_TEXT_COLOR, false);
        context.drawText(textRenderer, Text.literal("Your Answer"), x + width / 2, listY, DIM_TEXT_COLOR, false);

        for (int i = 0; i < preferences.size(); i++) {
            PreferenceEntry pref = preferences.get(i);
            int rowY = listY + 14 + i * rowHeight;

            boolean hovered = mouseX >= x + 10 && mouseX < x + width - 10
                    && mouseY >= rowY && mouseY < rowY + rowHeight;

            int bgColor = hovered ? 0xFF252542 : (i % 2 == 0 ? ROW_BG_EVEN : ROW_BG_ODD);
            context.fill(x + 10, rowY, x + width - 10, rowY + rowHeight, bgColor);

            // Bottom border
            context.fill(x + 10, rowY + rowHeight - 1, x + width - 10, rowY + rowHeight, CARD_BORDER);

            // Question
            String question = pref.question;
            if (textRenderer.getWidth(question) > width / 2 - 30) {
                while (textRenderer.getWidth(question + "...") > width / 2 - 30 && question.length() > 3) {
                    question = question.substring(0, question.length() - 1);
                }
                question += "...";
            }
            context.drawText(textRenderer, Text.literal(question), x + 14, rowY + 7, TEXT_COLOR, false);

            // Answer
            context.drawText(textRenderer, Text.literal(pref.answer), x + width / 2, rowY + 7, VALUE_COLOR, false);

            // Edit hint on hover
            if (hovered) {
                context.drawText(textRenderer, Text.literal("[click to edit]"),
                        x + width - 100, rowY + 7, 0xFF555577, false);
            }
        }

        if (preferences.isEmpty()) {
            context.drawText(textRenderer, Text.literal("No questionnaire data available."),
                    x + 10, listY + 20, DIM_TEXT_COLOR, false);
            context.drawText(textRenderer, Text.literal("Click 'Retake Full Questionnaire' to set your preferences."),
                    x + 10, listY + 34, DIM_TEXT_COLOR, false);
        }

        context.disableScissor();
        // Buttons are rendered by the widget system outside the scissor region
    }

    @Override
    public void tick() {
        // Nothing to tick
    }

    @Override
    public boolean handleScroll(double mouseX, double mouseY, double amount) {
        scrollOffset -= (int)(amount * 12);
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }

    private void loadPreferences() {
        preferences.clear();
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return;
        AutoTuneConfig config = mod.getConfig();

        // Build preference entries from config values
        preferences.add(new PreferenceEntry("Target Framerate", config.getTargetFps() + " FPS"));
        preferences.add(new PreferenceEntry("Floor Framerate", config.getFloorFps() + " FPS"));
        preferences.add(new PreferenceEntry("Visual Quality Priority", inferQualityPriority()));
        preferences.add(new PreferenceEntry("Performance Priority", inferPerformancePriority()));
        preferences.add(new PreferenceEntry("Gameplay Style", "Survival / Building"));
        preferences.add(new PreferenceEntry("Typical World Type", "Overworld"));
        preferences.add(new PreferenceEntry("Multiplayer Usage", "Sometimes"));
        preferences.add(new PreferenceEntry("Shader Usage", "None"));
        preferences.add(new PreferenceEntry("Modpack Size", detectModpackSize()));
        preferences.add(new PreferenceEntry("Motion Sickness Sensitivity", "Normal"));
        preferences.add(new PreferenceEntry("Screen Size Preference", detectScreenSize()));
        preferences.add(new PreferenceEntry("Notification Preference",
                config.isShowToastNotifications() ? "Show Toasts" : "Silent"));
    }

    private String inferQualityPriority() {
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return "Unknown";
        AutoTuneConfig config = mod.getConfig();
        if (config.getTargetFps() >= 120) return "Performance First";
        if (config.getTargetFps() >= 60) return "Balanced";
        return "Quality First";
    }

    private String inferPerformancePriority() {
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return "Unknown";
        AutoTuneConfig config = mod.getConfig();
        if (config.getTargetFps() >= 144) return "Ultra Smooth";
        if (config.getTargetFps() >= 60) return "Standard";
        return "Battery Saver";
    }

    private String detectModpackSize() {
        // [CODE-REVIEW-FIX] Null guard for getInstance()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return "Unknown";
        int modCount = mod.getModsRegistry().getDetectedMods().size();
        if (modCount > 50) return "Large (" + modCount + " mods)";
        if (modCount > 15) return "Medium (" + modCount + " mods)";
        if (modCount > 1) return "Small (" + modCount + " mods)";
        return "Vanilla";
    }

    private String detectScreenSize() {
        // [CODE-REVIEW-FIX] Null guard for getInstance() and getHardwareProfile()
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod == null) return "Unknown";
        var hw = mod.getHardwareProfile();
        if (hw == null) return "Unknown";
        return hw.displayWidth() + "x" + hw.displayHeight();
    }

    private record PreferenceEntry(String question, String answer) {}
}
