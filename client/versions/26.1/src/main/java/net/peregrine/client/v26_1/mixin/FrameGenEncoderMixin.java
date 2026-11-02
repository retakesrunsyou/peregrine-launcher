package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.peregrine.client.v26_1.FrameGen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame generation: the finished frame goes to the presenter instead of straight to the window. */
@Mixin(targets = {"com.mojang.blaze3d.opengl.GlCommandEncoder"})
public abstract class FrameGenEncoderMixin {

    @Inject(method = "presentTexture", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$present(GpuTextureView view, CallbackInfo ci) {
        if (FrameGen.capturePresent(view)) {
            ci.cancel();
        }
    }
}
