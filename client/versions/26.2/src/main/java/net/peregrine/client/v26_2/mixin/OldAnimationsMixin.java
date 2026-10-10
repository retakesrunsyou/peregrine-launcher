package net.peregrine.client.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.7 animations, in first person only: swing while using items, items held lower and further out. */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer", "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer"})
public abstract class OldAnimationsMixin {

    @Shadow
    protected abstract void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float swing);

    @Inject(method = "applyEatTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)V", at = @At("TAIL"))  // 26.2 (26.3 changed it)
    private void peregrine$swingWhileUsing(PoseStack poseStack, float partial, HumanoidArm arm, ItemStack stack, Player player,
                                           CallbackInfo ci) {
        if (Hooks.oldSwing) {
            float swing = net.peregrine.client.v26_2.Compat.attackAnim(player, partial);
            if (swing > 0) {
                this.applyItemArmAttackTransform(poseStack, arm, swing);
            }
        }
    }

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void peregrine$position(PoseStack poseStack, HumanoidArm arm, float height, CallbackInfo ci) {
        if (Hooks.oldPosition) {
            if (Hooks.animationHits < 1000) {
                Hooks.animationHits++;
            }
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(side * 0.04F, -0.06F, -0.05F);
        }
    }
}
