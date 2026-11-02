package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.v26_1.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HD look and frame generation: the world alone, before your hand and the HUD are drawn over it. */
@Mixin(GameRenderer.class)
public abstract class FrameGenWorldMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), require = 0)
    private void peregrine$beforeHand(CallbackInfo ci) {
        net.peregrine.client.v26_1.HdPass.apply();   // HD look first, so the kept world matches the frame
        FrameGen.captureWorld();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void peregrine$afterLevel(CallbackInfo ci) {
        net.peregrine.client.v26_1.HdPass.apply();   // when no hand was drawn (both do nothing if already done)
        FrameGen.captureWorld();
        net.peregrine.client.v26_1.HdPass.newFrame();
    }
}
