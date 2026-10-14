package net.peregrine.client.v1_21_6.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/** Item physics: dropped items stop bobbing and spinning and lie flat on the ground. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;renderMultipleFromCount(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/ItemClusterRenderState;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/phys/AABB;)V"))
    private void peregrine$lieFlat(ItemEntityRenderState state, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci, @Local AABB box) {
        if (!Hooks.itemPhysics) {
            return;
        }
        if (Hooks.itemPhysicsHits < 1000) {
            Hooks.itemPhysicsHits++;
        }
        poseStack.translate(0.0F, -Hooks.bob(state.ageInTicks, state.bobOffset), 0.0F);  // no bobbing
        poseStack.mulPose(Axis.YP.rotation(state.bobOffset - ItemEntity.getSpin(state.ageInTicks, state.bobOffset)));
        // Lay it down so its lowest point just touches the ground.
        float lift = -((float) box.minY) + 0.0625F;
        poseStack.translate(0.0F, 0.01F - (float) box.minZ - lift, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
    }
}
