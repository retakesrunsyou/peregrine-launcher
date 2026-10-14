package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/** Blocks you place grow into place from the middle instead of popping in. */
public final class PlaceAnimation extends Module {

    public static final String[] SPEEDS = {"Quick", "Normal", "Slow"};
    static final int[] MS = {110, 160, 240};

    private final ChoiceSetting speed = add(new ChoiceSetting("speed", "Speed", 1, SPEEDS));

    public PlaceAnimation() {
        super("place_animation", "Block place animation", "Blocks you place grow into place from the middle",
                Category.VISUALS, true);
    }

    private void apply() {
        Hooks.placeAnimMs = enabled() ? MS[speed.index] : 0;
    }

    @Override
    public void tick(Platform p) {
        apply();
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        apply();
    }
}
