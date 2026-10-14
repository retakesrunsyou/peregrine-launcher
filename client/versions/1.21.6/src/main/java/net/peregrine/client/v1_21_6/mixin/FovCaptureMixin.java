package net.peregrine.client.v1_21_6.mixin;

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
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.peregrine.client.v1_21_6.Proj;

/** Remembers the world's field of view each frame, so labels can be placed over the world. */
@Mixin(GameRenderer.class)
public abstract class FovCaptureMixin {

    @Inject(method = "getFov(Lnet/minecraft/client/Camera;FZ)F", at = @At("RETURN"))
    private void peregrine$fov(Camera camera, float partial, boolean useSetting, CallbackInfoReturnable<Float> cir) {
        if (useSetting) {
            Proj.fov = cir.getReturnValue().floatValue();
        }
    }
}
