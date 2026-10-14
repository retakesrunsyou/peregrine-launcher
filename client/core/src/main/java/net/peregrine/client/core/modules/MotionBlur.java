package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** Motion blur: each frame keeps a little of the last one, so turning leaves a soft trail. */
public final class MotionBlur extends Module {

    private final SliderSetting strength = add(new SliderSetting("strength", "Strength", 5f, 90f, 5f, 50f, "%.0f%%"));

    public MotionBlur() {
        super("motion_blur", "Motion blur", "A soft trail when you turn and move (off by default)",
                Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.motionBlur = enabled() ? strength.value / 100f : 0f;
    }

    @Override
    protected void onEnable() {
        Hooks.motionBlur = strength.value / 100f;
    }

    @Override
    protected void onDisable() {
        Hooks.motionBlur = 0f;
    }
}
