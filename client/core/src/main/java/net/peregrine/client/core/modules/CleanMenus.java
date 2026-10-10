package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;

/**
 * Resource packs only change the game, not the menus: the title screen, buttons,
 * menu backgrounds, fonts and splash texts stay as Minecraft (and Peregrine) draw
 * them, so a pack can't make the menus hard to read. Switching it reloads packs.
 */
public final class CleanMenus extends Module {

    public CleanMenus() {
        super("clean_menus", "Keep packs out of menus",
                "Resource packs change blocks, items and the HUD, but not the title screen, buttons or fonts",
                Category.VISUALS, true);
    }

    /** The saved choice, applied before Minecraft first loads its resources. */
    public void applyQuietly() {
        Hooks.cleanMenus = enabled();
    }

    @Override
    protected void onEnable() {
        Hooks.cleanMenus = true;
        platform().reloadResources();
    }

    @Override
    protected void onDisable() {
        Hooks.cleanMenus = false;
        platform().reloadResources();
    }
}
