package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

/** Where you'd come out in the other dimension: overworld / 8 in the Nether, x 8 back. */
public final class NetherCoordsHud extends HudModule {

    public NetherCoordsHud() {
        super("nether_coords", "Nether coordinates", "Matching Nether or Overworld spot, for building portals",
                false, 0f, 0.66f);
    }

    private boolean inNether(Platform p) {
        return p.dimension().endsWith("the_nether");
    }

    private String value(Platform p) {
        double f = inNether(p) ? 8 : 1 / 8.0;
        return String.format("%.0f  %.0f", Math.floor(p.x() * f), Math.floor(p.z() * f));
    }

    private String label(Platform p) {
        return inNether(p) ? "Overworld" : "Nether";
    }

    @Override
    public boolean hasContent(Platform p) {
        return !p.dimension().endsWith("the_end");
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width(label(p) + " " + value(p)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        if (!hasContent(p)) {
            return;
        }
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, label(p), value(p));
    }
}
