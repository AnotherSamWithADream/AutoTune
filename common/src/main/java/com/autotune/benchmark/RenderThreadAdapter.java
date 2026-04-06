package com.autotune.benchmark;

import com.autotune.platform.PlatformAdapter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GraphicsMode;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * Wraps a PlatformAdapter so that all setter calls are executed on the render thread.
 * This is necessary because Style-B benchmark phases run on a background thread but
 * Minecraft requires game option changes to happen on the render thread (RenderSystem thread check).
 *
 * Getter calls run on the calling thread since they don't touch GL state.
 */
public class RenderThreadAdapter implements PlatformAdapter {

    private final PlatformAdapter delegate;
    private final MinecraftClient client;

    public RenderThreadAdapter(PlatformAdapter delegate, MinecraftClient client) {
        this.delegate = delegate;
        this.client = client;
    }

    private void runOnRenderThread(Runnable task) {
        if (client.isOnThread()) {
            task.run();
        } else {
            // Fire-and-forget: queue the task on the render thread and sleep briefly
            // to let it execute. Using CompletableFuture.get() would deadlock because
            // the render thread may be waiting for our background thread.
            client.execute(task);
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }
    }

    private <T> T getOnRenderThread(Supplier<T> supplier) {
        // Getters are safe to call from any thread (they just read game options)
        return supplier.get();
    }

    @Override public void setRenderDistance(int d) { runOnRenderThread(() -> delegate.setRenderDistance(d)); }
    @Override public int getRenderDistance() { return delegate.getRenderDistance(); }
    @Override public void setSimulationDistance(int d) { runOnRenderThread(() -> delegate.setSimulationDistance(d)); }
    @Override public int getSimulationDistance() { return delegate.getSimulationDistance(); }
    @Override public void setGraphicsMode(GraphicsMode m) { runOnRenderThread(() -> delegate.setGraphicsMode(m)); }
    @Override public GraphicsMode getGraphicsMode() { return delegate.getGraphicsMode(); }
    @Override public void setSmoothLighting(boolean e) { runOnRenderThread(() -> delegate.setSmoothLighting(e)); }
    @Override public boolean getSmoothLighting() { return delegate.getSmoothLighting(); }
    @Override public void setMaxFps(int f) { runOnRenderThread(() -> delegate.setMaxFps(f)); }
    @Override public int getMaxFps() { return delegate.getMaxFps(); }
    @Override public void setVsync(boolean e) { runOnRenderThread(() -> delegate.setVsync(e)); }
    @Override public boolean getVsync() { return delegate.getVsync(); }
    @Override public void setBiomeBlendRadius(int r) { runOnRenderThread(() -> delegate.setBiomeBlendRadius(r)); }
    @Override public int getBiomeBlendRadius() { return delegate.getBiomeBlendRadius(); }
    @Override public void setEntityShadows(boolean e) { runOnRenderThread(() -> delegate.setEntityShadows(e)); }
    @Override public boolean getEntityShadows() { return delegate.getEntityShadows(); }
    @Override public void setParticles(int l) { runOnRenderThread(() -> delegate.setParticles(l)); }
    @Override public int getParticles() { return delegate.getParticles(); }
    @Override public void setMipmapLevels(int l) { runOnRenderThread(() -> delegate.setMipmapLevels(l)); }
    @Override public int getMipmapLevels() { return delegate.getMipmapLevels(); }
    @Override public void setCloudRenderMode(int m) { runOnRenderThread(() -> delegate.setCloudRenderMode(m)); }
    @Override public int getCloudRenderMode() { return delegate.getCloudRenderMode(); }
    @Override public void setFov(int f) { runOnRenderThread(() -> delegate.setFov(f)); }
    @Override public int getFov() { return delegate.getFov(); }
    @Override public void setFovEffectScale(float s) { runOnRenderThread(() -> delegate.setFovEffectScale(s)); }
    @Override public float getFovEffectScale() { return delegate.getFovEffectScale(); }
    @Override public void setScreenEffectScale(float s) { runOnRenderThread(() -> delegate.setScreenEffectScale(s)); }
    @Override public float getScreenEffectScale() { return delegate.getScreenEffectScale(); }
    @Override public void setDarknessEffectScale(float s) { runOnRenderThread(() -> delegate.setDarknessEffectScale(s)); }
    @Override public float getDarknessEffectScale() { return delegate.getDarknessEffectScale(); }
    @Override public void setViewBobbing(boolean e) { runOnRenderThread(() -> delegate.setViewBobbing(e)); }
    @Override public boolean getViewBobbing() { return delegate.getViewBobbing(); }
    @Override public void setGuiScale(int s) { runOnRenderThread(() -> delegate.setGuiScale(s)); }
    @Override public int getGuiScale() { return delegate.getGuiScale(); }
    @Override public void setFullscreen(boolean e) { runOnRenderThread(() -> delegate.setFullscreen(e)); }
    @Override public boolean getFullscreen() { return delegate.getFullscreen(); }
    @Override public void setEntityDistanceScaling(float s) { runOnRenderThread(() -> delegate.setEntityDistanceScaling(s)); }
    @Override public float getEntityDistanceScaling() { return delegate.getEntityDistanceScaling(); }
    @Override public void setGamma(double g) { runOnRenderThread(() -> delegate.setGamma(g)); }
    @Override public double getGamma() { return delegate.getGamma(); }
    @Override public void setAttackIndicator(int m) { runOnRenderThread(() -> delegate.setAttackIndicator(m)); }
    @Override public int getAttackIndicator() { return delegate.getAttackIndicator(); }
    @Override public void invalidateChunks() { runOnRenderThread(delegate::invalidateChunks); }
    @Override public int getLoadedChunkCount() { return delegate.getLoadedChunkCount(); }
    @Override public void reloadChunks() { runOnRenderThread(delegate::reloadChunks); }
    @Override public String getMinecraftVersion() { return delegate.getMinecraftVersion(); }
}
