package net.peregrine.client.v1_21_2.mixin;

import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Static sky: the time of day your game draws with stays where it was (sun, moon and stars stop). */
@Mixin(targets = {"net.minecraft.client.multiplayer.ClientLevel$ClientLevelData"})
public abstract class SkyTimeMixin {

    @Unique
    private static long peregrine$frozen = Long.MIN_VALUE;

    @Inject(method = "getDayTime()J", at = @At("RETURN"), cancellable = true)
    private void peregrine$still(CallbackInfoReturnable<Long> cir) {
        if (!Hooks.staticSky) {
            peregrine$frozen = Long.MIN_VALUE;
            return;
        }
        if (peregrine$frozen == Long.MIN_VALUE) {
            peregrine$frozen = cir.getReturnValue();
        }
        if (Hooks.skyHits < 1000) {
            Hooks.skyHits++;
        }
        cir.setReturnValue(peregrine$frozen);
    }
}
