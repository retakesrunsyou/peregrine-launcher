package net.peregrine.client.v1_21_6.mixin;

import net.minecraft.world.entity.Entity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.state.HitboxRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hitboxes: their color and the look line (the boxes themselves are filtered in HitboxFilterMixin). */
@Mixin(targets = {"net.minecraft.client.renderer.entity.EntityRenderDispatcher"})
public abstract class HitboxStateMixin {

    @ModifyVariable(method = "renderHitbox", at = @At("HEAD"), argsOnly = true)
    private static HitboxRenderState peregrine$color(HitboxRenderState s) {
        int c = Hooks.hitboxColor;
        if (c == 0 || s.red() != 1.0F || s.green() != 1.0F || s.blue() != 1.0F) {
            return s;  // Minecraft's own color, or not the main box (the red eye line stays red)
        }
        return new HitboxRenderState(s.x0(), s.y0(), s.z0(), s.x1(), s.y1(), s.z1(), s.offsetX(), s.offsetY(), s.offsetZ(),
                ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
    }

    @WrapWithCondition(method = "renderHitboxesAndViewVector", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderVector(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Vector3f;Lnet/minecraft/world/phys/Vec3;I)V"))
    private static boolean peregrine$lookLine(PoseStack poseStack, VertexConsumer buffer, Vector3f from, Vec3 dir, int color) {
        return Hooks.hitboxLookLine;
    }
}
