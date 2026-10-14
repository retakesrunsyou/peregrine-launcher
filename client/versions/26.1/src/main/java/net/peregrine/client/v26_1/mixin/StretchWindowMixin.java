package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.platform.Window;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stretched resolution: while the HUD is drawn stretched, the GUI is narrower. */
@Mixin(Window.class)
public abstract class StretchWindowMixin {

    @Inject(method = "getGuiScaledWidth", at = @At("RETURN"), cancellable = true)
    private void peregrine$narrow(CallbackInfoReturnable<Integer> cir) {
        if (Hooks.stretchingHud) {
            cir.setReturnValue(Hooks.stretchedGuiWidth(cir.getReturnValueI()));
        }
    }
}
