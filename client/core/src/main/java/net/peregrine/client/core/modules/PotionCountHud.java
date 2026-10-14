package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

public final class PotionCountHud extends HudModule {

    public PotionCountHud() {
        super("potion_count", "Potion counter", "How many potions are left in your inventory", false, 1f, 0.92f);
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("Potions " + p.potionCount()) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "Potions", String.valueOf(p.potionCount()));
    }
}
