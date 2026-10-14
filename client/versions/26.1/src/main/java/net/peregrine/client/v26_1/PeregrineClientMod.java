package net.peregrine.client.v26_1;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.peregrine.client.core.Peregrine;

/** Minecraft 1.21.1 entry point: connects the shared core to the game. */
public final class PeregrineClientMod implements ClientModInitializer {

    static KeyMapping menuKey;
    static KeyMapping zoomKey;
    static KeyMapping freelookKey;

    @Override
    public void onInitializeClient() {
        Peregrine.init(new GamePlatform());

        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.peregrine.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT,
                KeyMapping.Category.MISC));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.peregrine.zoom", InputConstants.Type.KEYSYM, InputConstants.KEY_C,
                KeyMapping.Category.MISC));
        freelookKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.peregrine.freelook", InputConstants.Type.KEYSYM, InputConstants.KEY_LALT,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (menuPressed(mc, Screens.current() == null) && Screens.current() == null) {
                Screens.open(new PeregrineScreen());
            }
            if (Screens.current() instanceof TitleScreen && Peregrine.get().titleMenu().enabled()) {
                Screens.open(new PeregrineTitleScreen());
            }
            if (net.peregrine.client.core.InventoryFx.closeDue()
                    && Screens.current() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> inv) {
                inv.onClose();  // the closing animation has played
            }
            Peregrine.get().tick();
            PlaceAnim.tick();
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("peregrine", "hud"), (graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            if (Screens.hudHidden()) {
                return;
            }
            if (mc.debugEntries.isOverlayVisible()) {
                Peregrine.get().renderDebug(new GuiDraw(graphics));  // Better F3, if it's on
                return;
            }
            Peregrine.get().renderHud(new GuiDraw(graphics));
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Peregrine.get().shutdown());
    }

    private static boolean menuWasDown, hadScreen = true;

    /** The menu key was pressed this tick. Reads the key itself as well as the binding, so the
     *  menu still opens when another mod uses the same key or swallows it. */
    private static boolean menuPressed(Minecraft mc, boolean noScreen) {
        boolean pressed = false;
        while (menuKey.consumeClick()) {
            pressed = true;
        }
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(menuKey);
        int code = -1;
        if (key.getType() == InputConstants.Type.KEYSYM) {
            code = key.getValue() == GLFW.GLFW_KEY_UNKNOWN ? GLFW.GLFW_KEY_RIGHT_SHIFT : key.getValue();
        }
        boolean down = code >= GLFW.GLFW_KEY_SPACE && GLFW.glfwGetKey(mc.getWindow().handle(), code) == GLFW.GLFW_PRESS;
        if (down && !menuWasDown && !hadScreen) {
            pressed = true;  // (not the press that just closed a menu, or typing in chat)
        }
        menuWasDown = down;
        hadScreen = !noScreen;
        return pressed;
    }
}
