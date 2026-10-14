package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.peregrine.client.core.MotionBlurFx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion blur: our post effect (each frame mixed with the last) whenever no other effect is showing. */
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {

    @Shadow PostChain postEffect;
    @Shadow private boolean effectActive;

    @Shadow
    protected abstract void loadEffect(ResourceLocation id);

    @Shadow
    public abstract void shutdownEffect();

    @Unique
    private static String peregrine$loaded;

    @Inject(method = "render", at = @At("HEAD"))
    private void peregrine$motionBlur(CallbackInfo ci) {
        String want = MotionBlurFx.effect("motion_blur_");
        boolean ours = this.postEffect != null && peregrine$loaded != null
                && this.postEffect.getName().contains(peregrine$loaded);
        if (want != null) {
            if (this.postEffect == null || (ours && !want.equals(peregrine$loaded))) {
                peregrine$loaded = want;
                this.loadEffect(ResourceLocation.fromNamespaceAndPath("peregrine-client", "shaders/post/" + want + ".json"));
            }
            if (ours || this.postEffect != null && this.postEffect.getName().contains(want)) {
                this.effectActive = true;
            }
        } else if (ours) {
            peregrine$loaded = null;
            this.shutdownEffect();
        }
    }
}
