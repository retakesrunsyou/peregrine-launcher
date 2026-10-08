package net.peregrine.client.v26_1;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.peregrine.client.core.Peregrine;
import org.lwjgl.glfw.GLFW;

/** Minecraft 1.21.1 entry point: connects the shared core to the game. */
public final class PeregrineClientMod implements ClientModInitializer {

    static KeyMapping menuKey;
    static KeyMapping zoomKey;

    @Override
    public void onInitializeClient() {
        Peregrine.init(new GamePlatform());

        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.peregrine.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.peregrine.zoom", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C,
                KeyMapping.Category.MISC));

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

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("peregrine", "hud"), (graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
                return;
            }
            Peregrine.get().renderHud(new GuiDraw(graphics));
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Peregrine.get().shutdown());
    }
}
