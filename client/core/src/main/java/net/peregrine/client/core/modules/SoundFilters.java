package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** Turn kinds of sounds up or down: explosions, rain, hits, footsteps, mobs and more. */
public final class SoundFilters extends Module {

    private final SliderSetting[] levels = new SliderSetting[Hooks.SOUND_GROUPS.length];

    public SoundFilters() {
        super("sound_filters", "Sound filters", "Make explosions, rain, footsteps, mobs and more quieter or silent",
                Category.UTILITY, false);
        float[] defaults = {40f, 50f, 100f, 100f, 100f, 60f, 70f, 60f, 60f};
        for (int i = 0; i < levels.length; i++) {
            levels[i] = add(new SliderSetting("group" + i, Hooks.SOUND_GROUPS[i], 0f, 100f, 5f, defaults[i], "%.0f%%"));
        }
    }

    private void apply() {
        float[] out = new float[levels.length];
        boolean any = false;
        for (int i = 0; i < levels.length; i++) {
            out[i] = enabled() ? levels[i].value / 100f : 1f;
            any |= out[i] != 1f;
        }
        Hooks.soundLevel = out;
        Hooks.soundFiltered = any;
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
