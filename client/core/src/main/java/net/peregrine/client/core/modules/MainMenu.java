package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Peregrine's main menu (drawn by TitleMenu): on or off, its background, and your player on it. */
public final class MainMenu extends Module {

    public static final String[] BACKGROUNDS = {"Minecraft panorama", "Dusk", "Aurora", "Night sky", "Your picture"};
    /** Texture files of the featured backgrounds (index 1..3). */
    public static final String[] FILES = {null, "dusk", "aurora", "night", null};
    public static final int CUSTOM = 4;

    public final ChoiceSetting background = add(new ChoiceSetting("background", "Background", 0, BACKGROUNDS));
    public final BoolSetting player = add(new BoolSetting("player", "Show your player", true));
    // Your picture: its size (1 = just fills the screen) and where its middle is (0..1 of the screen).
    // Set by dragging it around in the Locker's picture editor.
    public final SliderSetting zoom = add(new SliderSetting("zoom", "Picture size", 0.25f, 5f, 0.01f, 1f, "%.2fx")
            .withText(v -> Math.round(v * 100) + "%"));
    public final SliderSetting posX = add(new SliderSetting("pos_x", "Picture left / right", -0.5f, 1.5f, 0f, 0.5f, "%.2f")
            .withText(v -> Math.round(v * 100) + "%"));
    public final SliderSetting posY = add(new SliderSetting("pos_y", "Picture up / down", -0.5f, 1.5f, 0f, 0.5f, "%.2f")
            .withText(v -> Math.round(v * 100) + "%"));
    public final BoolSetting drift = add(new BoolSetting("drift", "Picture drifts slowly", true));
    public final SliderSetting dim = add(new SliderSetting("dim", "Darken the picture", 0f, 80f, 5f, 0f, "%.0f%%"));

    public MainMenu() {
        super("main_menu", "Peregrine main menu", "Replaces Minecraft's title screen with Peregrine's",
                Category.UTILITY, true);
    }
}
