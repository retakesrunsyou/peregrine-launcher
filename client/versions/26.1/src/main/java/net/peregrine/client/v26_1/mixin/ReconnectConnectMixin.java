package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.peregrine.client.core.Reconnect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto reconnect: remembers the server being joined (the one to come back to). */
@Mixin(ConnectScreen.class)
public abstract class ReconnectConnectMixin {

    @Inject(method = "startConnecting", at = @At("HEAD"), require = 0)
    private static void peregrine$joining(Screen parent, Minecraft mc, ServerAddress address, ServerData data, boolean quick,
                                          TransferState transfer, CallbackInfo ci) {
        if (data != null && !data.isLan()) {
            Reconnect.joining(data.ip != null ? data.ip : address.toString(), data.name);
        }
    }
}
