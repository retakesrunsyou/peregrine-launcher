package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.v26_1.Panel3D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3D menu: the menu layer is drawn into the panel's picture, then the panel onto the frame. */
@Mixin(GameRenderer.class)
public abstract class Panel3DGuiMixin {

    @Inject(method = "render", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void peregrine$beforeGui(CallbackInfo ci) {
        Panel3D.beginGui();
    }

    @Inject(method = "render", require = 0, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void peregrine$afterGui(CallbackInfo ci) {
        Panel3D.endGui();
    }
}
