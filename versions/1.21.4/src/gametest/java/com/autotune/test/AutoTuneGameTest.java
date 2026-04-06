package com.autotune.test;

import com.autotune.AutoTuneMod;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * Server-side GameTests for AutoTune.
 * Run with: ./gradlew :versions-1_21_4:runGametest
 */
public class AutoTuneGameTest {

    @GameTest
    public void modInitializes(TestContext context) {
        context.addFinalTaskWithDuration(20, () -> {
            AutoTuneMod mod = AutoTuneMod.getInstance();
            if (mod == null) {
                throw new AssertionError("AutoTuneMod instance is null");
            }
            context.complete();
        });
    }

    @GameTest
    public void configLoads(TestContext context) {
        context.addFinalTaskWithDuration(20, () -> {
            AutoTuneMod mod = AutoTuneMod.getInstance();
            if (mod == null || mod.getConfig() == null) {
                throw new AssertionError("Config failed to load");
            }
            if (mod.getConfig().getTargetFps() <= 0) {
                throw new AssertionError("Target FPS is invalid: " + mod.getConfig().getTargetFps());
            }
            context.complete();
        });
    }

    @GameTest
    public void settingsRegistryPopulated(TestContext context) {
        context.addFinalTaskWithDuration(20, () -> {
            AutoTuneMod mod = AutoTuneMod.getInstance();
            if (mod == null || mod.getSettingsRegistry() == null) {
                throw new AssertionError("SettingsRegistry is null");
            }
            int count = mod.getSettingsRegistry().getAll().size();
            if (count < 20) {
                throw new AssertionError("Only " + count + " settings registered, expected 20+");
            }
            context.complete();
        });
    }

    @GameTest
    public void liveEngineCreated(TestContext context) {
        context.addFinalTaskWithDuration(20, () -> {
            AutoTuneMod mod = AutoTuneMod.getInstance();
            if (mod == null || mod.getLiveEngine() == null) {
                throw new AssertionError("LiveAdaptiveEngine is null");
            }
            context.complete();
        });
    }
}
