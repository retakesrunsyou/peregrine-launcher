package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;

/** Your name tag: shown over your own head in third person. */
@Mixin(targets = {"net.minecraft.client.renderer.entity.player.AvatarRenderer", "net.minecraft.client.renderer.entity.LivingEntityRenderer"})
public abstract class OwnNameShowMixin {

    @Inject(method = "shouldShowName", at = @At("RETURN"), cancellable = true)
    private void peregrine$own(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) net.minecraft.world.entity.Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (Hooks.ownNameTag && entity == mc.player && !mc.options.getCameraType().isFirstPerson()
                && !mc.options.hideGui) {
            cir.setReturnValue(true);
        }
    }
}
