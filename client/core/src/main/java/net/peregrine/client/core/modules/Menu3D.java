package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.settings.ActionSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * 3D menu: Peregrine's menu (Right Shift) and pause menu drawn on a floating panel instead of
 * flat on the screen. It either tilts toward your mouse, or stays put in the world where you
 * opened it while you walk and look around (aim with your crosshair and click). Move the panel
 * by holding Alt and dragging it (in the world: hold Alt and look where you want it), and
 * resize it with Ctrl and the scroll wheel.
 */
public final class Menu3D extends Module {

    public static final int TILT = 0, WORLD = 1;

    public final ChoiceSetting mode = add(new ChoiceSetting("mode", "Panel", 0, "Tilts toward your mouse", "Stays in the world"));
    public final SliderSetting tilt = add(new SliderSetting("tilt", "How much it tilts", 0f, 20f, 1f, 8f, "%.0f°"));
    public final SliderSetting size = add(new SliderSetting("size", "Size", 50f, 120f, 1f, 88f, "%.0f%%"));
    public final SliderSetting x = add(new SliderSetting("x", "Left / right", -60f, 60f, 1f, 0f, "%.0f"));
    public final SliderSetting y = add(new SliderSetting("y", "Up / down", -60f, 60f, 1f, 0f, "%.0f"));
    public final SliderSetting distance = add(new SliderSetting("distance", "Distance in the world", 1f, 4f, 0.1f, 1.8f, "%.1f blocks"));
    public final ActionSetting reset = add(new ActionSetting("reset", "Panel position and size", () -> "Reset", this::resetPanel));

    private static volatile Menu3D instance;

    public Menu3D() {
        super("menu_3d", "3D menu", "Peregrine's menus and the pause menu in 3D: a panel that tilts toward your mouse, or stays in the "
                + "world while you walk and look around. Alt + drag moves it, Ctrl + scroll resizes it", Category.VISUALS, false);
        instance = this;
    }

    private void resetPanel() {
        size.value = 88f;
        x.value = 0f;
        y.value = 0f;
        distance.value = 1.8f;
    }

    public static Menu3D get() {
        Menu3D m = instance;
        return m != null && m.enabled() ? m : null;
    }

    /** Moves the panel by a share of the screen (from dragging it). */
    public void nudge(float dx, float dy) {
        x.value = Math.max(x.min, Math.min(x.max, x.value + dx * 100f));
        y.value = Math.max(y.min, Math.min(y.max, y.value + dy * 100f));
    }

    public void resize(float steps) {
        size.value = Math.max(size.min, Math.min(size.max, size.value + steps * 4f));
    }
}
