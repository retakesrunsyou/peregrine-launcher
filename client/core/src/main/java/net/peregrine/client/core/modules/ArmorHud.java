package net.peregrine.client.core.modules;

import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Platform.ItemInfo;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Your armor and held item. Stacked top to bottom or side by side, with durability
 * shown as a number, a percentage, a bar, a bar and number, or not at all.
 */
public final class ArmorHud extends HudModule {

    public static final int VERTICAL = 0, HORIZONTAL = 1;
    public static final int NUMBER = 0, PERCENT = 1, BAR = 2, BAR_AND_NUMBER = 3, NONE = 4;

    private static final int ROW = 18;
    private static final int BAR_W = 24, BAR_H = 3;

    public final ChoiceSetting layout = add(new ChoiceSetting("layout", "Layout", VERTICAL,
            "Top to bottom", "Left to right"));
    public final ChoiceSetting durability = add(new ChoiceSetting("durability", "Durability", NUMBER,
            "Number", "Percent", "Bar", "Bar and number", "Hidden"));

    public ArmorHud() {
        super("armor", "Armor status", "Your armor and held item, with durability left",
                true, 1f, 0.42f);
    }

    private boolean showsText() {
        int m = durability.index;
        return m == NUMBER || m == PERCENT || m == BAR_AND_NUMBER;
    }

    private boolean showsBar() {
        return durability.index == BAR || durability.index == BAR_AND_NUMBER;
    }

    private String label(ItemInfo item) {
        if (durability.index == PERCENT) {
            return Math.round(100.0 * item.remaining() / item.maxDamage) + "%";
        }
        return String.valueOf(item.remaining());
    }

    /** The widest a durability label gets. */
    private int labelWidth(Draw d) {
        return d.width(durability.index == PERCENT ? "100%" : "0000");
    }

    private int detailWidth(Draw d) {
        int w = 0;
        if (showsText()) {
            w = labelWidth(d);
        }
        if (showsBar()) {
            w = Math.max(w, BAR_W);
        }
        return w;
    }

    /** One item's cell when side by side. */
    private int cellWidth(Draw d) {
        int w = 16;
        if (showsText()) {
            w = Math.max(w, labelWidth(d));
        }
        if (showsBar()) {
            w = Math.max(w, 16);
        }
        return w + 4;
    }

    private int cellHeight(Draw d) {
        int h = 16;
        if (showsBar()) {
            h += 1 + BAR_H;
        }
        if (showsText()) {
            h += 1 + d.lineHeight();
        }
        return h;
    }

    private int count(Platform p) {
        return Math.max(1, p.armor().size());
    }

    @Override
    public int width(Draw d, Platform p) {
        if (layout.index == HORIZONTAL) {
            return count(p) * cellWidth(d) + 4;
        }
        int detail = detailWidth(d);
        return 4 + 16 + (detail > 0 ? 4 + detail : 0) + 4;
    }

    @Override
    public int height(Draw d, Platform p) {
        if (layout.index == HORIZONTAL) {
            return cellHeight(d) + 6;
        }
        return count(p) * ROW + 4;
    }

    private static int color(ItemInfo item) {
        double left = item.remaining() / (double) item.maxDamage;
        return left > 0.5 ? Theme.GOOD : left > 0.2 ? Theme.OK : Theme.BAD;
    }

    private static void bar(Draw d, ItemInfo item, int x, int y, int w) {
        double left = Math.max(0, Math.min(1, item.remaining() / (double) item.maxDamage));
        d.rect(x, y, w, BAR_H, 0xA0000000);
        int fill = (int) Math.round((w - 2) * left);
        if (fill > 0) {
            d.rect(x + 1, y + 1, fill, BAR_H - 2, 0xFF000000 | color(item));
        }
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        List<ItemInfo> items = p.armor();
        if (items.isEmpty()) {
            return;
        }
        panel(d, x, y, width(d, p), height(d, p));
        if (layout.index == HORIZONTAL) {
            int cw = cellWidth(d);
            int cx = x + 2;
            for (ItemInfo item : items) {
                int mid = cx + cw / 2;
                int iy = y + 3;
                d.item(item.stack, mid - 8, iy);
                int ry = iy + 16;
                if (item.damageable()) {
                    if (showsBar()) {
                        bar(d, item, mid - 8, ry + 1, 16);
                        ry += 1 + BAR_H;
                    }
                    if (showsText()) {
                        String s = label(item);
                        d.text(s, mid - d.width(s) / 2, ry + 1, color(item), true);
                    }
                }
                cx += cw;
            }
            return;
        }
        int ry = y + 2;
        int tx = x + 24;
        for (ItemInfo item : items) {
            d.item(item.stack, x + 4, ry + 1);
            if (item.damageable()) {
                boolean text = showsText(), bar = showsBar();
                if (text && bar) {
                    d.text(label(item), tx, ry + 1, color(item), true);
                    bar(d, item, tx, ry + 2 + d.lineHeight(), BAR_W);
                } else if (text) {
                    d.text(label(item), tx, ry + 5, color(item), true);
                } else if (bar) {
                    bar(d, item, tx, ry + 8, BAR_W);
                }
            }
            ry += ROW;
        }
    }

    @Override
    public boolean hasContent(Platform p) {
        return !p.armor().isEmpty();
    }
}
