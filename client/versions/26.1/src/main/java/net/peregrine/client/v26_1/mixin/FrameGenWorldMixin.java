package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.v26_1.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame generation: the world alone, before your hand and the HUD are drawn over it. */
@Mixin(GameRenderer.class)
public abstract class FrameGenWorldMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"), require = 0)
    private void peregrine$beforeHand(CallbackInfo ci) {
        FrameGen.captureWorld();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void peregrine$afterLevel(CallbackInfo ci) {
        FrameGen.captureWorld();   // when no hand was drawn (it does nothing if the world was already kept)
    }
}
