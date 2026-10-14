package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** A smaller tab list, with each player's ping as a number. */
public final class TabList extends Module {

    private final SliderSetting size = add(new SliderSetting("size", "Size", 50f, 100f, 5f, 80f, "%.0f%%"));
    private final BoolSetting ping = add(new BoolSetting("ping", "Ping as numbers", true));

    public TabList() {
        super("tab_list", "Compact tab list", "A smaller player list (Tab) with ping as numbers", Category.VISUALS, false);
    }

    private void apply() {
        Hooks.tabScale = enabled() ? size.value / 100f : 1f;
        Hooks.tabPingNumbers = enabled() && ping.value;
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

    /** A ping color: green, yellow, orange or red. */
    public static int pingColor(int ms) {
        return ms < 0 ? 0xFF8A8F9C : ms <= 80 ? 0xFF5BD68A : ms <= 150 ? 0xFFE8D25B : ms <= 300 ? 0xFFF09A4A : 0xFFE5676B;
    }
}
