package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

public final class CoordsHud extends HudModule {

    public CoordsHud() {
        super("coords", "Coordinates", "Your position and which way you're facing", true, 0f, 0.07f);
    }

    private String[] lines(Platform p) {
        return new String[] {
            String.format("%.1f  %.1f  %.1f", p.x(), p.y(), p.z()),
            p.facing()
        };
    }

    @Override
    public int width(Draw d, Platform p) {
        String[] l = lines(p);
        return Math.max(d.width("XYZ " + l[0]), d.width("Facing " + l[1])) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() * 2 + 8;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        String[] l = lines(p);
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "XYZ", l[0]);
        labelled(d, x + 4, y + 6 + d.lineHeight(), "Facing", l[1]);
    }
}
