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
 * Stacked or on one line, with each part switchable.
 */
public final class CoordsHud extends HudModule {

    private final ChoiceSetting layout = add(new ChoiceSetting("layout", "Layout", 0,
            "Stacked", "One line", "X Y Z on separate lines"));
    private final BoolSetting labels = add(new BoolSetting("labels", "Show labels", true));
    private final BoolSetting facing = add(new BoolSetting("facing", "Show facing", true));
    private final BoolSetting dimension = add(new BoolSetting("dimension", "Show dimension", true));
    private final BoolSetting biome = add(new BoolSetting("biome", "Show biome", false));
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

    /** Each part as {label, value}. */
    private List<String[]> parts(Platform p) {
        String f = decimals.index == 0 ? "%.0f" : decimals.index == 1 ? "%.1f" : "%.2f";
        List<String[]> out = new ArrayList<String[]>();
        if (layout.index == 2) {
            out.add(new String[] {"X", String.format(f, p.x())});
            out.add(new String[] {"Y", String.format(f, p.y())});
            out.add(new String[] {"Z", String.format(f, p.z())});
        } else {
            out.add(new String[] {"XYZ", String.format(f + "  " + f + "  " + f, p.x(), p.y(), p.z())});
        }
        if (facing.value) {
            out.add(new String[] {"Facing", p.facing()});
        }
        if (dimension.value) {
            out.add(new String[] {"In", dimensionName(p)});
        }
        if (biome.value) {
            out.add(new String[] {"Biome", BiomeHud.pretty(p.biome())});
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

    private int partWidth(Draw d, String[] part) {
        return labels.value ? d.width(part[0] + " " + part[1]) : d.width(part[1]);
    }

    private static final int SEP = 10;  // space between parts on one line

    @Override
    public int width(Draw d, Platform p) {
        List<String[]> parts = parts(p);
        int w = 0;
        if (layout.index == 1) {
            for (String[] part : parts) {
                w += partWidth(d, part) + SEP;
            }
            w -= SEP;
        } else {
            for (String[] part : parts) {
                w = Math.max(w, partWidth(d, part));
            }
        }
        return w + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        int lines = layout.index == 1 ? 1 : parts(p).size();
        return lines * (d.lineHeight() + 2) + 4;
    }

    private void part(Draw d, int x, int y, String[] part) {
        if (labels.value) {
            labelled(d, x, y, part[0], part[1]);
        } else {
            d.text(part[1], x, y, net.peregrine.client.core.Theme.TEXT, true);
        }
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        List<String[]> parts = parts(p);
        if (layout.index == 1) {
            int lx = x + 4;
            for (String[] part : parts) {
                part(d, lx, y + 4, part);
                lx += partWidth(d, part) + SEP;
            }
            return;
        }
        int ly = y + 4;
        for (String[] part : parts) {
            part(d, x + 4, ly, part);
            ly += d.lineHeight() + 2;
        }
    }
}
