package net.peregrine.client.v1_21_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire: the flames on your screen sit lower, so you can see while burning. */
@Mixin(ScreenEffectRenderer.class)
public abstract class LowFireMixin {

    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void peregrine$lower(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        poseStack.pushPose();
        float drop = Hooks.fireDrop;
        if (drop > 0) {
            if (Hooks.fireHits < 1000) {
                Hooks.fireHits++;
            }
            poseStack.translate(0.0F, -drop, 0.0F);
        }
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void peregrine$back(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        poseStack.popPose();
    }
}
