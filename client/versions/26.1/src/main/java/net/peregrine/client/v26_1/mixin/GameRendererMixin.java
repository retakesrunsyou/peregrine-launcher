package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: narrows the field of view while the zoom key is held. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = {"getFov", "computeFov"}, at = @At("RETURN"), cancellable = true)
    private void peregrine$zoom(Camera camera, float partialTick, boolean useFovSetting,
                                CallbackInfoReturnable<Float> cir) {
        Peregrine pc = Peregrine.get();
        if (pc != null) {
            double divisor = pc.zoomDivisor();
            if (divisor != 1.0) {
                cir.setReturnValue((float) (cir.getReturnValue() / divisor));
            }
        }
    }
}
