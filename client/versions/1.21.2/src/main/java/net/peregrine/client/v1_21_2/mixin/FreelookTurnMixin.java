package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: mouse movement turns the camera, and you keep facing the same way. */
@Mixin(Entity.class)
public abstract class FreelookTurnMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void peregrine$turnCamera(double yaw, double pitch, CallbackInfo ci) {
        if (Hooks.freelook && (Object) this instanceof LocalPlayer) {
            Hooks.turnCamera(yaw, pitch);
            ci.cancel();
        }
    }
}
