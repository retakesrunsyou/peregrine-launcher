package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.core.Hooks;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stretched view: the world's projection is widened, like a 4:3 game stretched across the screen. */
@Mixin(GameRenderer.class)
public abstract class StretchMixin {

    @Inject(method = "getProjectionMatrix(F)Lorg/joml/Matrix4f;", at = @At("RETURN"))
    private void peregrine$stretch(CallbackInfoReturnable<Matrix4f> cir) {
        float s = Hooks.stretch;
        if (s != 1f && cir.getReturnValue() != null) {
            if (Hooks.stretchHits < 1000) {
                Hooks.stretchHits++;
            }
            cir.getReturnValue().scaleLocal(s, 1f, 1f);
        }
    }
}
