package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.peregrine.client.v26_1.ReconnectHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto reconnect: the disconnected screen counts down every tick. */
@Mixin(Screen.class)
public abstract class ReconnectTickMixin {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void peregrine$tick(CallbackInfo ci) {
        if (this instanceof ReconnectHolder) {
            ((ReconnectHolder) this).peregrine$reconnectTick();
        }
    }
}
