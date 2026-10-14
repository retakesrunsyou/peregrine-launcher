package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;

/** Clear lava: see through lava (fire resistance helps here). */
public final class ClearLava extends Module {

    public ClearLava() {
        super("clear_lava", "Clear lava", "See where you're going in lava", Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.clearLava = true;
    }

    @Override
    protected void onEnable() {
        Hooks.clearLava = true;
    }

    @Override
    protected void onDisable() {
        Hooks.clearLava = false;
    }
}
