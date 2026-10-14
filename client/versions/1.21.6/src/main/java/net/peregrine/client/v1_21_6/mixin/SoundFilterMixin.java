package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.peregrine.client.core.Hooks;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
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
            float k = Hooks.soundFactor(String.valueOf(sound.getLocation()));
            if (k != 1f) {
                cir.setReturnValue(cir.getReturnValue() * k);
            }
        }
    }

    // New sounds get their volume straight from the category, not through the method above:
    // filter them here too (a volume of 0 means the sound isn't played at all).
    @ModifyExpressionValue(method = "play", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float peregrine$filterPlay(float volume, @Local(argsOnly = true) SoundInstance sound) {
        if (!Hooks.soundFiltered) {
            return volume;
        }
        return volume * Hooks.soundFactor(String.valueOf(sound.getLocation()));
    }
}
