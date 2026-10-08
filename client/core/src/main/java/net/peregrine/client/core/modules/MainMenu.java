package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;

/** On/off switch for Peregrine's main menu; the menu itself is TitleMenu. */
public final class MainMenu extends Module {

    public MainMenu() {
        super("main_menu", "Peregrine main menu", "Replaces Minecraft's title screen with Peregrine's",
                Category.UTILITY, true);
    }
}
