package net.peregrine.client.v26_2;

import java.lang.reflect.Method;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Draws Minecraft's moving panorama, found by name at runtime so a rename can't break the build. */
final class Panorama {

    private static Method method;
    private static boolean lookedUp;

    private Panorama() {
    }

    static void draw(Screen screen, GuiGraphicsExtractor g, float partialTick) {
        if (!lookedUp) {
            lookedUp = true;
            for (String name : new String[] {"extractPanorama", "renderPanorama"}) {
                try {
                    method = Screen.class.getDeclaredMethod(name, GuiGraphicsExtractor.class, float.class);
                    method.setAccessible(true);
                    break;
                } catch (NoSuchMethodException ignored) {
                    // try the next name
                }
            }
        }
        if (method != null) {
            try {
                method.invoke(screen, g, partialTick);
                return;
            } catch (ReflectiveOperationException ignored) {
                // fall through to a plain background
            }
        }
        g.fill(0, 0, screen.width, screen.height, 0xFF171A21);
    }
}
