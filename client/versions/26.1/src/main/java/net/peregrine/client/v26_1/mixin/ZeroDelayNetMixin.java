package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.peregrine.client.v26_1.ZeroDelayHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zero delay: the server confirmed a crystal is gone (so it isn't put back). */
@Mixin(ClientPacketListener.class)
public abstract class ZeroDelayNetMixin {

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"), require = 0)
    private void peregrine$gone(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        ZeroDelayHooks.removed(packet.getEntityIds());
    }
}
