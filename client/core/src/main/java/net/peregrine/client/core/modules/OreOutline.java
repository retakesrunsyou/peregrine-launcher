package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Ore outline: a glowing line around each vein of ore you can see, in the ore's
 * color. Only the outside of the vein is drawn, and it's hidden behind blocks
 * like any other outline, so it never shows through walls.
 */
public final class OreOutline extends Module {

    private final BoolSetting[] types = new BoolSetting[Hooks.ORE_TYPES.length];
    private final SliderSetting range = add(new SliderSetting("range", "Range", 4f, 24f, 1f, 14f, "%.0f blocks"));
    private final SliderSetting width = add(new SliderSetting("width", "Thickness (1.21.11 and newer)", 1f, 6f, 0.5f, 2.5f, "%.1fx"));

    public OreOutline() {
        super("ore_outline", "Ore outline", "A colored outline around each vein of ore you can see",
                Category.VISUALS, false);
        boolean[] defaults = {true, true, true, true, true, true, true, false, true, true};
        for (int i = 0; i < types.length; i++) {
            types[i] = add(new BoolSetting("ore" + i, Hooks.ORE_TYPES[i], defaults[i]));
        }
    }

    private void apply() {
        boolean[] on = new boolean[types.length];
        for (int i = 0; i < types.length; i++) {
            on[i] = types[i].value;
        }
        Hooks.oreEnabled = on;
        Hooks.oreOutlines = enabled();
        Hooks.oreRange = Math.round(range.value);
        Hooks.oreWidth = width.value;
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
        Hooks.oreOutlines = false;
    }
}
