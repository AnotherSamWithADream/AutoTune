package com.autotune.platform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.SharedConstants;

/**
 * Default PlatformAdapter that works for most 1.21.x versions.
 * Version subprojects override specific methods where APIs changed.
 */
public class DefaultPlatformAdapter implements PlatformAdapter {

    protected GameOptions options() {
        return MinecraftClient.getInstance().options;
    }

    @Override
    public void setRenderDistance(int distance) {
        options().getViewDistance().setValue(distance);
    }

    @Override
    public int getRenderDistance() {
        return options().getViewDistance().getValue();
    }

    @Override
    public void setSimulationDistance(int distance) {
        options().getSimulationDistance().setValue(distance);
    }

    @Override
    public int getSimulationDistance() {
        return options().getSimulationDistance().getValue();
    }

    @Override
    public void setGraphicsMode(GraphicsMode mode) {
        // Method name changed across versions: getGraphicsMode (1.21-1.21.10), getPreset (1.21.11)
        for (String methodName : new String[]{"getGraphicsMode", "getPreset", "getGraphicsQuality"}) {
            try {
                var method = GameOptions.class.getMethod(methodName);
                var option = method.invoke(options());
                option.getClass().getMethod("setValue", Object.class).invoke(option, mode);
                return; // Success
            } catch (Exception ignored) {}
        }
    }

    @Override
    public GraphicsMode getGraphicsMode() {
        // Method name changed across versions: getGraphicsMode (1.21-1.21.10), getPreset (1.21.11)
        for (String methodName : new String[]{"getGraphicsMode", "getPreset", "getGraphicsQuality"}) {
            try {
                var method = GameOptions.class.getMethod(methodName);
                var option = method.invoke(options());
                return (GraphicsMode) option.getClass().getMethod("getValue").invoke(option);
            } catch (Exception ignored) {}
        }
        return GraphicsMode.FANCY;
    }

    @Override
    public void setSmoothLighting(boolean enabled) {
        options().getAo().setValue(enabled);
    }

    @Override
    public boolean getSmoothLighting() {
        return options().getAo().getValue();
    }

    @Override
    public void setMaxFps(int fps) {
        options().getMaxFps().setValue(fps);
    }

    @Override
    public int getMaxFps() {
        return options().getMaxFps().getValue();
    }

    @Override
    public void setVsync(boolean enabled) {
        options().getEnableVsync().setValue(enabled);
    }

    @Override
    public boolean getVsync() {
        return options().getEnableVsync().getValue();
    }

    @Override
    public void setBiomeBlendRadius(int radius) {
        options().getBiomeBlendRadius().setValue(radius);
    }

    @Override
    public int getBiomeBlendRadius() {
        return options().getBiomeBlendRadius().getValue();
    }

    @Override
    public void setEntityShadows(boolean enabled) {
        options().getEntityShadows().setValue(enabled);
    }

    @Override
    public boolean getEntityShadows() {
        return options().getEntityShadows().getValue();
    }

    @Override
    // Unchecked/rawtypes: ParticlesMode enum resolved via reflection; raw Enum.valueOf needed for cross-version compat
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void setParticles(int level) {
        try {
            // ParticlesMode moved packages between MC versions; resolve via reflection
            Object currentVal = options().getParticles().getValue();
            Class enumClass = currentVal.getClass();
            String name = switch (level) {
                case 1 -> "DECREASED";
                case 2 -> "MINIMAL";
                default -> "ALL";
            };
            Object enumVal = Enum.valueOf(enumClass, name);
            // Use raw-typed access to bypass generics check on setValue
            var particlesOption = options().getParticles();
            java.lang.reflect.Method setValueMethod = particlesOption.getClass().getMethod("setValue", Object.class);
            setValueMethod.invoke(particlesOption, enumVal);
        } catch (Exception e) {
            // Fallback: do nothing
        }
    }

    @Override
    public int getParticles() {
        Enum<?> val = options().getParticles().getValue();
        return switch (val.name()) {
            case "DECREASED" -> 1;
            case "MINIMAL" -> 2;
            default -> 0;
        };
    }

