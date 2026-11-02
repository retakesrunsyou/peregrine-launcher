package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.gui.Gui;
import net.peregrine.client.v26_1.Panel3D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D menu: the HUD steps aside while the panel is up (it would otherwise ride along on it). */
@Mixin(Gui.class)
public abstract class Panel3DHudMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$hide(CallbackInfo ci) {
        if (Panel3D.active()) {
            ci.cancel();
        }
    }
}
