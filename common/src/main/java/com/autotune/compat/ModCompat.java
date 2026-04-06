package com.autotune.compat;

public interface ModCompat {
    String getModId();
    String getModName();
    boolean isLoaded();
    void initialize();
}
