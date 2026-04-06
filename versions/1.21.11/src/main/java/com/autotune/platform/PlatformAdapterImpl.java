package com.autotune.platform;

import net.minecraft.client.option.GraphicsMode;

/**
 * PlatformAdapter implementation for Minecraft 1.21.11.
 * In 1.21.11, getGraphicsMode() was renamed to getPreset().
 */
public class PlatformAdapterImpl extends DefaultPlatformAdapter {

    @Override
    public String getMinecraftVersion() {
        return "1.21.11";
    }

    @Override
    public void setGraphicsMode(GraphicsMode mode) {
        // 1.21.11: getGraphicsMode() renamed to getPreset()
        options().getPreset().setValue(mode);
    }

    @Override
    public GraphicsMode getGraphicsMode() {
        // 1.21.11: getGraphicsMode() renamed to getPreset()
        return options().getPreset().getValue();
    }
}
