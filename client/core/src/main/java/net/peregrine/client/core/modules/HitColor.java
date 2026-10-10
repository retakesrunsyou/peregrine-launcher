package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Changes the red flash on mobs and players when they take damage. */
public final class HitColor extends Module {

    static final String[] NAMES = {"Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "White"};
    static final int[] RGB = {0xFF0000, 0xFF8A00, 0xFFE600, 0x2BFF4A, 0x00E5FF, 0x2F6BFF, 0x9B3BFF, 0xFF4FD8, 0xFFFFFF};

    private final ChoiceSetting color = add(new ChoiceSetting("color", "Color", 6, NAMES));
    private final SliderSetting strength = add(new SliderSetting("strength", "Strength", 10f, 100f, 5f, 70f, "%.0f%%"));
    private final ChoiceSetting crit = add(new ChoiceSetting("crit", "Crit sparks", 1,
            "Minecraft's", "Same as hit color", "Red", "Gold", "Green", "Cyan", "Purple", "White"));
    static final int[] CRIT_RGB = {0, 0, 0xFF3030, 0xFFC020, 0x40FF60, 0x30E0FF, 0xA050FF, 0xFFFFFF};
    private int applied = -1;

    public HitColor() {
        super("hit_color", "Hit color", "Change the red flash when mobs and players get hit, and the crit sparks", Category.VISUALS, false);
    }

    /** The overlay color as ARGB (alpha = how strongly it tints). */
    public int argb() {
        int a = Math.round(strength.value / 100f * 255f);
        return a << 24 | RGB[color.index];
    }

    @Override
    public void tick(Platform p) {
        net.peregrine.client.core.Hooks.critColor = crit.index == 0 ? 0
                : (crit.index == 1 ? RGB[color.index] : CRIT_RGB[crit.index]) | 0xFF000000;
        int want = argb();
        if (want != applied) {
            applied = want;
            p.setHitColor(want);
        }
    }

    @Override
    protected void onDisable() {
        net.peregrine.client.core.Hooks.critColor = 0;
        applied = -1;
        platform().setHitColor(0);
    }
}
