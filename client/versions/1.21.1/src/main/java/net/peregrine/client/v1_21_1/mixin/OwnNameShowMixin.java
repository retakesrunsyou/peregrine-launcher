package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Your name tag: shown over your own head in third person. */
@Mixin(LivingEntityRenderer.class)
public abstract class OwnNameShowMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("RETURN"), cancellable = true)
    private void peregrine$own(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (Hooks.ownNameTag && entity == mc.player && !mc.options.getCameraType().isFirstPerson()
                && !mc.options.hideGui) {
            cir.setReturnValue(true);
        }
    }
}
