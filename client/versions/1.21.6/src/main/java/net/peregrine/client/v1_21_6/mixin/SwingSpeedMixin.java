package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swing speed: your own arm's swing animation is longer or shorter. Only on your screen. */
@Mixin(LivingEntity.class)
public abstract class SwingSpeedMixin {

    @Inject(method = {"getCurrentSwingDuration", "getModifiedSwingDuration"}, at = @At("RETURN"), cancellable = true)
    private void peregrine$speed(CallbackInfoReturnable<Integer> cir) {
        if (Hooks.swingSpeed != 1f && (Object) this instanceof LocalPlayer) {
            cir.setReturnValue(Hooks.swingDuration(cir.getReturnValue()));
        }
    }
}
