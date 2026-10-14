package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.v1_21_1.Extras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom crosshair: draws Peregrine's in place of Minecraft's when it's switched on. */
@Mixin(Gui.class)
public abstract class CrosshairMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void peregrine$crosshair(GuiGraphics graphics, DeltaTracker delta, CallbackInfo ci) {
        if (Extras.crosshair(graphics)) {
            ci.cancel();
        }
    }
}
