package net.peregrine.client.v26_2.mixin;

import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.peregrine.client.core.MotionBlurFx;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Motion blur on 26.3: our post effect (each frame mixed with the last) goes first in the list of
 * effects the frame asks for, before Minecraft's own end-of-frame effect.
 */
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {

    @Shadow @Final private List<Identifier> requestedPostEffects;

    @Inject(method = "extract", at = @At("HEAD"))
    private void peregrine$motionBlur(CallbackInfo ci) {
        String want = MotionBlurFx.effect("motion_blur_sdl_");
        Identifier keep = null;
        for (int i = this.requestedPostEffects.size() - 1; i >= 0; i--) {
            Identifier id = this.requestedPostEffects.get(i);
            if (id.getNamespace().equals("peregrine-client") && MotionBlurFx.ours(id.getPath())) {
                if (want != null && id.getPath().equals(want) && keep == null) {
                    keep = id;
                } else {
                    this.requestedPostEffects.remove(i);
                }
            }
        }
        if (want != null && keep == null) {
            this.requestedPostEffects.add(0, Identifier.fromNamespaceAndPath("peregrine-client", want));
        }
    }
}
