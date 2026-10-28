package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.v26_1.WorldScale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** World resolution: the world (not the hand or HUD) is drawn smaller and scaled up. */
@Mixin(GameRenderer.class)
public abstract class WorldScaleMixin {

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V"),
            require = 0)
    private void peregrine$smaller(CallbackInfo ci) {
        WorldScale.before();
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZLnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;)V",
            shift = At.Shift.AFTER), require = 0)
    private void peregrine$scaleUp(CallbackInfo ci) {
        WorldScale.after();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void peregrine$safety(CallbackInfo ci) {
        WorldScale.after();   // in case the world drawing returned early
    }
}
