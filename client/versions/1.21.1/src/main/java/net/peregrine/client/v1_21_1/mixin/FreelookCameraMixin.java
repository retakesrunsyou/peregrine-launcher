package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.Camera;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Freelook: while it's on, the camera faces its own way instead of where you're looking. */
@Mixin(Camera.class)
public abstract class FreelookCameraMixin {

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float peregrine$yaw(float yaw) {
        if (!Hooks.freelook) {
            return yaw;
        }
        if (Hooks.cameraHits < 1000) {
            Hooks.cameraHits++;
        }
        return Hooks.camYaw;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float peregrine$pitch(float pitch) {
        return Hooks.freelook ? Hooks.camPitch : pitch;
    }
}
