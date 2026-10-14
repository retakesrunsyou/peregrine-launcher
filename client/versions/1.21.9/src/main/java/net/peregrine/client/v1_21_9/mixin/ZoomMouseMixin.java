package net.peregrine.client.v1_21_9.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: scroll while zooming to zoom further in or out, and turn slower the further in you are. */
@Mixin(MouseHandler.class)
public abstract class ZoomMouseMixin {

    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), require = 0)
    private void peregrine$zoomTurn(CallbackInfo ci) {
        double k = Hooks.zoomTurn();
        if (k != 1.0) {
            accumulatedDX *= k;
            accumulatedDY *= k;
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$zoomScroll(CallbackInfo ci, @Local(argsOnly = true, ordinal = 1) double y) {
        if (Hooks.zoomNow > 1.01 && Minecraft.getInstance().screen == null && Hooks.zoomScroll(y)) {
            ci.cancel();  // the hotbar stays where it is
        }
    }
}
