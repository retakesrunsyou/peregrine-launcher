package net.peregrine.client.v1_21_9.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    /** A never-ending effect, so the game's own night vision math never sees a missing one. */
    @Unique
    private static MobEffectInstance peregrine$endless;

    @Inject(method = "getEffect", at = @At("HEAD"), cancellable = true)
    private void peregrine$nightVisionEffect(Holder<MobEffect> effect, CallbackInfoReturnable<MobEffectInstance> cir) {
        if (Hooks.nightVision && effect == MobEffects.NIGHT_VISION && (Object) this instanceof LocalPlayer) {
            if (peregrine$endless == null) {
                peregrine$endless = new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false);
            }
            cir.setReturnValue(peregrine$endless);
        }
    }
}
