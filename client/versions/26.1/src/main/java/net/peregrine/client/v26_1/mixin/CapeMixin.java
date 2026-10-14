package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.peregrine.client.v26_1.Capes;

/** Peregrine capes: your chosen cape, and the capes of players on the Peregrine cosmetics list. */
@Mixin(AbstractClientPlayer.class)
public abstract class CapeMixin {

    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin peregrine$cape(PlayerSkin s) {
        if (s == null) {
            return s;
        }
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        String cape = Hooks.capeFor(self.getUUID().toString(), self == Minecraft.getInstance().player);
        if (cape == null) {
            return s;
        }
        var t = Capes.texture(cape);
        return new PlayerSkin(s.body(), t, t, s.model(), s.secure());
    }
}
