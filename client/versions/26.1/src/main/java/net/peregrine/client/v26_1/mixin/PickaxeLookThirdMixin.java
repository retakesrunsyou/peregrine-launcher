package net.peregrine.client.v26_1.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.v26_1.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pickaxe look in third person: your own pickaxe in your hand (others' stay as they are); with the Skeleton look, held items sit in the bony hand. */
@Mixin(ItemInHandLayer.class)
public abstract class PickaxeLookThirdMixin {

    @WrapOperation(method = "submitArmWithItem", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void peregrine$look(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector collector, int light, int overlay,
                                int outline, Operation<Void> original, @Local(argsOnly = true) ArmedEntityRenderState state,
                                @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) HumanoidArm arm) {
        // Skeleton look: a real arm is longer, so whatever's held moves down into the bony hand
        boolean bones = net.peregrine.client.core.modules.SkeletonLook.on()
                && state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState;
        if (bones) {
            pose.pushPose();
            pose.translate(0f, 0f, -1.5f / 16f);
        }
        try {
            if (SkeletonDraw.isYou(state) && SkeletonDraw.pickaxe(stack, arm == HumanoidArm.LEFT
                    ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, pose, collector, light, overlay)) {
                return;
            }
            original.call(item, pose, collector, light, overlay, outline);
        } finally {
            if (bones) {
                pose.popPose();
            }
        }
    }
}
