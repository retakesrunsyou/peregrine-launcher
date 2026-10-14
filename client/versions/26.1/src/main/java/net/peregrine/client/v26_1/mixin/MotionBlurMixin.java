package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.peregrine.client.core.MotionBlurFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion blur: our post effect (each frame mixed with the last) whenever no other effect is showing. */
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {

    @Shadow private Identifier postEffectId;
    @Shadow private boolean effectActive;

    @Inject(method = "render", at = @At("HEAD"))
    private void peregrine$motionBlur(CallbackInfo ci) {
        String want = MotionBlurFx.effect("motion_blur_");
        boolean ours = this.postEffectId != null && this.postEffectId.getNamespace().equals("peregrine-client")
                && MotionBlurFx.ours(this.postEffectId.getPath());
        if (want != null) {
            if (this.postEffectId == null || (ours && !this.postEffectId.getPath().equals(want))) {
                this.postEffectId = Identifier.fromNamespaceAndPath("peregrine-client", want);
                this.effectActive = true;
            } else if (ours) {
                this.effectActive = true;
            }
        } else if (ours) {
            this.postEffectId = null;
        }
    }
}
