package net.peregrine.client.v26_1.mixin;

import net.peregrine.client.v26_1.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Frame generation: the game draws on a shared context, and the presenter thread owns the window. */
@Mixin(targets = {"com.mojang.blaze3d.opengl.GlDevice"})
public abstract class FrameGenDeviceMixin {

    @Redirect(method = "<init>", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V", remap = false))
    private void peregrine$context(long window) {
        FrameGen.makeCurrent(window);
    }

    @Redirect(method = "setVsync", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapInterval(I)V", remap = false))
    private void peregrine$vsync(int interval) {
        FrameGen.swapInterval(interval);
    }

    @Redirect(method = "presentFrame", require = 0,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private void peregrine$swap(long window) {
        FrameGen.present(window);
    }
}
