package com.autotune.optimizer;

import com.autotune.compat.DetectedModsRegistry;
import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.option.GraphicsMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Central registry of all managed game settings. Registers vanilla Minecraft settings,
 * Sodium settings (if loaded), and Iris settings (if loaded), each with proper
 * min/max ranges, visual/performance impact scores, and applier/reader lambdas.
 */
public class SettingsRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoTune/SettingsRegistry");

    private final Map<String, SettingDefinition<?>> settings = new LinkedHashMap<>();
    private final PlatformAdapter adapter;

    public SettingsRegistry(PlatformAdapter adapter, DetectedModsRegistry modsRegistry) {
        this.adapter = adapter;

        registerVanillaSettings();

        if (modsRegistry.isModLoaded("sodium")) {
            registerSodiumSettings();
        }

        if (modsRegistry.isModLoaded("iris")) {
            registerIrisSettings();
        }

        // [CODE-REVIEW-FIX] L-005: Use debug level to avoid logging during construction
        LOGGER.debug("Settings registry initialized: {} settings ({} vanilla, {} sodium, {} iris)",
                settings.size(),
                getByCategory("vanilla").size(),
                getByCategory("sodium").size(),
                getByCategory("iris").size());
    }

    // ---- Registration: Vanilla Settings (33 settings) ----

    private void registerVanillaSettings() {
        // 1. Render Distance
        register(SettingDefinition.builder("vanilla.render_distance", Integer.class)
                .displayName("Render Distance")
                .category("vanilla")
                .min(2).max(32).defaultValue(12)
                .visualImpact(0.95).performanceImpact(0.95)
                .applier(adapter::setRenderDistance)
                .reader(adapter::getRenderDistance)
                .benchmarkPhaseId("render_distance_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(5000)
                .description("How far chunks are rendered in the world")
                .build());

        // 2. Simulation Distance
        register(SettingDefinition.builder("vanilla.simulation_distance", Integer.class)
                .displayName("Simulation Distance")
                .category("vanilla")
                .min(5).max(32).defaultValue(12)
                .visualImpact(0.30).performanceImpact(0.80)
                .applier(adapter::setSimulationDistance)
                .reader(adapter::getSimulationDistance)
                .benchmarkPhaseId("simulation_distance_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(5000)
                .description("How far entity and block simulation occurs")
                .build());

        // 3. Graphics Mode (discrete: FAST=1, FANCY=2, FABULOUS=3)
        register(SettingDefinition.builder("vanilla.graphics_mode", Integer.class)
                .displayName("Graphics Mode")
                .category("vanilla")
                .min(1).max(3).defaultValue(2)
                .discreteValues(List.of(1, 2, 3))
                .visualImpact(0.85).performanceImpact(0.70)
                .applier(v -> {
                    GraphicsMode mode = switch (v) {
                        case 1 -> GraphicsMode.FAST;
                        case 3 -> GraphicsMode.FABULOUS;
                        default -> GraphicsMode.FANCY;
                    };
                    adapter.setGraphicsMode(mode);
                })
                .reader(() -> {
                    GraphicsMode mode = adapter.getGraphicsMode();
                    if (mode == GraphicsMode.FAST) return 1;
                    if (mode == GraphicsMode.FABULOUS) return 3;
                    return 2;
                })
                .benchmarkPhaseId("graphics_mode_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(3000)
                .description("Overall graphics quality preset")
                .build());

        // 4. Smooth Lighting
        register(SettingDefinition.builder("vanilla.smooth_lighting", Boolean.class)
                .displayName("Smooth Lighting")
                .category("vanilla")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.60).performanceImpact(0.35)
                .applier(adapter::setSmoothLighting)
                .reader(adapter::getSmoothLighting)
                .benchmarkPhaseId("smooth_lighting_test")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(2000)
                .description("Smooth light level transitions between blocks")
                .build());

        // 5. Max FPS
        register(SettingDefinition.builder("vanilla.max_fps", Integer.class)
                .displayName("Max Framerate")
                .category("vanilla")
                .min(10).max(260).defaultValue(120)
                .visualImpact(0.05).performanceImpact(0.10)
                .applier(adapter::setMaxFps)
                .reader(adapter::getMaxFps)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Maximum framerate cap")
                .build());

        // 6. VSync
        register(SettingDefinition.builder("vanilla.vsync", Boolean.class)
                .displayName("VSync")
                .category("vanilla")
                .min(false).max(true).defaultValue(false)
                .visualImpact(0.10).performanceImpact(0.15)
                .applier(adapter::setVsync)
                .reader(adapter::getVsync)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(2000)
                .description("Synchronize framerate with monitor refresh rate")
                .build());

        // 7. Biome Blend Radius
        register(SettingDefinition.builder("vanilla.biome_blend", Integer.class)
                .displayName("Biome Blend")
                .category("vanilla")
                .min(0).max(7).defaultValue(2)
                .visualImpact(0.40).performanceImpact(0.45)
                .applier(adapter::setBiomeBlendRadius)
                .reader(adapter::getBiomeBlendRadius)
                .benchmarkPhaseId("biome_blend_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(3000)
                .description("Radius for blending biome colors at borders")
                .build());

        // 8. Entity Shadows
        register(SettingDefinition.builder("vanilla.entity_shadows", Boolean.class)
                .displayName("Entity Shadows")
                .category("vanilla")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.50).performanceImpact(0.30)
                .applier(adapter::setEntityShadows)
                .reader(adapter::getEntityShadows)
                .benchmarkPhaseId("entity_shadows_test")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(2000)
                .description("Render shadows beneath entities")
                .build());

        // 9. Particles (0=ALL, 1=DECREASED, 2=MINIMAL)
        register(SettingDefinition.builder("vanilla.particles", Integer.class)
                .displayName("Particles")
                .category("vanilla")
                .min(0).max(2).defaultValue(0)
                .discreteValues(List.of(0, 1, 2))
                .visualImpact(0.55).performanceImpact(0.40)
                .applier(adapter::setParticles)
                .reader(adapter::getParticles)
                .benchmarkPhaseId("particles_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(2000)
                .description("Particle rendering level (All, Decreased, Minimal)")
                .build());

        // 10. Mipmap Levels
        register(SettingDefinition.builder("vanilla.mipmap_levels", Integer.class)
                .displayName("Mipmap Levels")
                .category("vanilla")
                .min(0).max(4).defaultValue(4)
                .visualImpact(0.35).performanceImpact(0.25)
                .applier(adapter::setMipmapLevels)
                .reader(adapter::getMipmapLevels)
                .benchmarkPhaseId("mipmap_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(3000)
                .description("Texture mipmap levels for distant textures")
                .build());

        // 11. Cloud Render Mode (0=OFF, 1=FAST, 2=FANCY)
        register(SettingDefinition.builder("vanilla.cloud_render_mode", Integer.class)
                .displayName("Clouds")
                .category("vanilla")
                .min(0).max(2).defaultValue(2)
                .discreteValues(List.of(0, 1, 2))
                .visualImpact(0.45).performanceImpact(0.25)
                .applier(adapter::setCloudRenderMode)
                .reader(adapter::getCloudRenderMode)
                .benchmarkPhaseId("cloud_mode_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(2000)
                .description("Cloud rendering mode (Off, Fast, Fancy)")
                .build());

        // 12. FOV
        register(SettingDefinition.builder("vanilla.fov", Integer.class)
                .displayName("FOV")
                .category("vanilla")
                .min(30).max(110).defaultValue(70)
                .visualImpact(0.20).performanceImpact(0.15)
                .applier(adapter::setFov)
                .reader(adapter::getFov)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Field of view angle")
                .build());

        // 13. FOV Effect Scale
        register(SettingDefinition.builder("vanilla.fov_effect_scale", Float.class)
                .displayName("FOV Effects")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(1.0f)
                .visualImpact(0.10).performanceImpact(0.05)
                .applier(adapter::setFovEffectScale)
                .reader(adapter::getFovEffectScale)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Scale of FOV changes from speed effects")
                .build());

        // 14. Screen Effect Scale
        register(SettingDefinition.builder("vanilla.screen_effect_scale", Float.class)
                .displayName("Distortion Effects")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(1.0f)
                .visualImpact(0.15).performanceImpact(0.10)
                .applier(adapter::setScreenEffectScale)
                .reader(adapter::getScreenEffectScale)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Scale of nausea and portal screen distortion")
                .build());

        // 15. Darkness Effect Scale
        register(SettingDefinition.builder("vanilla.darkness_effect_scale", Float.class)
                .displayName("Darkness Pulsing")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(1.0f)
                .visualImpact(0.10).performanceImpact(0.05)
                .applier(adapter::setDarknessEffectScale)
                .reader(adapter::getDarknessEffectScale)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Scale of the Darkness effect pulsing")
                .build());

        // 16. View Bobbing
        register(SettingDefinition.builder("vanilla.view_bobbing", Boolean.class)
                .displayName("View Bobbing")
                .category("vanilla")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.20).performanceImpact(0.02)
                .applier(adapter::setViewBobbing)
                .reader(adapter::getViewBobbing)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Camera bobbing while walking")
                .build());

        // 17. GUI Scale
        register(SettingDefinition.builder("vanilla.gui_scale", Integer.class)
                .displayName("GUI Scale")
                .category("vanilla")
                .min(0).max(4).defaultValue(0)
                .visualImpact(0.05).performanceImpact(0.02)
                .applier(adapter::setGuiScale)
                .reader(adapter::getGuiScale)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Size of UI elements (0=Auto)")
                .build());

        // 18. Fullscreen
        register(SettingDefinition.builder("vanilla.fullscreen", Boolean.class)
                .displayName("Fullscreen")
                .category("vanilla")
                .min(false).max(true).defaultValue(false)
                .visualImpact(0.05).performanceImpact(0.10)
                .applier(adapter::setFullscreen)
                .reader(adapter::getFullscreen)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Run in fullscreen mode")
                .build());

        // 19. Entity Distance Scaling
        register(SettingDefinition.builder("vanilla.entity_distance_scaling", Float.class)
                .displayName("Entity Distance")
                .category("vanilla")
                .min(0.5f).max(5.0f).defaultValue(1.0f)
                .visualImpact(0.50).performanceImpact(0.45)
                .applier(adapter::setEntityDistanceScaling)
                .reader(adapter::getEntityDistanceScaling)
                .benchmarkPhaseId("entity_distance_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(3000)
                .description("Multiplier for entity rendering distance")
                .build());

        // 20. Gamma (Brightness)
        register(SettingDefinition.builder("vanilla.gamma", Float.class)
                .displayName("Brightness")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(0.5f)
                .visualImpact(0.15).performanceImpact(0.01)
                .applier(v -> adapter.setGamma(v.doubleValue()))
                .reader(() -> (float) adapter.getGamma())
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Screen brightness level")
                .build());

        // 21. Attack Indicator (0=OFF, 1=CROSSHAIR, 2=HOTBAR)
        register(SettingDefinition.builder("vanilla.attack_indicator", Integer.class)
                .displayName("Attack Indicator")
                .category("vanilla")
                .min(0).max(2).defaultValue(1)
                .discreteValues(List.of(0, 1, 2))
                .visualImpact(0.05).performanceImpact(0.01)
                .applier(adapter::setAttackIndicator)
                .reader(adapter::getAttackIndicator)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Attack cooldown indicator display mode")
                .build());

        // 22. Chunk Updates Per Frame
        register(SettingDefinition.builder("vanilla.chunk_updates_per_frame", Integer.class)
                .displayName("Chunk Updates/Frame")
                .category("vanilla")
                .min(1).max(10).defaultValue(5)
                .visualImpact(0.25).performanceImpact(0.50)
                .applier(v -> { /* Managed internally by the engine */ })
                .reader(() -> 5)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Maximum chunk section updates per frame")
                .build());

        // 23. Autosave Indicator
        register(SettingDefinition.builder("vanilla.autosave_indicator", Boolean.class)
                .displayName("Autosave Indicator")
                .category("vanilla")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.02).performanceImpact(0.01)
                .applier(v -> { /* UI-only */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Show autosave indicator icon")
                .build());

        // 24. Reduced Debug Info
        register(SettingDefinition.builder("vanilla.reduced_debug_info", Boolean.class)
                .displayName("Reduced Debug Info")
                .category("vanilla")
                .min(false).max(true).defaultValue(false)
                .visualImpact(0.01).performanceImpact(0.02)
                .applier(v -> { /* Server-controlled */ })
                .reader(() -> false)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Reduces information on debug screen")
                .build());

        // 25. Chat Opacity
        register(SettingDefinition.builder("vanilla.chat_opacity", Float.class)
                .displayName("Chat Opacity")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(1.0f)
                .visualImpact(0.02).performanceImpact(0.01)
                .applier(v -> { /* Chat UI */ })
                .reader(() -> 1.0f)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Chat window background opacity")
                .build());

        // 26. Chat Line Spacing
        register(SettingDefinition.builder("vanilla.chat_line_spacing", Float.class)
                .displayName("Chat Line Spacing")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(0.0f)
                .visualImpact(0.01).performanceImpact(0.01)
                .applier(v -> { /* Chat UI */ })
                .reader(() -> 0.0f)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Spacing between chat lines")
                .build());

        // 27. Text Background Opacity
        register(SettingDefinition.builder("vanilla.text_background_opacity", Float.class)
                .displayName("Text Background Opacity")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(0.5f)
                .visualImpact(0.02).performanceImpact(0.01)
                .applier(v -> { /* UI */ })
                .reader(() -> 0.5f)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Opacity of text background in signs and chat")
                .build());

        // 28. Narrator
        register(SettingDefinition.builder("vanilla.narrator", Integer.class)
                .displayName("Narrator")
                .category("vanilla")
                .min(0).max(3).defaultValue(0)
                .discreteValues(List.of(0, 1, 2, 3))
                .visualImpact(0.01).performanceImpact(0.01)
                .applier(v -> { /* Accessibility */ })
                .reader(() -> 0)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Narrator mode (Off, All, Chat, System)")
                .build());

        // 29. Master Volume
        register(SettingDefinition.builder("vanilla.master_volume", Float.class)
                .displayName("Master Volume")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(1.0f)
                .visualImpact(0.00).performanceImpact(0.05)
                .applier(v -> { /* Audio */ })
                .reader(() -> 1.0f)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Master audio volume")
                .build());

        // 30. Music Volume
        register(SettingDefinition.builder("vanilla.music_volume", Float.class)
                .displayName("Music Volume")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(1.0f)
                .visualImpact(0.00).performanceImpact(0.03)
                .applier(v -> { /* Audio */ })
                .reader(() -> 1.0f)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Music volume level")
                .build());

        // 31. Weather Volume
        register(SettingDefinition.builder("vanilla.weather_volume", Float.class)
                .displayName("Weather Volume")
                .category("vanilla")
                .min(0.0f).max(1.0f).defaultValue(1.0f)
                .visualImpact(0.00).performanceImpact(0.02)
                .applier(v -> { /* Audio */ })
                .reader(() -> 1.0f)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Weather sounds volume")
                .build());

        // 32. Realms Notifications
        register(SettingDefinition.builder("vanilla.realms_notifications", Boolean.class)
                .displayName("Realms Notifications")
                .category("vanilla")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.00).performanceImpact(0.01)
                .applier(v -> { /* Network */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Show Realms notification popups")
                .build());

        // 33. Allow Server Listing
        register(SettingDefinition.builder("vanilla.allow_server_listing", Boolean.class)
                .displayName("Allow Server Listing")
                .category("vanilla")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.00).performanceImpact(0.00)
                .applier(v -> { /* Network */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(1000)
                .description("Allow servers to list your name")
                .build());
    }

    // ---- Registration: Sodium Settings (21 settings) ----

    private void registerSodiumSettings() {
        // 1. Chunk Update Threads
        register(SettingDefinition.builder("sodium.chunk_update_threads", Integer.class)
                .displayName("Chunk Update Threads")
                .category("sodium")
                .min(0).max(16).defaultValue(0)
                .visualImpact(0.10).performanceImpact(0.60)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> 0)
                .benchmarkPhaseId("sodium_threads_sweep")
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Number of threads for chunk building (0=auto)")
                .build());

        // 2. Always Defer Chunk Updates
        register(SettingDefinition.builder("sodium.always_defer_chunk_updates", Boolean.class)
                .displayName("Defer Chunk Updates")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.15).performanceImpact(0.35)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId("sodium_defer_test")
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Defer chunk updates to reduce main thread stalls")
                .build());

        // 3. Use Block Face Culling
        register(SettingDefinition.builder("sodium.use_block_face_culling", Boolean.class)
                .displayName("Block Face Culling")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.02).performanceImpact(0.40)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId("sodium_culling_test")
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Skip rendering hidden block faces")
                .build());

        // 4. Use Fog Occlusion
        register(SettingDefinition.builder("sodium.use_fog_occlusion", Boolean.class)
                .displayName("Fog Occlusion")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.05).performanceImpact(0.30)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId("sodium_fog_test")
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Skip rendering chunks hidden by fog")
                .build());

        // 5. Use Entity Culling
        register(SettingDefinition.builder("sodium.use_entity_culling", Boolean.class)
                .displayName("Entity Culling")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.03).performanceImpact(0.35)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId("sodium_entity_culling_test")
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Skip rendering entities not visible to camera")
                .build());

        // 6. Use Particle Culling
        register(SettingDefinition.builder("sodium.use_particle_culling", Boolean.class)
                .displayName("Particle Culling")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.02).performanceImpact(0.20)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(2000)
                .description("Skip rendering particles not visible to camera")
                .build());

        // 7. Animate Only Visible Textures
        register(SettingDefinition.builder("sodium.use_animate_only_visible", Boolean.class)
                .displayName("Animate Only Visible")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.05).performanceImpact(0.25)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(2000)
                .description("Only animate textures currently on screen")
                .build());

        // 8-14. Individual animation toggles
        registerSodiumAnimation("sodium.animation.water", "Water Animation", 0.30, 0.15);
        registerSodiumAnimation("sodium.animation.lava", "Lava Animation", 0.25, 0.12);
        registerSodiumAnimation("sodium.animation.fire", "Fire Animation", 0.20, 0.10);
        registerSodiumAnimation("sodium.animation.portal", "Portal Animation", 0.15, 0.08);
        registerSodiumAnimation("sodium.animation.redstone", "Redstone Animation", 0.10, 0.05);
        registerSodiumAnimation("sodium.animation.enchantment", "Enchantment Animation", 0.10, 0.05);
        registerSodiumAnimation("sodium.animation.weather", "Weather Animation", 0.20, 0.15);

        // 15. Allow Direct Memory Access
        register(SettingDefinition.builder("sodium.allow_direct_memory", Boolean.class)
                .displayName("Direct Memory Access")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.00).performanceImpact(0.30)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Use direct memory for chunk data transfer")
                .build());

        // 16. Use Compact Vertex Format
        register(SettingDefinition.builder("sodium.use_compact_vertex_format", Boolean.class)
                .displayName("Compact Vertex Format")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.01).performanceImpact(0.20)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Use memory-efficient vertex format for chunk meshes")
                .build());

        // 17. Translucency Sorting
        register(SettingDefinition.builder("sodium.translucency_sort", Integer.class)
                .displayName("Translucency Sorting")
                .category("sodium")
                .min(0).max(2).defaultValue(1)
                .discreteValues(List.of(0, 1, 2))
                .visualImpact(0.20).performanceImpact(0.15)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> 1)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Translucent block sorting (Off, Dynamic, Static)")
                .build());

        // 18. No Error GL Context
        register(SettingDefinition.builder("sodium.use_no_error_context", Boolean.class)
                .displayName("No Error GL Context")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.00).performanceImpact(0.08)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Disable OpenGL error checking for performance")
                .build());

        // 19. Persistent Mapping
        register(SettingDefinition.builder("sodium.use_persistent_mapping", Boolean.class)
                .displayName("Persistent Mapping")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.00).performanceImpact(0.25)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Use persistent buffer mapping for uploads")
                .build());

        // 20. CPU Render Ahead Limit
        register(SettingDefinition.builder("sodium.cpu_render_ahead_limit", Integer.class)
                .displayName("CPU Render Ahead")
                .category("sodium")
                .min(0).max(9).defaultValue(3)
                .visualImpact(0.00).performanceImpact(0.20)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> 3)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Max frames CPU can prepare ahead of GPU")
                .build());

        // 21. Smooth Chunk Animation
        register(SettingDefinition.builder("sodium.smooth_chunk_animation", Boolean.class)
                .displayName("Smooth Chunk Animation")
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.15).performanceImpact(0.05)
                .applier(v -> { /* Sodium config */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(2000)
                .description("Smooth fade-in animation for newly loaded chunks")
                .build());
    }

    private void registerSodiumAnimation(String id, String displayName,
                                          double visualImpact, double performanceImpact) {
        register(SettingDefinition.builder(id, Boolean.class)
                .displayName(displayName)
                .category("sodium")
                .min(false).max(true).defaultValue(true)
                .visualImpact(visualImpact).performanceImpact(performanceImpact)
                .applier(v -> { /* Sodium animation config */ })
                .reader(() -> true)
                .benchmarkPhaseId("sodium_animations_sweep")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(2000)
                .description("Toggle " + displayName.toLowerCase())
                .build());
    }

    // ---- Registration: Iris Settings (6 settings) ----

    private void registerIrisSettings() {
        // 1. Shaders Enabled
        register(SettingDefinition.builder("iris.shaders_enabled", Boolean.class)
                .displayName("Shaders Enabled")
                .category("iris")
                .min(false).max(true).defaultValue(false)
                .visualImpact(1.0).performanceImpact(0.95)
                .applier(v -> { /* Iris API */ })
                .reader(() -> false)
                .benchmarkPhaseId("iris_shaders_test")
                .supportsLiveAdjust(true).liveAdjustCooldownMs(5000)
                .description("Enable or disable shader packs")
                .build());

        // 2. Shadow Quality
        register(SettingDefinition.builder("iris.shadow_quality", Float.class)
                .displayName("Shadow Quality")
                .category("iris")
                .min(0.0f).max(1.0f).defaultValue(1.0f)
                .visualImpact(0.80).performanceImpact(0.85)
                .applier(v -> { /* Iris config */ })
                .reader(() -> 1.0f)
                .benchmarkPhaseId("iris_shadow_sweep")
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Shadow map resolution multiplier")
                .build());

        // 3. Color Space
        register(SettingDefinition.builder("iris.color_space", Integer.class)
                .displayName("Color Space")
                .category("iris")
                .min(0).max(2).defaultValue(0)
                .discreteValues(List.of(0, 1, 2))
                .visualImpact(0.10).performanceImpact(0.05)
                .applier(v -> { /* Iris config */ })
                .reader(() -> 0)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Color space mode (sRGB, Display P3, Adobe RGB)")
                .build());

        // 4. Render Distance Override
        register(SettingDefinition.builder("iris.render_distance_override", Integer.class)
                .displayName("Shader Render Distance")
                .category("iris")
                .min(0).max(32).defaultValue(0)
                .visualImpact(0.70).performanceImpact(0.80)
                .applier(v -> { /* Iris config */ })
                .reader(() -> 0)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Override render distance for shaders (0=use vanilla)")
                .build());

        // 5. Shadow Distance
        register(SettingDefinition.builder("iris.shadow_distance", Integer.class)
                .displayName("Shadow Distance")
                .category("iris")
                .min(0).max(32).defaultValue(0)
                .visualImpact(0.70).performanceImpact(0.75)
                .applier(v -> { /* Iris config */ })
                .reader(() -> 0)
                .benchmarkPhaseId("iris_shadow_distance_sweep")
                .supportsLiveAdjust(false).liveAdjustCooldownMs(5000)
                .description("Maximum shadow rendering distance (0=shader default)")
                .build());

        // 6. Hand Lighting
        register(SettingDefinition.builder("iris.hand_lighting", Boolean.class)
                .displayName("Hand Lighting")
                .category("iris")
                .min(false).max(true).defaultValue(true)
                .visualImpact(0.30).performanceImpact(0.15)
                .applier(v -> { /* Iris config */ })
                .reader(() -> true)
                .benchmarkPhaseId(null)
                .supportsLiveAdjust(false).liveAdjustCooldownMs(3000)
                .description("Dynamic lighting from held items")
                .build());
    }

    // ---- Registry API ----

    private void register(SettingDefinition<?> definition) {
        if (settings.containsKey(definition.id())) {
            LOGGER.warn("Duplicate setting registration for ID: {}", definition.id());
        }
        settings.put(definition.id(), definition);
    }

    /**
     * Gets a setting definition by its ID, or null if not found.
     */
    public SettingDefinition<?> get(String id) {
        return settings.get(id);
    }

    /**
     * Returns all registered setting definitions.
     */
    public Collection<SettingDefinition<?>> getAll() {
        return Collections.unmodifiableCollection(settings.values());
    }

    /**
     * Returns all settings in a specific category.
     */
    public List<SettingDefinition<?>> getByCategory(String category) {
        return settings.values().stream()
                .filter(s -> s.category().equals(category))
                .collect(Collectors.toList());
    }

    /**
     * Returns all settings that support live adjustment.
     */
    public List<SettingDefinition<?>> getLiveAdjustable() {
        return settings.values().stream()
                .filter(SettingDefinition::supportsLiveAdjust)
                .collect(Collectors.toList());
    }

    /**
     * Returns all settings associated with a specific benchmark phase.
     */
    public List<SettingDefinition<?>> getByBenchmarkPhase(String phaseId) {
        return settings.values().stream()
                .filter(s -> phaseId.equals(s.benchmarkPhaseId()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the number of registered settings.
     */
    public int size() {
        return settings.size();
    }
}
