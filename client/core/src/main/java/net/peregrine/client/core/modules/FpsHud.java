package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

public final class FpsHud extends HudModule {

    public FpsHud() {
        super("fps", "FPS", "Frames per second, so you can see what your settings do", true, 0f, 0f);
    }

    private String value(Platform p) {
        return String.valueOf(p.fps());
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("FPS " + value(p)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "FPS", value(p));
    }
}
