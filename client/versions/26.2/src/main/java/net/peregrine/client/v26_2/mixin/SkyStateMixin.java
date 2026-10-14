package net.peregrine.client.v26_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Static sky: the sun, moon and stars stay where they were. */
@Mixin(targets = {"net.minecraft.client.renderer.SkyRenderer"})
public abstract class SkyStateMixin {

    @Unique
    private static float[] peregrine$frozen;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void peregrine$still(CallbackInfo ci, @Local(argsOnly = true) SkyRenderState state) {
        if (!Hooks.staticSky) {
            peregrine$frozen = null;
            return;
        }
        if (peregrine$frozen == null) {
            peregrine$frozen = new float[] {state.sunAngle, state.moonAngle, state.starAngle};
        }
        if (Hooks.skyHits < 1000) {
            Hooks.skyHits++;
        }
        state.sunAngle = peregrine$frozen[0];
        state.moonAngle = peregrine$frozen[1];
        state.starAngle = peregrine$frozen[2];
    }
}
