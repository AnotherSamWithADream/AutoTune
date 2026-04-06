package com.autotune.platform;

/**
 * PlatformAdapter implementation for Minecraft 1.21.2.
 * Extends DefaultPlatformAdapter; override methods here if APIs changed in this version.
 */
public class PlatformAdapterImpl extends DefaultPlatformAdapter {

    @Override
    public String getMinecraftVersion() {
        return "1.21.2";
    }
}
