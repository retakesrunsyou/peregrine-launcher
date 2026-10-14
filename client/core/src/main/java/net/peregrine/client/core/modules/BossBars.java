package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Boss bars: smaller, bigger or hidden. */
public final class BossBars extends Module {

    private final SliderSetting size = add(new SliderSetting("boss", "Size", 40f, 150f, 5f, 80f, "%.0f%%"));
    private final BoolSetting hide = add(new BoolSetting("hide_boss", "Hide them", false));

    public BossBars() {
        super("boss_bars", "Boss bars", "Resize or hide boss bars at the top of the screen", Category.VISUALS, false);
    }

    private void apply() {
        boolean on = enabled();
        Hooks.bossScale = on ? size.value / 100f : 1f;
        Hooks.bossHidden = on && hide.value;
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
