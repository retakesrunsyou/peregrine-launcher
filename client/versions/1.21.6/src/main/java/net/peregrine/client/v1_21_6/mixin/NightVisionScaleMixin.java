package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fullbright: full-strength night vision, with no flicker (the real effect has no timer here). */
@Mixin(GameRenderer.class)
public abstract class NightVisionScaleMixin {

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void peregrine$full(LivingEntity entity, float partial, CallbackInfoReturnable<Float> cir) {
        if (Hooks.nightVision && entity instanceof LocalPlayer) {
            if (Hooks.nightVisionHits < 1000) {
                Hooks.nightVisionHits++;
            }
            cir.setReturnValue(1.0F);
        }
    }
}
