package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.modules.CleanOverlays;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clean overlays: skips the pumpkin mask and powder snow frost drawn over the screen. */
@Mixin(Gui.class)
public abstract class CleanOverlaysMixin {

    @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$overlay(GuiGraphicsExtractor graphics, Identifier texture, float alpha, CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        Module m = pc == null ? null : pc.module("clean_overlays");
        if (m instanceof CleanOverlays && texture != null && ((CleanOverlays) m).hides(texture.getPath())) {
            ci.cancel();
        }
    }
}
