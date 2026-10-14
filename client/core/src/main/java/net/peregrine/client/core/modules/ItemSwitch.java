package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/** How your hand moves when you switch items: slower and smoother, or quicker. */
public final class ItemSwitch extends Module {

    public static final String[] MODES = {"Smooth", "Quick", "Snappy"};
    /** How far the hand may move each tick (Minecraft's own is 0.4). */
    static final float[] STEPS = {0.22f, 0.65f, 1f};

    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Animation", 0, MODES));

    public ItemSwitch() {
        super("item_switch", "Item switch", "A smoother, quicker or snappier hand when you change items",
                Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.equipStep = STEPS[mode.index];
    }

    @Override
    protected void onEnable() {
        Hooks.equipStep = STEPS[mode.index];
    }

    @Override
    protected void onDisable() {
        Hooks.equipStep = 0.4f;
    }
}
