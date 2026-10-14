package net.peregrine.client.v1_21_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.v1_21_1.Extras;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.sugar.Local;

/** Your name tag: the Peregrine logo, a divider, then your name. */
@Mixin(EntityRenderer.class)
public abstract class OwnNameLogoMixin {

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component peregrine$logo(Component name, @Local(argsOnly = true) Entity entity) {
        return entity == Minecraft.getInstance().player ? Extras.withLogo(name) : name;
    }
}
