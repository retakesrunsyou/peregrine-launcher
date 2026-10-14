package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** Moves the fire overlay down when you're burning, so you can still see. */
public final class LowFire extends Module {

    private final SliderSetting drop = add(new SliderSetting("drop", "How much lower", 10f, 60f, 5f, 35f, "%.0f%%"));

    public LowFire() {
        super("low_fire", "Low fire", "The flames on your screen sit lower when you're on fire", Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.fireDrop = drop.value / 100f;
    }

    @Override
    protected void onEnable() {
        Hooks.fireDrop = drop.value / 100f;
    }

    @Override
    protected void onDisable() {
        Hooks.fireDrop = 0f;
    }
}
