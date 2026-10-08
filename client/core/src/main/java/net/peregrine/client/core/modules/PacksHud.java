package net.peregrine.client.core.modules;

import java.util.ArrayList;
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

    private static final int MAX_LINES = 6;

    /** The player's own packs: built-in ones (Minecraft's default, each mod's) are left out. */
    static List<String> lines(Platform p) {
        List<String> out = new ArrayList<String>();
        for (String s : p.resourcePacks()) {
            String low = s.toLowerCase();
            if (low.equals("default") || low.startsWith("fabric mod") || low.equals("fabric mods")
                    || low.equals("mod resources") || low.equals("minecraft")) {
                continue;
            }
            out.add(s);
        }
        if (out.isEmpty()) {
            out.add("Default");
        } else if (out.size() > MAX_LINES) {
            int more = out.size() - (MAX_LINES - 1);
            out = new ArrayList<String>(out.subList(0, MAX_LINES - 1));
            out.add("+" + more + " more");
        }
        return out;
    }

    @Override
    public int width(Draw d, Platform p) {
        int w = d.width("Resource packs");
        for (String s : lines(p)) {
            w = Math.max(w, d.width(s));
        }
        return Math.min(w, 180) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return (lines(p).size() + 1) * (d.lineHeight() + 2) + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        List<String> packs = lines(p);
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
