package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.peregrine.client.core.modules.ClickSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Click sounds: every mouse button press and release. */
@Mixin(targets = {"net.minecraft.client.MouseHandler"})
public abstract class ClickSoundMouseMixin {

    @Inject(method = "onButton", at = @At("HEAD"), require = 0)
    private void peregrine$click(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (window == mc.getWindow().handle()) {
                ClickSounds.mouseButton(info.button(), action, mc.screen != null);
            }
        } catch (Throwable ignored) {
            // never in the way of clicking
        }
    }
}
