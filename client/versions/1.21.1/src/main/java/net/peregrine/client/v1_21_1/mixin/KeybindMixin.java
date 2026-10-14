package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.KeyboardHandler;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keybinds: tells Peregrine Client about each key press while playing, as it happens. */
@Mixin(KeyboardHandler.class)
public abstract class KeybindMixin {

    @Inject(method = "keyPress(JIIII)V", at = @At("HEAD"))
    private void peregrine$key(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        if (pc != null && action == org.lwjgl.glfw.GLFW.GLFW_PRESS && net.minecraft.client.Minecraft.getInstance().screen == null) {
            pc.onKey(key);
        }
    }
}
