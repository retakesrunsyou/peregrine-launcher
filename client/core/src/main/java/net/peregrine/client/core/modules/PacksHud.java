package net.peregrine.client.core.modules;

import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;

/** The resource packs in use, top of the list first. */
public final class PacksHud extends HudModule {

    public PacksHud() {
        super("packs", "Resource packs", "Which resource packs are on, in order", false, 1f, 0.75f);
    }

    @Override
    public int width(Draw d, Platform p) {
        int w = d.width("Resource packs");
        for (String s : p.resourcePacks()) {
            w = Math.max(w, d.width(s));
        }
        return Math.min(w, 180) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return (p.resourcePacks().size() + 1) * (d.lineHeight() + 2) + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        List<String> packs = p.resourcePacks();
        int w = width(d, p);
        panel(d, x, y, w, height(d, p));
        d.text("Resource packs", x + 4, y + 4, Peregrine.get().accent(), true);
        int ry = y + 6 + d.lineHeight();
        for (String s : packs) {
            d.text(d.trim(s, w - 8), x + 4, ry, Theme.TEXT, true);
            ry += d.lineHeight() + 2;
        }
    }
}
