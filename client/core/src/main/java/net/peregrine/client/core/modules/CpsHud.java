package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;

public final class CpsHud extends HudModule {

    public CpsHud() {
        super("cps", "CPS", "Clicks per second for left and right mouse buttons", false, 0f, 0.16f);
    }

    private String value() {
        Peregrine pc = Peregrine.get();
        return pc.cps(0) + " | " + pc.cps(1);
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("CPS " + value()) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "CPS", value());
    }
}
