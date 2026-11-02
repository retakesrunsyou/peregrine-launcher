package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.peregrine.client.v26_1.Panel3D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 3D menu: while the menus are drawn, "the main picture" is the panel's. */
@Mixin(Minecraft.class)
public abstract class Panel3DTargetMixin {

    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$panel(CallbackInfoReturnable<RenderTarget> cir) {
        RenderTarget t = Panel3D.current();
        if (t != null) {
            cir.setReturnValue(t);
        }
    }
}
