package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** Peregrine badges before names in the tab list. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabBadgeMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component peregrine$badge(Component name, PlayerInfo info) {
        if (name == null || info.getProfile() == null) {
            return name;
        }
        var id = info.getProfile().id();
        var me = Minecraft.getInstance().player;
        String badge = Hooks.badgeFor(String.valueOf(id), me != null && me.getUUID().equals(id));
        return badge == null ? name : Component.literal(badge + " ").append(name);
    }
}
