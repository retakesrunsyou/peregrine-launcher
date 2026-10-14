package net.peregrine.client.core.modules;

import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;

public final class CpsHud extends LineHud {

    public CpsHud() {
        super("cps", "CPS", "Clicks per second for left and right mouse buttons", "CPS", 0f, 0.175f);
    }

    @Override
    String value(Platform p) {
        Peregrine pc = Peregrine.get();
        return pc.cps(0) + " | " + pc.cps(1);
    }
}
