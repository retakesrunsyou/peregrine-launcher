package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.peregrine.client.core.Peregrine;
import com.mojang.blaze3d.platform.InputConstants;
import net.peregrine.client.v26_2.Screens;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts mouse clicks for the CPS display (only while playing, not in menus). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void peregrine$countClick(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        if (pc != null && action == InputConstants.PRESS && Screens.current() == null) {
            pc.onMouseButton(Screens.button(info.button()));
        }
    }
}
