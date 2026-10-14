package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * A smaller totem: the one held in your hand takes up less of your screen, and
 * the big totem that flies at you when one pops is smaller too.
 */
public final class SmallTotem extends Module {

    private final SliderSetting held = add(new SliderSetting("held", "Totem in your hand", 20f, 100f, 5f, 55f, "%.0f%%"));
    private final SliderSetting pop = add(new SliderSetting("pop", "Totem pop animation", 10f, 100f, 5f, 40f, "%.0f%%"));

    public SmallTotem() {
        super("small_totem", "Small totem", "A smaller totem in your hand, and a smaller one when it pops",
                Category.VISUALS, false);
    }

    private void apply() {
        Hooks.totemHeld = enabled() ? held.value / 100f : 1f;
        Hooks.totemPop = enabled() ? pop.value / 100f : 1f;
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
