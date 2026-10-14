package net.peregrine.client.v1_21_9.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Unique;

/** Peregrine badges before other players' names above their heads (yours has the logo already). */
@Mixin(EntityRenderer.class)
public abstract class BadgeNameMixin {

    @ModifyReturnValue(method = "getNameTag", at = @At("RETURN"))
    private Component peregrine$badge(Component name, Entity entity) {
        return peregrine$badged(name, entity);
    }

    @Unique
    private static Component peregrine$badged(Component name, Entity entity) {
        if (!(entity instanceof Player) || entity == Minecraft.getInstance().player || name == null) {
            return name;
        }
        String badge = Hooks.badgeFor(entity.getUUID().toString(), false);
        return badge == null ? name : Component.literal(badge + " ").append(name);
    }
}
