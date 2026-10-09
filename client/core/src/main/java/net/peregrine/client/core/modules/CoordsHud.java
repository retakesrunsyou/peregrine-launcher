package net.peregrine.client.core.modules;

import java.util.ArrayList;
import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Position and facing, in every dimension (and on any server or Realm: it reads
 * your own position, so servers that hide F3 coordinates don't affect it).
 */
public final class CoordsHud extends HudModule {

    private final BoolSetting facing = add(new BoolSetting("facing", "Show facing", true));
    private final BoolSetting dimension = add(new BoolSetting("dimension", "Show dimension", true));
    private final BoolSetting other = add(new BoolSetting("other", "Show Nether / Overworld match", false));
    private final ChoiceSetting decimals = add(new ChoiceSetting("decimals", "Decimals", 1, "None", "One", "Two"));

    public CoordsHud() {
        super("coords", "Coordinates", "Your position, facing and dimension, everywhere you play", true, 0f, 0.04f);
    }

    private static String dimensionName(Platform p) {
        String d = p.dimension();
        if (d.endsWith("the_nether")) {
            return "Nether";
        }
        if (d.endsWith("the_end")) {
            return "The End";
        }
        if (d.endsWith("overworld") || d.isEmpty()) {
            return "Overworld";
        }
        String id = d.contains(":") ? d.substring(d.indexOf(':') + 1) : d;  // a modded dimension
        return id.isEmpty() ? "Overworld" : Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
    }

    private List<String[]> lines(Platform p) {
        String f = decimals.index == 0 ? "%.0f" : decimals.index == 1 ? "%.1f" : "%.2f";
        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[] {"XYZ", String.format(f + "  " + f + "  " + f, p.x(), p.y(), p.z())});
        if (facing.value) {
            out.add(new String[] {"Facing", p.facing()});
        }
        if (dimension.value) {
            out.add(new String[] {"In", dimensionName(p)});
        }
        String d = p.dimension();
        if (other.value && !d.endsWith("the_end")) {
            boolean nether = d.endsWith("the_nether");
            double k = nether ? 8 : 1 / 8.0;
            out.add(new String[] {nether ? "Overworld" : "Nether",
                    String.format("%.0f  %.0f", Math.floor(p.x() * k), Math.floor(p.z() * k))});
        }
        return out;
    }

    @Override
    public int width(Draw d, Platform p) {
        int w = 0;
        for (String[] l : lines(p)) {
            w = Math.max(w, d.width(l[0] + " " + l[1]));
        }
        return w + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return lines(p).size() * (d.lineHeight() + 2) + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        int ly = y + 4;
        for (String[] l : lines(p)) {
            labelled(d, x + 4, ly, l[0], l[1]);
            ly += d.lineHeight() + 2;
        }
    }
}
