package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;

/** Dropped items lie flat and still on the ground instead of floating and spinning. */
public final class ItemPhysics extends Module {

    public ItemPhysics() {
        super("item_physics", "Item physics", "Dropped items lie flat on the ground instead of spinning",
                Category.VISUALS, false);
    }

    @Override
    protected void onEnable() {
        Hooks.itemPhysics = true;
    }

    @Override
    public void tick(net.peregrine.client.core.Platform p) {
        Hooks.itemPhysics = true;  // also covers being switched on from the settings file
    }

    @Override
    protected void onDisable() {
        Hooks.itemPhysics = false;
    }
}
