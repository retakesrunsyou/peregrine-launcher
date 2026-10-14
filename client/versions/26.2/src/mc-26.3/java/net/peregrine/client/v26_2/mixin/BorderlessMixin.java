package net.peregrine.client.v26_2.mixin;

import com.mojang.blaze3d.platform.Window;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Borderless fullscreen on 26.3: its SDL window already has a borderless desktop fullscreen,
 * used whenever "exclusive" fullscreen is off. With Borderless on, fullscreen (F11) always
 * uses it, so alt-tab is instant and the screen never switches modes.
 */
@Mixin(Window.class)
public abstract class BorderlessMixin {

    @Shadow
    private boolean exclusiveFullscreen;

    @Inject(method = "setMode", at = @At("HEAD"), require = 0)
    private void peregrine$borderless(CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        if (pc != null && pc.on("borderless") && this.exclusiveFullscreen) {
            this.exclusiveFullscreen = false;
            if (Hooks.borderlessHits < 1000) {
                Hooks.borderlessHits++;
            }
        }
    }
}
