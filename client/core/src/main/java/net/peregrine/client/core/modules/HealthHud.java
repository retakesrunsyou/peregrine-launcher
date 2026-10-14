package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;

/** Exact health as a number, colored green to red as it drops. */
public final class HealthHud extends HudModule {

    public HealthHud() {
        super("health", "Health", "Your exact health, turning red when it gets low", false, 1f, 0.30f);
    }

    private String value(Platform p) {
        return String.format("%.1f / %.0f", Math.max(0, p.health()) / 2f, p.maxHealth() / 2f);
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("Health " + value(p)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public boolean hasContent(Platform p) {
        return p.health() >= 0;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        if (!hasContent(p)) {
            return;
        }
        panel(d, x, y, width(d, p), height(d, p));
        float f = p.health() / Math.max(1f, p.maxHealth());
        int color = f > 0.6f ? Theme.GOOD : f > 0.3f ? Theme.OK : Theme.BAD;
        d.text("Health", x + 4, y + 4, color, true);
        d.text(value(p), x + 4 + d.width("Health "), y + 4, Theme.TEXT, true);
    }
}
