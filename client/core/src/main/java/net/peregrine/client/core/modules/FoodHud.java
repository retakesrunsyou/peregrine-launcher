package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

/** Hunger plus the hidden saturation value Minecraft doesn't show. */
public final class FoodHud extends HudModule {

    public FoodHud() {
        super("food", "Food", "Hunger and the hidden saturation level", false, 0f, 0.67f);
    }

    private static String value(Platform p) {
        return p.food() + "   Saturation " + String.format("%.1f", p.saturation());
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("Food " + value(p)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "Food", value(p));
    }
}
