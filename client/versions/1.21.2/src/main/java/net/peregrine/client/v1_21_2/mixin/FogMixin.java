package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.peregrine.client.core.Hooks;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fog: off in the distance, clear water and lava, and its color. */
@Mixin(FogRenderer.class)
public abstract class FogMixin {

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private static void peregrine$fog(Camera camera, FogRenderer.FogMode mode, Vector4f color, float distance, boolean thick,
                                      float partial, CallbackInfoReturnable<FogParameters> cir) {
        FogType type = camera.getFluidInCamera();
        boolean clear = type == FogType.WATER ? Hooks.clearWater : type == FogType.LAVA ? Hooks.clearLava
                : type == FogType.NONE && Hooks.fogOff;
        if (clear) {
            if (Hooks.fogHits < 1000) {
                Hooks.fogHits++;
            }
            FogParameters p = cir.getReturnValue();
            cir.setReturnValue(new FogParameters(1.0E6F, 2.0E6F, p.shape(), p.red(), p.green(), p.blue(), p.alpha()));
        }
    }

    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private static void peregrine$color(Camera camera, float partial, net.minecraft.client.multiplayer.ClientLevel level,
                                        int distance, float darken, CallbackInfoReturnable<Vector4f> cir) {
        if (Hooks.fogColor != 0 && camera.getFluidInCamera() == FogType.NONE) {
            cir.setReturnValue(new Vector4f(peregrine$c(16), peregrine$c(8), peregrine$c(0), cir.getReturnValue().w));
        }
    }

    /** The fog color picked in the menu, as 0-1 floats. */
    private static float peregrine$c(int shift) {
        return ((Hooks.fogColor >> shift) & 0xFF) / 255f;
    }
}
