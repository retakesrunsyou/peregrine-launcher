package net.peregrine.client.v26_1.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.peregrine.client.core.modules.SkeletonLook;
import net.peregrine.client.v26_1.SkeletonDraw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Skeleton look: players' models are drawn as bone; only what they hold (and armor, if asked) stays on them. */
@Mixin(LivingEntityRenderer.class)
public abstract class SkeletonRenderMixin {

    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
            + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @WrapOperation(method = SUBMIT, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;"
                    + "ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
    private void peregrine$bones(SubmitNodeCollector collector, Model<?> model, Object state, PoseStack pose, RenderType type,
                                 int light, int overlay, int tint, TextureAtlasSprite sprite, int outline,
                                 ModelFeatureRenderer.CrumblingOverlay crumbling, Operation<Void> original) {
        if ((Object) this instanceof AvatarRenderer && SkeletonLook.on() && model instanceof PlayerModel
                && state instanceof AvatarRenderState
                && SkeletonDraw.player((PlayerModel) model, (AvatarRenderState) state, pose, collector, light, overlay, tint)) {
            return;
        }
        original.call(collector, model, state, pose, type, light, overlay, tint, sprite, outline, crumbling);
    }

    @WrapWithCondition(method = SUBMIT, require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private boolean peregrine$layers(RenderLayer<?, ?> layer, PoseStack pose, SubmitNodeCollector collector, int light,
                                     EntityRenderState state, float yRot, float xRot) {
        if (!((Object) this instanceof AvatarRenderer) || !SkeletonLook.on() || !(state instanceof AvatarRenderState)) {
            return true;
        }
        // skin layers (cape, ears, arrows stuck in the skin...) go; held items stay, armor if asked
        return layer instanceof ItemInHandLayer || (layer instanceof HumanoidArmorLayer && SkeletonLook.showArmor());
    }
}
