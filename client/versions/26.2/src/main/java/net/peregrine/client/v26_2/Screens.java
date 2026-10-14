package net.peregrine.client.v26_2;

import com.mojang.blaze3d.platform.InputConstants;

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

    /**
     * A key as GLFW numbers it (what Peregrine Client stores for keybinds). 26.3 uses
     * SDL's numbers, so translate through Minecraft's own key constants; on 26.2 this
     * gives the same number back. Returns -1 for keys it doesn't know.
     */
    public static int glfwKey(int k) {
        if (k >= InputConstants.KEY_A && k <= InputConstants.KEY_Z) {
            return 65 + (k - InputConstants.KEY_A);
        }
        if (k >= InputConstants.KEY_0 && k <= InputConstants.KEY_9) {
            return 48 + (k - InputConstants.KEY_0);
        }
        if (k >= InputConstants.KEY_F1 && k <= InputConstants.KEY_F12) {
            return 290 + (k - InputConstants.KEY_F1);
        }
        int[] pad = {InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2,
            InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6,
            InputConstants.KEY_NUMPAD7, InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9};
        for (int i = 0; i < pad.length; i++) {  // (SDL puts keypad 0 after 9, so one by one)
            if (pad[i] == k) {
                return 320 + i;
            }
        }
        int[][] pairs = {
            {InputConstants.KEY_SPACE, 32}, {InputConstants.KEY_ESCAPE, 256}, {InputConstants.KEY_RETURN, 257},
            {InputConstants.KEY_TAB, 258}, {InputConstants.KEY_BACKSPACE, 259}, {InputConstants.KEY_INSERT, 260},
            {InputConstants.KEY_DELETE, 261}, {InputConstants.KEY_RIGHT, 262}, {InputConstants.KEY_LEFT, 263},
            {InputConstants.KEY_DOWN, 264}, {InputConstants.KEY_UP, 265}, {InputConstants.KEY_PAGEUP, 266},
            {InputConstants.KEY_PAGEDOWN, 267}, {InputConstants.KEY_HOME, 268}, {InputConstants.KEY_END, 269},
            {InputConstants.KEY_CAPSLOCK, 280}, {InputConstants.KEY_LSHIFT, 340}, {InputConstants.KEY_LCONTROL, 341},
            {InputConstants.KEY_LALT, 342}, {InputConstants.KEY_RSHIFT, 344}, {InputConstants.KEY_RCONTROL, 345},
            {InputConstants.KEY_RALT, 346}, {InputConstants.KEY_NUMPADENTER, 335}, {InputConstants.KEY_MINUS, 45},
            {InputConstants.KEY_EQUALS, 61}, {InputConstants.KEY_COMMA, 44}, {InputConstants.KEY_PERIOD, 46},
            {InputConstants.KEY_SLASH, 47}, {InputConstants.KEY_SEMICOLON, 59}, {InputConstants.KEY_APOSTROPHE, 39},
            {InputConstants.KEY_LBRACKET, 91}, {InputConstants.KEY_RBRACKET, 93}, {InputConstants.KEY_BACKSLASH, 92},
            {InputConstants.KEY_GRAVE, 96},
        };
        for (int[] pair : pairs) {
            if (pair[0] == k) {
                return pair[1];
            }
        }
        return -1;
    }
}
