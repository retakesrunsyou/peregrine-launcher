package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;

/** On/off switch for Peregrine's pause menu; the menu itself is PauseMenu. */
public final class PauseMenuToggle extends Module {

    public PauseMenuToggle() {
        super("pause_menu", "Peregrine pause menu", "Replaces Minecraft's pause menu with Peregrine's",
                Category.UTILITY, true);
    }
}
