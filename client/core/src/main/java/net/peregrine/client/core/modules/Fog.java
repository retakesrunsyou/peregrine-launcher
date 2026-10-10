package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/** Fog: switch it off, see clearly under water and in lava, and pick its color. */
public final class Fog extends Module {

    static final String[] COLORS = {"Minecraft's", "White", "Black", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink"};
    static final int[] RGB = {0, 0xFFFFFFFF, 0xFF101014, 0xFFE04848, 0xFFF0903C, 0xFFF2D24A, 0xFF5ED17A,
        0xFF4FD8E0, 0xFF4C7CF0, 0xFF9C62F0, 0xFFF07AC8};

    private final BoolSetting off = add(new BoolSetting("off", "No fog in the distance", true));
    private final BoolSetting water = add(new BoolSetting("water", "Clear water (no fog or overlay)", true));
    private final BoolSetting lava = add(new BoolSetting("lava", "Clear lava", true));
    private final ChoiceSetting color = add(new ChoiceSetting("color", "Fog color", 0, COLORS));

    public Fog() {
        super("fog", "Fog", "Turn fog off, see clearly under water and in lava, and color the fog", Category.VISUALS, false);
    }

    private void apply() {
        boolean on = enabled();
        Hooks.fogOff = on && off.value;
        Hooks.clearWater = on && water.value;
        Hooks.clearLava = on && lava.value;
        Hooks.fogColor = on ? RGB[color.index] : 0;
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
