package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sound filters: each kind of sound at the volume picked in the menu. */
@Mixin(targets = {"net.minecraft.client.sounds.SoundEngine"})
public abstract class SoundFilterMixin {

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void peregrine$filter(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (Hooks.soundFiltered) {
            float k = Hooks.soundFactor(String.valueOf(sound.getIdentifier()));
            if (k != 1f) {
                cir.setReturnValue(cir.getReturnValue() * k);
            }
        }
    }
}
