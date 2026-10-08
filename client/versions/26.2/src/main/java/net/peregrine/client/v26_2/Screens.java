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

    /** True while F1 has hidden the HUD. */
    public static boolean hudHidden() {
        return Minecraft.getInstance().gui.hud.isHidden();
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