    @Override
    public void setMipmapLevels(int levels) {
        options().getMipmapLevels().setValue(levels);
    }

    @Override
    public int getMipmapLevels() {
        return options().getMipmapLevels().getValue();
    }

    @Override
    public void setCloudRenderMode(int mode) {
        CloudRenderMode crm = switch (mode) {
            case 1 -> CloudRenderMode.FAST;
            case 2 -> CloudRenderMode.FANCY;
            default -> CloudRenderMode.OFF;
        };
        options().getCloudRenderMode().setValue(crm);
    }

    @Override
    public int getCloudRenderMode() {
        return switch (options().getCloudRenderMode().getValue()) {
            case FAST -> 1;
            case FANCY -> 2;
            default -> 0;
        };
    }

    @Override
    public void setFov(int fov) {
        options().getFov().setValue(fov);
    }

    @Override
    public int getFov() {
        return options().getFov().getValue();
    }

    @Override
    public void setFovEffectScale(float scale) {
        options().getFovEffectScale().setValue((double) scale);
    }

    @Override
    public float getFovEffectScale() {
        return options().getFovEffectScale().getValue().floatValue();
    }

    @Override
    public void setScreenEffectScale(float scale) {
        options().getDistortionEffectScale().setValue((double) scale);
    }

    @Override
    public float getScreenEffectScale() {
        return options().getDistortionEffectScale().getValue().floatValue();
    }

    @Override
    public void setDarknessEffectScale(float scale) {
        options().getDarknessEffectScale().setValue((double) scale);
    }

    @Override
    public float getDarknessEffectScale() {
        return options().getDarknessEffectScale().getValue().floatValue();
    }

    @Override
    public void setViewBobbing(boolean enabled) {
        options().getBobView().setValue(enabled);
    }

    @Override
    public boolean getViewBobbing() {
        return options().getBobView().getValue();
    }

    @Override
    public void setGuiScale(int scale) {
        options().getGuiScale().setValue(scale);
    }

    @Override
    public int getGuiScale() {
        return options().getGuiScale().getValue();
    }

    @Override
    public void setFullscreen(boolean enabled) {
        options().getFullscreen().setValue(enabled);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow().isFullscreen() != enabled) {
            client.getWindow().toggleFullscreen();
        }
    }

    @Override
    public boolean getFullscreen() {
        return options().getFullscreen().getValue();
    }

    @Override
    public void setEntityDistanceScaling(float scaling) {
        options().getEntityDistanceScaling().setValue((double) scaling);
    }

    @Override
    public float getEntityDistanceScaling() {
        return options().getEntityDistanceScaling().getValue().floatValue();
    }

    @Override
    public void setGamma(double gamma) {
        options().getGamma().setValue(gamma);
    }

    @Override
    public double getGamma() {
        return options().getGamma().getValue();
    }

    @Override
    public void setAttackIndicator(int mode) {
        var values = net.minecraft.client.option.AttackIndicator.values();
        if (mode >= 0 && mode < values.length) {
            options().getAttackIndicator().setValue(values[mode]);
        }
    }

    @Override
    public int getAttackIndicator() {
        return options().getAttackIndicator().getValue().ordinal();
    }

    @Override
    public void invalidateChunks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null) {
            client.worldRenderer.reload();
        }
    }

    @Override
    public int getLoadedChunkCount() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null) {
            return client.worldRenderer.getCompletedChunkCount();
        }
        return 0;
    }

    @Override
    public void reloadChunks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null) {
            client.worldRenderer.reload();
        }
    }

    @Override
    public String getMinecraftVersion() {
        Object gameVersion = SharedConstants.getGameVersion();
        // Try different method names; the API changed across MC versions
        for (String methodName : new String[]{"getId", "getName", "getVersionString"}) {
            try {
                java.lang.reflect.Method method = gameVersion.getClass().getMethod(methodName);
                Object result = method.invoke(gameVersion);
                if (result instanceof String s) {
                    return s;
                }
            } catch (Exception ignored) {}
        }
        // Final fallback
        return gameVersion.toString();
    }
}
