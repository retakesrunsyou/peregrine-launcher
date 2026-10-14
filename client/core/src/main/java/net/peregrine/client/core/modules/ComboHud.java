package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** Hits in a row without getting hit back. */
public final class ComboHud extends LineHud {

    private final SliderSetting timeout = add(new SliderSetting("timeout", "Resets after", 1f, 10f, 1f, 3f, "%.0f s"));
    private int combo;
    private long lastHit;

    public ComboHud() {
        super("combo", "Combo counter", "Hits in a row without getting hit back", "Combo", 0f, 0.86f);
    }

    public void hit() {
        combo++;
        lastHit = System.currentTimeMillis();
    }

    @Override
    public void tick(Platform p) {
        if (p.hurtTime() > 0 || System.currentTimeMillis() - lastHit > timeout.value * 1000) {
            combo = 0;
        }
    }

    @Override
    String value(Platform p) {
        return combo == 0 ? "0" : combo + (combo == 1 ? " hit" : " hits");
    }
}
