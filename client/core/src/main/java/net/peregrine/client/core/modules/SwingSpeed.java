package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** Your arm swings faster or slower on your screen. The server still sees normal swings. */
public final class SwingSpeed extends Module {

    private final SliderSetting speed = add(new SliderSetting("speed", "Swing speed", 25f, 300f, 5f, 60f, "%.0f%%"));

    public SwingSpeed() {
        super("swing_speed", "Swing speed", "Speed up or slow down your swing animation (only on your screen)",
                Category.VISUALS, false);
    }

    private void apply() {
        Hooks.swingSpeed = enabled() ? speed.value / 100f : 1f;
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
