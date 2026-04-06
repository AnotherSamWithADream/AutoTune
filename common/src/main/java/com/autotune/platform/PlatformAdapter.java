package com.autotune.platform;

import net.minecraft.client.option.GraphicsMode;

import java.util.ServiceLoader;

/**
 * Abstracts Minecraft APIs that may change between 1.21.x versions.
 * Each version subproject provides a PlatformAdapterImpl.
 */
public interface PlatformAdapter {

    // --- Graphics Options ---
    void setRenderDistance(int distance);
    int getRenderDistance();

    void setSimulationDistance(int distance);
    int getSimulationDistance();

    void setGraphicsMode(GraphicsMode mode);
    GraphicsMode getGraphicsMode();

    void setSmoothLighting(boolean enabled);
    boolean getSmoothLighting();

    void setMaxFps(int fps);
    int getMaxFps();

    void setVsync(boolean enabled);
    boolean getVsync();

    void setBiomeBlendRadius(int radius);
    int getBiomeBlendRadius();

    void setEntityShadows(boolean enabled);
    boolean getEntityShadows();

    void setParticles(int level); // 0=ALL, 1=DECREASED, 2=MINIMAL
    int getParticles();

    void setMipmapLevels(int levels);
    int getMipmapLevels();

    void setCloudRenderMode(int mode); // 0=OFF, 1=FAST, 2=FANCY
    int getCloudRenderMode();

    void setFov(int fov);
    int getFov();

    void setFovEffectScale(float scale);
    float getFovEffectScale();

    void setScreenEffectScale(float scale);
    float getScreenEffectScale();

    void setDarknessEffectScale(float scale);
    float getDarknessEffectScale();

    void setViewBobbing(boolean enabled);
    boolean getViewBobbing();

    void setGuiScale(int scale);
    int getGuiScale();

    void setFullscreen(boolean enabled);
    boolean getFullscreen();

    void setEntityDistanceScaling(float scaling);
    float getEntityDistanceScaling();

    void setGamma(double gamma);
    double getGamma();

    void setAttackIndicator(int mode); // 0=OFF, 1=CROSSHAIR, 2=HOTBAR
    int getAttackIndicator();

    // --- Chunk System ---
    void invalidateChunks();
    int getLoadedChunkCount();
    void reloadChunks();

    // --- Version Info ---
    String getMinecraftVersion();

    // --- Factory ---
    static PlatformAdapter create() {
        ServiceLoader<PlatformAdapter> loader = ServiceLoader.load(PlatformAdapter.class);
        return loader.findFirst().orElseGet(DefaultPlatformAdapter::new);
    }
}
