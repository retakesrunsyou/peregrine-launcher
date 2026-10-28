package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.peregrine.client.core.Reconnect;
import net.peregrine.client.v26_1.ReconnectHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto reconnect: the countdown button on the "disconnected" screen, and the rejoin itself. */
@Mixin(DisconnectedScreen.class)
public abstract class ReconnectScreenMixin extends Screen implements ReconnectHolder {

    @Shadow
    @Final
    private Screen parent;

    @Shadow
    @Final
    private DisconnectionDetails details;

    @Unique
    private boolean peregrine$decided;

    @Unique
    private Button peregrine$button;

    protected ReconnectScreenMixin() {
        super(null);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void peregrine$button(CallbackInfo ci) {
        try {
            if (!peregrine$decided) {
                peregrine$decided = true;
                Reconnect.dropped(details == null || details.reason() == null ? "" : details.reason().getString());
            }
            String label = Reconnect.label();
            if (label == null) {
                return;
            }
            peregrine$button = addRenderableWidget(Button.builder(Component.literal(label), b -> {
                if (Reconnect.waiting()) {
                    Reconnect.cancel();
                }
                b.setMessage(Component.literal(String.valueOf(Reconnect.label())));
            }).bounds(width / 2 - 120, height - 32, 240, 20).build());
        } catch (Throwable e) {
            System.err.println("[Peregrine] auto reconnect: " + e);
        }
    }

    @Override
    public void peregrine$reconnectTick() {
        try {
            if (peregrine$button != null) {
                String label = Reconnect.label();
                peregrine$button.setMessage(Component.literal(label == null ? "" : label));
            }
            if (Reconnect.due() && Reconnect.address() != null) {
                String a = Reconnect.address(), n = Reconnect.name();
                ServerData data = new ServerData(n == null ? a : n, a, ServerData.Type.OTHER);
                ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(a), data, false, null);
            }
        } catch (Throwable e) {
            Reconnect.cancel();
            System.err.println("[Peregrine] auto reconnect: " + e);
        }
    }
}
