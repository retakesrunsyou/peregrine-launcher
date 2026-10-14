package net.peregrine.client.v26_2.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import net.peregrine.client.v26_2.PlaceAnim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block place animation: draws the growing blocks alongside the block-breaking cracks. */
@Mixin(LevelRenderer.class)
public abstract class PlaceAnimDrawMixin {

    @Inject(method = "submitBlockDestroyAnimation", at = @At("HEAD"))
    private void peregrine$grow(PoseStack pose, SubmitNodeCollector out, LevelRenderState state, CallbackInfo ci) {
        Vec3 cam = state.cameraRenderState.pos;
        PlaceAnim.draw(pose, out, cam.x, cam.y, cam.z);
    }
}
