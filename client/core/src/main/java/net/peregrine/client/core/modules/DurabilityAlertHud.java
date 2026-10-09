package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Platform.ItemInfo;
import net.peregrine.client.core.Theme;

/** A gentle pulsing warning when worn armor or the held tool is about to break. */
public final class DurabilityAlertHud extends HudModule {

    private static final float LOW = 0.10f;

    public DurabilityAlertHud() {
        super("durability_alert", "Low durability alert", "Warns before your armor or tool breaks",
                true, 0.5f, 0.70f);
    }

    private ItemInfo worst(Platform p) {
        ItemInfo worst = null;
        for (ItemInfo i : p.armor()) {
            if (i.damageable() && i.remaining() <= i.maxDamage * LOW
                    && (worst == null || i.remaining() < worst.remaining())) {
                worst = i;
            }
        }
        return worst;
    }

    @Override
    public boolean hasContent(Platform p) {
        return worst(p) != null;
    }

    private static final String TEXT = "Low durability";

    @Override
    public int width(Draw d, Platform p) {
        return d.width(TEXT) + 30;
    }

    @Override
    public int height(Draw d, Platform p) {
        return 20;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        ItemInfo item = worst(p);
        if (item == null) {
            return;
        }
        int w = width(d, p);
        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 220.0);
        d.roundRect(x, y, w, 20, Theme.withAlpha(Theme.BAD, Math.round(0x30 + 0x40 * pulse)));
        d.roundOutline(x, y, w, 20, Theme.withAlpha(Theme.BAD, Math.round(0x80 + 0x7F * pulse)));
        d.item(item.stack, x + 3, y + 2);
        d.text(TEXT, x + 23, y + 6, Theme.TEXT, true);
    }
}
