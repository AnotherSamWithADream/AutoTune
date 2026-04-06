package com.autotune.mixin;

import com.autotune.AutoTuneMod;
import com.autotune.live.LiveAdaptiveEngine;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    private long autotune$frameStartNanos;

    @Inject(method = "render", at = @At("HEAD"))
    private void autotune$onRenderStart(boolean tick, CallbackInfo ci) {
        autotune$frameStartNanos = System.nanoTime();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void autotune$onRenderEnd(boolean tick, CallbackInfo ci) {
        long frameTimeNanos = System.nanoTime() - autotune$frameStartNanos;
        AutoTuneMod mod = AutoTuneMod.getInstance();
        if (mod != null) {
            LiveAdaptiveEngine engine = mod.getLiveEngine();
            if (engine != null) {
                engine.onFrameRendered(frameTimeNanos);
            }

            var runner = mod.getBenchmarkRunner();
            if (runner != null && runner.isRunning()) {
                runner.recordFrameTime(frameTimeNanos);
            }
        }
    }
}
