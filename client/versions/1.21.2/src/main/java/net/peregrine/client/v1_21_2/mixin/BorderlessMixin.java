package net.peregrine.client.v1_21_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;

/** Borderless fullscreen: F11 makes the window fill the screen without a mode switch (instant alt-tab). */
@Mixin(Window.class)
public abstract class BorderlessMixin {

    @Unique
    private static int[] peregrine$saved;

    @Inject(method = "toggleFullScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$borderless(CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        if (pc == null || (!pc.on("borderless") && peregrine$saved == null)) {
            return;
        }
        Window self = (Window) (Object) this;
        if (self.isFullscreen()) {
            return;  // real fullscreen on: let Minecraft leave it first
        }
        ci.cancel();
        long h = self.getWindow();
        if (peregrine$saved == null) {
            int[] x = new int[1], y = new int[1], w = new int[1], hh = new int[1];
            GLFW.glfwGetWindowPos(h, x, y);
            GLFW.glfwGetWindowSize(h, w, hh);
            peregrine$saved = new int[] {x[0], y[0], w[0], hh[0]};
            long monitor = GLFW.glfwGetPrimaryMonitor();
            int[] mx = new int[1], my = new int[1];
            GLFW.glfwGetMonitorPos(monitor, mx, my);
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            if (mode == null) {
                peregrine$saved = null;
                return;
            }
            GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowPos(h, mx[0], my[0]);
            GLFW.glfwSetWindowSize(h, mode.width(), mode.height());
        } else {
            int[] s = peregrine$saved;
            peregrine$saved = null;
            GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
            GLFW.glfwSetWindowSize(h, s[2], s[3]);
            GLFW.glfwSetWindowPos(h, s[0], s[1]);
        }
    }
}
