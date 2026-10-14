package net.peregrine.client.v26_1.mixin;

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

/** Smooth hotbar: the selection box slides to the new slot. */
@Mixin(targets = {"net.minecraft.client.gui.Gui"})
public abstract class SmoothHotbarMixin {

    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 1), index = 2, require = 0)
    private int peregrine$slide0(int x) {
        return Hooks.hotbarSpeed > 0f ? net.peregrine.client.core.modules.SmoothHotbar.slide(x) : x;
    }

    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1), index = 2, require = 0)
    private int peregrine$slide1(int x) {
        return Hooks.hotbarSpeed > 0f ? net.peregrine.client.core.modules.SmoothHotbar.slide(x) : x;
    }
}
