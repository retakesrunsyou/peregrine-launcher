package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.v26_2.Extras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom crosshair: draws Peregrine's in place of Minecraft's when it's switched on. */
@Mixin(Hud.class)
public abstract class CrosshairMixin {

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void peregrine$crosshair(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (Extras.crosshair(graphics)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void peregrine$cleanEdges(GuiGraphicsExtractor graphics, net.minecraft.world.entity.Entity camera, CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        if (pc != null && pc.on("clean_edges")) {
            ci.cancel();
        }
    }
}
