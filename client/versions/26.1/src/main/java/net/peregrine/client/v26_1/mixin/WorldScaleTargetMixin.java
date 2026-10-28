package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.peregrine.client.v26_1.WorldScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** World resolution: while the world is drawn, "the main picture" is the smaller one. */
@Mixin(Minecraft.class)
public abstract class WorldScaleTargetMixin {

    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$scaled(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget t = WorldScale.current();
        if (t != null) {
            cir.setReturnValue(t);
        }
    }
}
