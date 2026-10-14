package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stretched resolution, "Stretch the HUD too": the HUD is laid out narrower, then drawn stretched across. */
@Mixin(Gui.class)
public abstract class StretchHudMixin {

    @Unique
    private boolean peregrine$stretched;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void peregrine$in(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        float s = Hooks.stretch;
        peregrine$stretched = Hooks.stretchHud && s > 1f;
        if (peregrine$stretched) {
            g.pose().pushMatrix();
            g.pose().scale(s, 1f);
            Hooks.stretchingHud = true;
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void peregrine$out(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        if (peregrine$stretched) {
            peregrine$stretched = false;
            Hooks.stretchingHud = false;
            g.pose().popMatrix();
        }
    }
}
