package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;

/** A count of one kind of item in your inventory (totems, arrows). Red when you're out. */
public final class ItemCountHud extends HudModule {

    private final String what;
    private final String label;

    public ItemCountHud(String id, String name, String description, String what, String label, float fx, float fy) {
        super(id, name, description, false, fx, fy);
        this.what = what;
        this.label = label;
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width(label + " " + p.itemCount(what)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        int n = p.itemCount(what);
        panel(d, x, y, width(d, p), height(d, p));
        d.text(label, x + 4, y + 4, n == 0 ? Theme.BAD : net.peregrine.client.core.Peregrine.get().accent(), true);
        d.text(String.valueOf(n), x + 4 + d.width(label + " "), y + 4, Theme.TEXT, true);
    }
}
