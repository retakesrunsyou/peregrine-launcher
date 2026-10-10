package net.peregrine.client.v1_21_6.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.peregrine.client.core.Hooks;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fog color, in the open air. */
@Mixin(FogRenderer.class)
public abstract class FogColorMixin {

    @Inject(method = "computeFogColor", at = @At("RETURN"), cancellable = true)
    private void peregrine$color(CallbackInfoReturnable<Vector4f> cir, @Local(argsOnly = true) Camera camera) {
        if (Hooks.fogColor != 0 && camera.getFluidInCamera() == FogType.ATMOSPHERIC) {
            cir.setReturnValue(new Vector4f(peregrine$c(16), peregrine$c(8), peregrine$c(0), cir.getReturnValue().w));
        }
    }

    /** The fog color picked in the menu, as 0-1 floats. */
    private static float peregrine$c(int shift) {
        return ((Hooks.fogColor >> shift) & 0xFF) / 255f;
    }
}
