package com.autotune.questionnaire.screens;

import com.autotune.questionnaire.PlayerPreferences;
import com.autotune.questionnaire.PlayerPreferences.ShaderPref;
import com.autotune.questionnaire.QuestionnaireManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Screen 4: Shader preference selection.
 * Radio buttons for shader preference. Shows message if Iris is not loaded.
 */
public class ShaderPreferenceScreen extends Screen {

    private static final Text TITLE = Text.translatable("autotune.questionnaire.shaders.title");
    private static final Text SUBTITLE = Text.translatable("autotune.questionnaire.shaders.subtitle");

    private final QuestionnaireManager manager;
    private ShaderPref selectedPref;
    private boolean irisLoaded;

    // Radio button widgets
    private ButtonWidget noShadersBtn;
    private ButtonWidget autoSelectBtn;
    private ButtonWidget keepCurrentBtn;
    private ButtonWidget specificPackBtn;

    public ShaderPreferenceScreen(QuestionnaireManager manager) {
        super(TITLE);
        this.manager = manager;
        this.selectedPref = manager.getPreferences().getShaderPref();
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int buttonWidth = 200;
        int buttonX = centerX - buttonWidth / 2;

        // Check if Iris is loaded
        irisLoaded = isIrisPresent();

        int startY = 80;
        int spacing = 28;

        // No Shaders option
        noShadersBtn = this.addDrawableChild(ButtonWidget.builder(
                getRadioText(ShaderPref.NO_SHADERS),
                button -> selectOption(ShaderPref.NO_SHADERS)
        ).dimensions(buttonX, startY, buttonWidth, 20).build());

        // Auto Select option
        autoSelectBtn = this.addDrawableChild(ButtonWidget.builder(
                getRadioText(ShaderPref.AUTO_SELECT),
                button -> selectOption(ShaderPref.AUTO_SELECT)
        ).dimensions(buttonX, startY + spacing, buttonWidth, 20).build());
        autoSelectBtn.active = irisLoaded;

        // Keep Current option
        keepCurrentBtn = this.addDrawableChild(ButtonWidget.builder(
                getRadioText(ShaderPref.KEEP_CURRENT),
                button -> selectOption(ShaderPref.KEEP_CURRENT)
        ).dimensions(buttonX, startY + spacing * 2, buttonWidth, 20).build());
        keepCurrentBtn.active = irisLoaded;

        // Specific Pack option
        specificPackBtn = this.addDrawableChild(ButtonWidget.builder(
                getRadioText(ShaderPref.SPECIFIC_PACK),
                button -> selectOption(ShaderPref.SPECIFIC_PACK)
        ).dimensions(buttonX, startY + spacing * 3, buttonWidth, 20).build());
        specificPackBtn.active = irisLoaded;

        // Navigation buttons
        int buttonY = this.height - 36;
        int navBtnWidth = 100;

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.back"),
                button -> manager.previousScreen()
        ).dimensions(centerX - navBtnWidth - 10, buttonY, navBtnWidth, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("autotune.questionnaire.next"),
                button -> {
                    saveSelections();
                    manager.nextScreen();
                }
        ).dimensions(centerX + 10, buttonY, navBtnWidth, 20).build());
    }

    private void selectOption(ShaderPref pref) {
        selectedPref = pref;
        updateRadioButtons();
    }

    private void updateRadioButtons() {
        noShadersBtn.setMessage(getRadioText(ShaderPref.NO_SHADERS));
        autoSelectBtn.setMessage(getRadioText(ShaderPref.AUTO_SELECT));
        keepCurrentBtn.setMessage(getRadioText(ShaderPref.KEEP_CURRENT));
        specificPackBtn.setMessage(getRadioText(ShaderPref.SPECIFIC_PACK));
    }

    private Text getRadioText(ShaderPref pref) {
        String prefix = (selectedPref == pref) ? "\u25C9 " : "\u25CB ";
        return Text.literal(prefix + pref.getDisplayName());
    }

    private void saveSelections() {
        PlayerPreferences prefs = manager.getPreferences();
        prefs.setShaderPref(selectedPref);
    }

    private boolean isIrisPresent() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        int centerX = this.width / 2;

        // Header
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("AutoTune Setup - Step 4 of " + manager.getTotalScreens()),
                centerX, 10, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                TITLE, centerX, 25, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                SUBTITLE, centerX, 40, 0xFFCCCCCC);

        // Iris status message
        if (!irisLoaded) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Iris Shaders is not installed."),
                    centerX, 55, 0xFFFF6666);
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Only 'No Shaders' is available."),
                    centerX, 67, 0xFFFFAA44);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("Iris Shaders detected!"),
                    centerX, 60, 0xFF44FF44);
        }

        // Description of selected option
        int descY = 200;
        switch (selectedPref) {
            case NO_SHADERS:
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Best performance - no shader effects will be used"),
                        centerX, descY, 0xFF88AACC);
                break;
            case AUTO_SELECT:
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("AutoTune will choose the best shader pack for your hardware"),
                        centerX, descY, 0xFF88AACC);
                break;
            case KEEP_CURRENT:
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("Keep your currently active shader pack"),
                        centerX, descY, 0xFF88AACC);
                break;
            case SPECIFIC_PACK:
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal("You can specify a shader pack after completing setup"),
                        centerX, descY, 0xFF88AACC);
                break;
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
}
