package net.peregrine.client.v1_21_6.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.fog.FogData;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fog: pushes this kind of fog out of sight when it's switched off in the menu. */
@Mixin(targets = {"net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment"})
public abstract class FogAirMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private void peregrine$clear(CallbackInfo ci, @Local(argsOnly = true) FogData fog) {
        if (Hooks.fogOff) {
            if (Hooks.fogHits < 1000) {
                Hooks.fogHits++;
            }
            fog.environmentalStart = 1.0E6F;
            fog.environmentalEnd = 2.0E6F;
            fog.renderDistanceStart = 1.0E6F;
            fog.renderDistanceEnd = 2.0E6F;
            fog.skyEnd = 2.0E6F;
            fog.cloudEnd = 2.0E6F;
        }
    }
}
