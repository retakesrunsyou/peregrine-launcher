package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fullbright: the game draws your view as if you had night vision (only on your screen). */
@Mixin(LivingEntity.class)
public abstract class NightVisionMixin {

    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void peregrine$nightVision(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (Hooks.nightVision && effect == MobEffects.NIGHT_VISION && (Object) this instanceof LocalPlayer) {
            cir.setReturnValue(true);
        }
    }
}
