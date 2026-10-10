package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.peregrine.client.v1_21_2.Extras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Your name tag: the Peregrine logo, a divider, then your name. */
@Mixin(EntityRenderer.class)
public abstract class OwnNameLogoMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void peregrine$logo(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (entity == Minecraft.getInstance().player && cir.getReturnValue() != null) {
            cir.setReturnValue(Extras.withLogo(cir.getReturnValue()));
        }
    }
}
