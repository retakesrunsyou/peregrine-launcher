package net.peregrine.client.v1_21_2.mixin;

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
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;

/** Hand position: moves, turns and resizes what each hand holds. */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class HandPositionMixin {

    @Inject(method = "applyItemArmTransform", at = @At("TAIL"), require = 0)
    private void peregrine$place(PoseStack pose, HumanoidArm arm, float height, CallbackInfo ci) {
        float[] m = Hooks.handMain, o = Hooks.handOff;
        if (m == null && o == null) {
            return;
        }
        boolean main = arm == Minecraft.getInstance().options.mainHand().get();
        float[] v = main ? m : o;
        if (v == null) {
            return;
        }
        if (Hooks.handHits < 1000) {
            Hooks.handHits++;
        }
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * v[0], v[1], v[2]);
        if (v[4] != 0f) {
            pose.mulPose(Axis.YP.rotationDegrees(side * v[4]));
        }
        if (v[3] != 1f) {
            pose.scale(v[3], v[3], v[3]);
        }
    }
}
