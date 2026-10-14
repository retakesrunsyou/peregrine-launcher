package net.peregrine.client.v1_21_9.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.v1_21_9.PlaceAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block place animation: draws the growing blocks where the block outline is drawn. */
@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public abstract class PlaceAnimDrawMixin {

    @Inject(method = "renderBlockOutline", at = @At("HEAD"))
    private void peregrine$grow(MultiBufferSource.BufferSource buffers, PoseStack pose, boolean translucent, LevelRenderState state, CallbackInfo ci) {
        if (!translucent) {
            Vec3 cam = state.cameraRenderState.pos;
            PlaceAnim.draw(pose, buffers, cam.x, cam.y, cam.z);
        }
    }
}
