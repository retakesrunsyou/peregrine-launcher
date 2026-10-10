package net.peregrine.client.v1_21_9.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keybinds: tells Peregrine Client about each key press while playing, as it happens. */
@Mixin(KeyboardHandler.class)
public abstract class KeybindMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"))
    private void peregrine$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        if (pc != null && action == org.lwjgl.glfw.GLFW.GLFW_PRESS && net.minecraft.client.Minecraft.getInstance().screen == null) {
            pc.onKey(event.key());
        }
    }
}
