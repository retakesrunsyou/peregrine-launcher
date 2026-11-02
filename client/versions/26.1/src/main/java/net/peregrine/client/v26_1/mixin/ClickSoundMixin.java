package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.peregrine.client.core.modules.ClickSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Click sounds: every key press and release. */
@Mixin(targets = {"net.minecraft.client.KeyboardHandler"})
public abstract class ClickSoundMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void peregrine$click(long window, int action, KeyEvent event, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (window == mc.getWindow().handle()) {
                ClickSounds.key(event.key(), action, mc.screen != null);
            }
        } catch (Throwable ignored) {
            // never in the way of typing
        }
    }
}
