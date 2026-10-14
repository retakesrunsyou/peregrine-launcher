package net.peregrine.client.v1_21_1.mixin;

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
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** MC Tiers: a player's tier before their name above their head. */
@Mixin(EntityRenderer.class)
public abstract class TierNameMixin {

    @ModifyVariable(method = "renderNameTag", at = @At("HEAD"), argsOnly = true)
    private Component peregrine$tier(Component name, @Local(argsOnly = true) Entity entity) {
        return entity instanceof Player ? peregrine$with(name, entity) : name;
    }

    @Unique
    private static Component peregrine$with(Component name, Entity entity) {
        if (!Hooks.tiersOverhead()) {
            return name;
        }
        String uuid = entity.getUUID().toString();
        String tier = Hooks.tierText(uuid);
        if (tier == null) {
            return name;
        }
        int c = Hooks.tierColor(uuid);
        return Component.literal(tier).withStyle(st -> st.withColor(c).withBold(true))
                .append(Component.literal(" | ").withStyle(net.minecraft.ChatFormatting.DARK_GRAY)).append(name);
    }
}
