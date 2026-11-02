package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.platform.Window;
import net.peregrine.client.v26_1.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame generation: the presenter lets go of the window before the game closes it. */
@Mixin(Window.class)
public abstract class FrameGenWindowMixin {

    @Inject(method = "close", at = @At("HEAD"), require = 0)
    private void peregrine$close(CallbackInfo ci) {
        FrameGen.shutdown();
    }
}
