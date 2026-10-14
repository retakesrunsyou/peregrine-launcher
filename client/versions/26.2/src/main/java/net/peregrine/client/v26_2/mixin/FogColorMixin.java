package net.peregrine.client.v26_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.peregrine.client.core.Hooks;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fog color, in the open air. */
@Mixin(FogRenderer.class)
public abstract class FogColorMixin {

    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void peregrine$color(CallbackInfo ci, @Local(argsOnly = true) Camera camera, @Local(argsOnly = true) Vector4f out) {
        if (Hooks.fogColor != 0 && camera.getFluidInCamera() == FogType.ATMOSPHERIC) {
            out.set(peregrine$c(16), peregrine$c(8), peregrine$c(0), out.w);
        }
    }

    /** The fog color picked in the menu, as 0-1 floats. */
    private static float peregrine$c(int shift) {
        return ((Hooks.fogColor >> shift) & 0xFF) / 255f;
    }
}
