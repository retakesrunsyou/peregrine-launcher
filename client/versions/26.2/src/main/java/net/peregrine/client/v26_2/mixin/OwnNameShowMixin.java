package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Your name tag: shown over your own head in third person. (The player renderer's
 * own check builds on this one, and players always pass the rest of it.)
 */
@Mixin(LivingEntityRenderer.class)
public abstract class OwnNameShowMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
    private void peregrine$own(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (Hooks.ownNameTag && entity == mc.player && !mc.options.getCameraType().isFirstPerson()
                && !net.peregrine.client.v26_2.Screens.hudHidden()) {
            cir.setReturnValue(true);
        }
    }
}
