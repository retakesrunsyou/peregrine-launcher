package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Block outline: the color picked in the menu. */
@Mixin(LevelRenderer.class)
public abstract class OutlineColorMixin {

    @ModifyArgs(method = "renderHitOutline", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderShape(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/shapes/VoxelShape;DDDFFFF)V"))
    private void peregrine$color(Args args) {
        int c = Hooks.outlineColor;
        if (c != 0 && !Hooks.drawingOres) {
            if (Hooks.outlineHits < 1000) {
                Hooks.outlineHits++;
            }
            args.set(6, ((c >> 16) & 0xFF) / 255f);
            args.set(7, ((c >> 8) & 0xFF) / 255f);
            args.set(8, (c & 0xFF) / 255f);
            args.set(9, ((c >>> 24) & 0xFF) / 255f);
        }
    }
}
