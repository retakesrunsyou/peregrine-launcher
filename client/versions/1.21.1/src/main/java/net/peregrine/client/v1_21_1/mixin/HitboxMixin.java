package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.world.entity.Entity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.v1_21_1.Extras;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hitboxes: which things show them, their color, and the look line. */
@Mixin(EntityRenderDispatcher.class)
public abstract class HitboxMixin {

    @Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true)
    private static void peregrine$filter(PoseStack poseStack, VertexConsumer buffer, Entity entity, float partial,
                                         float r, float g, float b, CallbackInfo ci) {
        if (Hooks.hitboxHits < 1000) {
            Hooks.hitboxHits++;
        }
        if (!Hooks.showHitbox(Extras.hitboxKind(entity))) {
            ci.cancel();
        }
    }

    @ModifyVariable(method = "renderHitbox", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static float peregrine$red(float r) {
        return Hooks.hitboxColor != 0 ? ((Hooks.hitboxColor >> 16) & 0xFF) / 255f : r;
    }

    @ModifyVariable(method = "renderHitbox", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private static float peregrine$green(float g) {
        return Hooks.hitboxColor != 0 ? ((Hooks.hitboxColor >> 8) & 0xFF) / 255f : g;
    }

    @ModifyVariable(method = "renderHitbox", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static float peregrine$blue(float b) {
        return Hooks.hitboxColor != 0 ? (Hooks.hitboxColor & 0xFF) / 255f : b;
    }

    @WrapWithCondition(method = "renderHitbox", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderVector(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Vector3f;Lnet/minecraft/world/phys/Vec3;I)V"))
    private static boolean peregrine$lookLine(PoseStack poseStack, VertexConsumer buffer, Vector3f from, Vec3 dir, int color) {
        return Hooks.hitboxLookLine;
    }
}
