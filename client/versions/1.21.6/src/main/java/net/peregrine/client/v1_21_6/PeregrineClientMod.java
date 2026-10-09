package net.peregrine.client.v1_21_6;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.peregrine.client.core.Peregrine;
import org.lwjgl.glfw.GLFW;

/** Minecraft 1.21.1 entry point: connects the shared core to the game. */
public final class PeregrineClientMod implements ClientModInitializer {

    static KeyMapping menuKey;
    static KeyMapping zoomKey;
    static KeyMapping freelookKey;

    @Override
    public void onInitializeClient() {
        Peregrine.init(new GamePlatform());

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.peregrine.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                "key.categories.peregrine"));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.peregrine.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C,
                "key.categories.peregrine"));
        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.peregrine.freelook", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT,
                "key.categories.peregrine"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new PeregrineScreen());
                }
            }
            if (mc.screen instanceof TitleScreen && Peregrine.get().titleMenu().enabled()) {
                mc.setScreen(new PeregrineTitleScreen());
            }
            Peregrine.get().tick();
        });

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
                return;
            }
            Peregrine.get().renderHud(new GuiDraw(graphics));
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Peregrine.get().shutdown());
    }
}
