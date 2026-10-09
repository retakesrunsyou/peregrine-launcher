package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

/** A one-line HUD item: "Label value". Hidden when value() returns null. */
abstract class LineHud extends HudModule {

    private final String label;

    LineHud(String id, String name, String description, String label, float fx, float fy) {
        super(id, name, description, false, fx, fy);
        this.label = label;
    }

    /** The text after the label, or null for nothing to show right now. */
    abstract String value(Platform p);

    /** Color for the label; 0 = the accent (and the item's own label color). */
    int labelColor(Platform p) {
        return 0;
    }

    private String shown(Platform p) {
        String v = value(p);
        return v == null ? "-" : v;
    }

    @Override
    public boolean hasContent(Platform p) {
        return value(p) != null;
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width(label + " " + shown(p)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        String v = value(p);
        if (v == null) {
            return;
        }
        panel(d, x, y, width(d, p), height(d, p));
        int c = labelColor(p);
        if (c == 0) {
            labelled(d, x + 4, y + 4, label, v);
        } else {
            d.text(label, x + 4, y + 4, c, true);
            d.text(v, x + 4 + d.width(label + " "), y + 4, net.peregrine.client.core.Theme.TEXT, true);
        }
    }
}
