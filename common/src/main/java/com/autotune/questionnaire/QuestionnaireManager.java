package com.autotune.questionnaire;

import com.autotune.AutoTuneMod;
import com.autotune.AutoTuneLogger;
import com.autotune.config.AutoTuneConfig;
import com.autotune.config.ConfigManager;
import com.autotune.questionnaire.screens.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuestionnaireManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestionnaireManager.class);
    private static final int TOTAL_SCREENS = 8;

    private static QuestionnaireManager instance;

    private final PlayerPreferences preferences;
    private Screen parentScreen;
    private int currentScreenIndex;
    private boolean active;

    private QuestionnaireManager() {
        this.preferences = new PlayerPreferences();
        this.currentScreenIndex = 0;
        this.active = false;
    }

    public static QuestionnaireManager getInstance() {
        if (instance == null) {
            instance = new QuestionnaireManager();
        }
        return instance;
    }

    /**
     * Starts the questionnaire flow from the first screen.
     *
     * @param parent the screen to return to when the questionnaire is complete or cancelled
     */
    public void startQuestionnaire(Screen parent) {
        this.parentScreen = parent;
        this.currentScreenIndex = 0;
        this.active = true;
        LOGGER.info("Starting AutoTune questionnaire");
        openScreen(0);
    }

    /**
     * Navigates to the next screen. If on the last screen, completes the questionnaire.
     */
    public void nextScreen() {
        if (currentScreenIndex < TOTAL_SCREENS - 1) {
            currentScreenIndex++;
            openScreen(currentScreenIndex);
        } else {
            onComplete(preferences);
        }
    }

    /**
     * Navigates to the previous screen. If on the first screen, cancels the questionnaire.
     */
    public void previousScreen() {
        if (currentScreenIndex > 0) {
            currentScreenIndex--;
            openScreen(currentScreenIndex);
        } else {
            cancel();
        }
    }

    /**
     * Opens the screen at the given index.
     */
    private void openScreen(int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        Screen screen = createScreen(index);
        if (screen != null) {
            client.setScreen(screen);
        }
    }

    /**
     * Creates the appropriate screen for the given index.
     */
    private Screen createScreen(int index) {
        return switch (index) {
            case 0 -> new PriorityScreen(this);
            case 1 -> new TargetFPSScreen(this);
            case 2 -> new MinimumFPSScreen(this);
            case 3 -> new ShaderPreferenceScreen(this);
            case 4 -> new PlayStyleScreen(this);
            case 5 -> new SpecialConsiderationsScreen(this);
            case 6 -> new OverridesScreen(this);
            case 7 -> new LiveModePreferencesScreen(this);
            default -> {
                LOGGER.error("Invalid questionnaire screen index: {}", index);
                yield null;
            }
        };
    }

    /**
     * Called when the questionnaire is completed. Saves preferences to the config.
     */
    public void onComplete(PlayerPreferences prefs) {
        active = false;
        LOGGER.info("Questionnaire completed, saving preferences");

        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod != null) {
            ConfigManager configManager = mod.getConfigManager();
            AutoTuneConfig config = mod.getConfig();

            // Apply target and floor FPS to main config
            config.setTargetFps(prefs.getTargetFps());
            config.setFloorFps(prefs.getFloorFps());

            // Apply live mode preferences to config
            AutoTuneConfig.LiveModeConfig liveModeConfig = config.getLiveModeConfig();
            switch (prefs.getLiveModePref()) {
                case FULL:
                    liveModeConfig.setMode("full");
                    break;
                case CONSERVATIVE:
                    liveModeConfig.setMode("conservative");
                    break;
                case STATIC:
                    liveModeConfig.setMode("static");
                    liveModeConfig.setEnabled(false);
                    break;
            }

            // Save preferences file
            configManager.savePlayerPreferences(prefs);
            configManager.save(config);

            LOGGER.info("Player preferences saved: targetFps={}, floorFps={}, liveMode={}",
                    prefs.getTargetFps(), prefs.getFloorFps(), prefs.getLiveModePref());
        }

        // Return to game (close all screens)
        AutoTuneLogger.info("Questionnaire complete, closing screen");
        MinecraftClient.getInstance().setScreen(null);
    }

    /**
     * Cancels the questionnaire and returns to the parent screen.
     */
    public void cancel() {
        active = false;
        LOGGER.info("Questionnaire cancelled");
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    /**
     * Returns the current screen index (0-7).
     */
    public int getCurrentScreen() {
        return currentScreenIndex;
    }

    /**
     * Returns the total number of screens.
     */
    public int getTotalScreens() {
        return TOTAL_SCREENS;
    }

    /**
     * Returns the preferences object being built during the questionnaire.
     */
    public PlayerPreferences getPreferences() {
        return preferences;
    }

    /**
     * Returns whether the questionnaire is currently active.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Returns the parent screen to return to after completion or cancellation.
     */
    public Screen getParentScreen() {
        return parentScreen;
    }

    /**
     * Returns the progress percentage (0.0 - 1.0).
     */
    public float getProgress() {
        return (float) (currentScreenIndex + 1) / TOTAL_SCREENS;
    }

    /**
     * Resets the manager for a fresh questionnaire run.
     */
    public static void reset() {
        instance = null;
    }
}
