package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.peregrine.client.v26_1.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pickaxe look in first person: your pickaxe drawn as the look you picked. */
@Mixin(targets = {"net.minecraft.client.renderer.ItemInHandRenderer"})
public abstract class PickaxeLookMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$look(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                SubmitNodeCollector collector, int light, CallbackInfo ci) {
        if (SkeletonDraw.pickaxe(stack, context, pose, collector, light, OverlayTexture.NO_OVERLAY)) {
            ci.cancel();
        }
    }
}
