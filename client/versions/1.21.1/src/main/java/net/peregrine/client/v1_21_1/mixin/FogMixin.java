package net.peregrine.client.v1_21_1.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fog: off in the distance, clear water and lava, and its color. */
@Mixin(FogRenderer.class)
public abstract class FogMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void peregrine$fog(Camera camera, FogRenderer.FogMode mode, float distance, boolean thick, float partial,
                                      CallbackInfo ci) {
        FogType type = camera.getFluidInCamera();
        boolean clear = type == FogType.WATER ? Hooks.clearWater : type == FogType.LAVA ? Hooks.clearLava
                : type == FogType.NONE && Hooks.fogOff;
        if (clear) {
            if (Hooks.fogHits < 1000) {
                Hooks.fogHits++;
            }
            RenderSystem.setShaderFogStart(1.0E6F);
            RenderSystem.setShaderFogEnd(2.0E6F);
        }
        if (Hooks.fogColor != 0 && type == FogType.NONE) {
            RenderSystem.setShaderFogColor(peregrine$c(16), peregrine$c(8), peregrine$c(0));
        }
    }

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void peregrine$sky(CallbackInfo ci) {
        if (Hooks.fogColor != 0) {
            RenderSystem.clearColor(peregrine$c(16), peregrine$c(8), peregrine$c(0), 0.0F);
        }
    }

    /** The fog color picked in the menu, as 0-1 floats. */
    private static float peregrine$c(int shift) {
        return ((Hooks.fogColor >> shift) & 0xFF) / 255f;
    }
}
