package net.peregrine.client.v26_2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Matrix4f;

/** Stretched view on 26.x: the world's perspective is widened (the GUI's flat projection is left alone). */
@Mixin(targets = {"net.minecraft.client.renderer.Projection"})
public abstract class StretchMixin {

    @Shadow
    public abstract float fov();

    @Unique
    private float peregrine$applied = 1f;
    @Unique
    private long peregrine$bump;

    @Inject(method = "getMatrix(Lorg/joml/Matrix4f;)Lorg/joml/Matrix4f;", at = @At("RETURN"), require = 0)
    private void peregrine$stretch(Matrix4f dest, CallbackInfoReturnable<Matrix4f> cir) {
        float s = Hooks.stretch;
        if (s != 1f && this.fov() > 0f && cir.getReturnValue() != null) {
            if (Hooks.stretchHits < 1000) {
                Hooks.stretchHits++;
            }
            cir.getReturnValue().scaleLocal(s, 1f, 1f);
        }
    }

    /** A changed stretch counts as a new matrix, so the game picks it up straight away. */
    @Inject(method = "getMatrixVersion()J", at = @At("RETURN"), cancellable = true, require = 0)
    private void peregrine$version(CallbackInfoReturnable<Long> cir) {
        float s = Hooks.stretch;
        if (s != peregrine$applied) {
            peregrine$applied = s;
            peregrine$bump++;
        }
        if (peregrine$bump != 0) {
            cir.setReturnValue(cir.getReturnValue() + peregrine$bump * 1_000_003L);
        }
    }
}
