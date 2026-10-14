package net.peregrine.client.v1_21_9.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
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

    @Inject(method = "render", at = @At("HEAD"))
    private void peregrine$in(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics g) {
        float s = Hooks.stretch;
        peregrine$stretched = Hooks.stretchHud && s > 1f;
        if (peregrine$stretched) {
            g.pose().pushMatrix(); g.pose().scale(s, 1f);
            Hooks.stretchingHud = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void peregrine$out(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics g) {
        if (peregrine$stretched) {
            peregrine$stretched = false;
            Hooks.stretchingHud = false;
            g.pose().popMatrix();
        }
    }
}
