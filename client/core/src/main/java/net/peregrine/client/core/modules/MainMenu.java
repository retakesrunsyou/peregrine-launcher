package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/** Peregrine's main menu (drawn by TitleMenu): on or off, its background, and your player on it. */
public final class MainMenu extends Module {

    public static final String[] BACKGROUNDS = {"Minecraft panorama", "Dusk", "Aurora", "Night sky", "Your picture"};
    /** Texture files of the featured backgrounds (index 1..3). */
    public static final String[] FILES = {null, "dusk", "aurora", "night", null};
    public static final int CUSTOM = 4;

    public final ChoiceSetting background = add(new ChoiceSetting("background", "Background", 0, BACKGROUNDS));
    public final BoolSetting player = add(new BoolSetting("player", "Show your player", true));

    public MainMenu() {
        super("main_menu", "Peregrine main menu", "Replaces Minecraft's title screen with Peregrine's",
                Category.UTILITY, true);
    }
}
