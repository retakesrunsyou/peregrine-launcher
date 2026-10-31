package net.peregrine.client.v26_1.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.peregrine.client.core.modules.SkeletonLook;
import net.peregrine.client.v26_1.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Skeleton look: your arm in first person is bone. */
@Mixin(AvatarRenderer.class)
public abstract class SkeletonHandMixin {

    @WrapOperation(method = "renderHand", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void peregrine$boneArm(SubmitNodeCollector collector, ModelPart part, PoseStack pose, RenderType type, int light, int overlay,
                                   TextureAtlasSprite sprite, Operation<Void> original) {
        if (SkeletonLook.arm()) {
            PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
            if (SkeletonDraw.hand(part, part == model.rightArm, pose, collector, light, overlay)) {
                return;
            }
        }
        original.call(collector, part, pose, type, light, overlay, sprite);
    }
}
