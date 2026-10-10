package net.peregrine.client.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Small totem: the totem that flies at you when one pops is drawn smaller. The
 * animation scales its pose once; that one scale is shrunk.
 */
@Mixin(targets = {"net.minecraft.client.renderer.GameRenderer", "net.minecraft.client.renderer.ScreenEffectRenderer"})
public abstract class TotemPopMixin {

    @WrapOperation(method = "renderItemActivationAnimation",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"))
    private void peregrine$smaller(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        float s = Hooks.totemPop;
        original.call(pose, x * s, y * s, z * s);
    }
}
