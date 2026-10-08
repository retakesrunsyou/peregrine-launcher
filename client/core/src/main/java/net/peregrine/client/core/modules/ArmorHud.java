package net.peregrine.client.core.modules;

import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Platform.ItemInfo;
import net.peregrine.client.core.Theme;

public final class ArmorHud extends HudModule {

    private static final int ROW = 18;

    public ArmorHud() {
        super("armor", "Armor status", "Your armor and held item, with durability left",
                true, 1f, 0.5f);
    }

    @Override
    public int width(Draw d, Platform p) {
        return 16 + 4 + d.width("0000") + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return Math.max(1, p.armor().size()) * ROW + 4;
    }

    private static int color(ItemInfo item) {
        double left = item.remaining() / (double) item.maxDamage;
        return left > 0.5 ? Theme.GOOD : left > 0.2 ? Theme.OK : Theme.BAD;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        List<ItemInfo> items = p.armor();
        if (items.isEmpty()) {
            return;
        }
        panel(d, x, y, width(d, p), height(d, p));
        int ry = y + 2;
        for (ItemInfo item : items) {
            d.item(item.stack, x + 4, ry + 1);
            if (item.damageable()) {
                d.text(String.valueOf(item.remaining()), x + 24, ry + 5, color(item), true);
            }
            ry += ROW;
        }
    }
}
