package net.peregrine.client.v1_21_2.mixin;

import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clear water: no blue overlay on your screen under water. */
@Mixin(targets = {"net.minecraft.client.renderer.ScreenEffectRenderer"})
public abstract class WaterOverlayMixin {

    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void peregrine$clear(CallbackInfo ci) {
        if (Hooks.clearWater) {
            ci.cancel();
        }
    }
}
