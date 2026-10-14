package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;

/** Clear water: no fog or blue overlay under water. */
public final class ClearWater extends Module {

    public ClearWater() {
        super("clear_water", "Clear water", "See far and clearly under water: no fog, no blue overlay", Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.clearWater = true;
    }

    @Override
    protected void onEnable() {
        Hooks.clearWater = true;
    }

    @Override
    protected void onDisable() {
        Hooks.clearWater = false;
    }
}
