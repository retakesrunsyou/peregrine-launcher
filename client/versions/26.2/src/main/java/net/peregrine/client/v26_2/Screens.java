package net.peregrine.client.v26_2;

import java.lang.reflect.Constructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/** Opening and reading screens, which moved from Minecraft to Gui in 26.2. */
public final class Screens {
    private Screens() {}

    public static Screen current() {
        return Minecraft.getInstance().gui.screen();
    }

    public static void open(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    /**
     * Mouse button as the core counts them (0 = left, 1 = right, 2 = middle).
     * 26.3 numbers them the SDL way (left = 1), earlier versions the GLFW way (left = 0).
     */
    public static int button(int raw) {
        if (raw == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            return 0;
        }
        if (raw == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
            return 1;
        }
        if (raw == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE) {
            return 2;
        }
        return 3 + raw;
    }

    /** True while F1 has hidden the HUD. */
    public static boolean hudHidden() {
        return Minecraft.getInstance().gui.hud.isHidden();
    }

    /**
     * Minecraft 26.3 (SDL) only sends typed characters while text input is on,
     * so the menu's search box has to switch it on. Earlier versions always send them.
     */
    public static void textInput(Object owner, boolean on) {
        try {
            Object manager = Minecraft.class.getMethod("textInputManager").invoke(Minecraft.getInstance());
            manager.getClass().getMethod(on ? "startTextInput" : "stopTextInput", Object.class)
                    .invoke(manager, owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 26.2: no owner-based text input; characters arrive anyway
        }
    }

    /** Minecraft's Options screen; its constructor differs between versions. */
    public static Screen options(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        try {
            for (Constructor<?> c : OptionsScreen.class.getConstructors()) {
                Class<?>[] t = c.getParameterTypes();
                if (t.length == 2 && t[1] == Options.class) {
                    return (Screen) c.newInstance(parent, mc.options);
                }
                if (t.length == 3 && t[1] == Options.class && t[2] == boolean.class) {
                    return (Screen) c.newInstance(parent, mc.options, mc.level != null);
                }
            }
        } catch (ReflectiveOperationException e) {
            // fall through
        }
        return parent;
    }
}
