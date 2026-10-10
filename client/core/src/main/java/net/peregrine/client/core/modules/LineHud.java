package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/** A one-line HUD item: "Label value". Hidden when value() returns null. */
abstract class LineHud extends HudModule {

    private final String label;
    /** How the line reads: "FPS 120", "120", "FPS: 120" or "[FPS: 120]". */
    private final ChoiceSetting style = add(new ChoiceSetting("style", "Style", 0,
            "Label value", "Value only", "Label: value", "[Label: value]"));

    LineHud(String id, String name, String description, String label, float fx, float fy) {
        this(id, name, description, label, false, fx, fy);
    }

    LineHud(String id, String name, String description, String label, boolean defaultOn, float fx, float fy) {
        super(id, name, description, defaultOn, fx, fy);
        this.label = label;
    }

    /** The label part as shown in the chosen style ("" for value only). */
    private String head() {
        switch (style.index) {
            case 1: return "";
            case 2: return label + ":";
            case 3: return "[" + label + ":";
            default: return label;
        }
    }

    private String tail() {
        return style.index == 3 ? "]" : "";
    }

    private String line(String v) {
        String h = head();
        return (h.isEmpty() ? "" : h + " ") + v + tail();
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
        return d.width(line(shown(p))) + 8;
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
        String h = head();
        int tx = x + 4;
        if (!h.isEmpty()) {
            if (c == 0) {
                labelled(d, tx, y + 4, h, v + tail());
                return;
            }
            d.text(h, tx, y + 4, c, true);
            tx += d.width(h + " ");
        }
        d.text(v + tail(), tx, y + 4, net.peregrine.client.core.Theme.TEXT, true);
    }
}
