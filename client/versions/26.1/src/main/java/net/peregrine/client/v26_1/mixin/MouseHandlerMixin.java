package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.peregrine.client.core.Peregrine;
import org.lwjgl.glfw.GLFW;
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
        if (pc != null && action == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) {
            pc.onMouseButton(info.button());
        }
    }
}
