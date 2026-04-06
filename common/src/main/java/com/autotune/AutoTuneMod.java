package com.autotune;

import com.autotune.benchmark.BenchmarkRunner;
import com.autotune.compat.DetectedModsRegistry;
import com.autotune.config.AutoTuneConfig;
import com.autotune.config.ConfigManager;
import com.autotune.benchmark.hardware.HardwareDetector;
import com.autotune.benchmark.hardware.HardwareProfile;
import com.autotune.live.LiveAdaptiveEngine;
import com.autotune.optimizer.SettingsRegistry;
import com.autotune.platform.PlatformAdapter;
import com.autotune.profile.ProfileManager;
import com.autotune.ui.hud.BenchmarkProgressHud;
import com.autotune.ui.hud.FPSOverlayHud;
import com.autotune.ui.hud.LiveModeHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoTuneMod implements ClientModInitializer {

    public static final String MOD_ID = "autotune";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static AutoTuneMod instance;

    private PlatformAdapter platformAdapter;
    private ConfigManager configManager;
    private AutoTuneConfig config;
    private HardwareProfile hardwareProfile;
    private SettingsRegistry settingsRegistry;
    private BenchmarkRunner benchmarkRunner;
    private LiveAdaptiveEngine liveEngine;
    private ProfileManager profileManager;
    private DetectedModsRegistry modsRegistry;

    private BenchmarkProgressHud benchmarkHud;
    private FPSOverlayHud fpsOverlayHud;
    private LiveModeHud liveModeHud;
    private boolean hardwareDetectionAttempted;

    @Override
    public void onInitializeClient() {
        instance = this;
        AutoTuneLogger.init();
        AutoTuneLogger.section("MOD INITIALIZATION");
        AutoTuneLogger.info("AutoTune v1.0.0 initializing...");

        platformAdapter = PlatformAdapter.create();
        configManager = new ConfigManager();
        config = configManager.loadOrCreate();

        modsRegistry = new DetectedModsRegistry();
        modsRegistry.scan();

        settingsRegistry = new SettingsRegistry(platformAdapter, modsRegistry);
        profileManager = new ProfileManager(configManager);
        benchmarkRunner = new BenchmarkRunner(platformAdapter, settingsRegistry, config);
        liveEngine = new LiveAdaptiveEngine(settingsRegistry, config);

        benchmarkHud = new BenchmarkProgressHud(benchmarkRunner);
        fpsOverlayHud = new FPSOverlayHud();
        liveModeHud = new LiveModeHud(liveEngine);

        AutoTuneKeybindings.register();

        // HudRenderCallback is deprecated since Fabric API 0.116 in favor of HudElementRegistry (1.21.6+).
        // We use it here for cross-version compatibility (1.21 through 1.21.11). It still works in all versions.
        @SuppressWarnings("deprecation")
        var hudEvent = HudRenderCallback.EVENT;
        hudEvent.register((drawContext, renderTickCounter) -> {
            if (benchmarkRunner.isRunning()) {
                benchmarkHud.render(drawContext);
            }
            if (config.isShowFpsOverlay()) {
                fpsOverlayHud.render(drawContext);
            }
            if (liveEngine.isEnabled()) {
                liveModeHud.render(drawContext);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Deferred hardware detection — OpenGL is ready by first tick
            if (hardwareProfile == null && !config.hasHardwareProfile() && !hardwareDetectionAttempted) {
                hardwareDetectionAttempted = true;
                try {
                    detectHardware();
                } catch (Exception e) {
                    LOGGER.error("Hardware detection failed", e);
                }
            }

            AutoTuneKeybindings.handleTick(client);

            // Tick the benchmark runner state machine every client tick
            if (benchmarkRunner.isRunning()) {
                benchmarkRunner.tick();
            }

            if (liveEngine.isEnabled() && client.world != null) {
                liveEngine.tick();
            }
        });

        // [CODE-REVIEW-FIX] Defer hardware detection to first client tick.
        // OpenGL context is NOT ready during onInitializeClient — calling GL11.glGetString()
        // here crashes with EXCEPTION_ACCESS_VIOLATION in lwjgl_opengl.dll.
        // Load cached profile if available; detect on first tick otherwise.
        if (config.hasHardwareProfile()) {
            hardwareProfile = configManager.loadHardwareProfile();
        }
        // else: hardwareProfile stays null, detected on first tick below

        AutoTuneLogger.info("Config loaded: targetFps={}, floorFps={}", config.getTargetFps(), config.getFloorFps());
        AutoTuneLogger.info("Settings registry: {} settings registered", settingsRegistry.getAll().size());
        AutoTuneLogger.info("Detected mods: {}", modsRegistry.getDetectedModNames());
        AutoTuneLogger.info("Benchmark runner: {} phases available", benchmarkRunner.getAllPhaseCount());
        AutoTuneLogger.section("INITIALIZATION COMPLETE");
    }

    public void detectHardware() {
        AutoTuneLogger.section("HARDWARE DETECTION");
        MinecraftClient client = MinecraftClient.getInstance();
        HardwareDetector detector = new HardwareDetector();
        hardwareProfile = detector.detect(client);
        configManager.saveHardwareProfile(hardwareProfile);
        config.setHasHardwareProfile(true);
        configManager.save(config);
        AutoTuneLogger.info("GPU: {} ({}, {} MB VRAM)", hardwareProfile.gpuName(), hardwareProfile.gpuVendor(), hardwareProfile.gpuVramMb());
        AutoTuneLogger.info("CPU: {} ({} cores / {} threads)", hardwareProfile.cpuName(), hardwareProfile.cpuCores(), hardwareProfile.cpuThreads());
        AutoTuneLogger.info("RAM: {} MB total, {} MB max heap", hardwareProfile.totalRamMb(), hardwareProfile.maxHeapMb());
        AutoTuneLogger.info("Display: {}x{} @ {} Hz", hardwareProfile.displayWidth(), hardwareProfile.displayHeight(), hardwareProfile.displayRefreshRate());
        AutoTuneLogger.info("Storage: {} ({} bytes free)", hardwareProfile.storageType(), hardwareProfile.storageFreeBytes());
        AutoTuneLogger.info("GL Version: {}", hardwareProfile.glVersion());
    }

    public static AutoTuneMod getInstance() {
        return instance;
    }

    public PlatformAdapter getPlatformAdapter() {
        return platformAdapter;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AutoTuneConfig getConfig() {
        return config;
    }

    public HardwareProfile getHardwareProfile() {
        return hardwareProfile;
    }

    public SettingsRegistry getSettingsRegistry() {
        return settingsRegistry;
    }

    public BenchmarkRunner getBenchmarkRunner() {
        return benchmarkRunner;
    }

    public LiveAdaptiveEngine getLiveEngine() {
        return liveEngine;
    }

    public ProfileManager getProfileManager() {
        return profileManager;
    }

    public DetectedModsRegistry getModsRegistry() {
        return modsRegistry;
    }
}
