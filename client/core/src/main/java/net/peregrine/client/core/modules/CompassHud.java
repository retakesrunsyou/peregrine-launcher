package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;

/** A compass strip with headings and a centered marker. */
public final class CompassHud extends HudModule {

    private static final int W = 160;
    private static final float VISIBLE = 120f;  // degrees shown across the strip
    private static final String[] LABELS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    public CompassHud() {
        super("compass", "Compass", "A strip of headings across the top of the screen", false, 0.5f, 0f);
    }

    @Override
    public int width(Draw d, Platform p) {
        return W;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() * 2 + 8;
    }

    /** Minecraft yaw (0 = south) to a compass heading (0 = north). */
    static float heading(float yaw) {
        float h = (yaw + 180f) % 360f;
        return h < 0 ? h + 360f : h;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        int h = height(d, p);
        panel(d, x, y, W, h);
        float heading = heading(p.yaw());
        float pxPerDeg = W / VISIBLE;
        int a = Peregrine.get().accent();
        d.clip(x, y, W, h);
        for (int deg = 0; deg < 360; deg += 15) {
            float diff = deg - heading;
            while (diff > 180) {
                diff -= 360;
            }
            while (diff < -180) {
                diff += 360;
            }
            if (Math.abs(diff) > VISIBLE / 2 + 10) {
                continue;
            }
            int cx = x + W / 2 + Math.round(diff * pxPerDeg);
            if (deg % 45 == 0) {
                String label = LABELS[deg / 45];
                d.text(label, cx - d.width(label) / 2, y + 3, deg == 0 ? a : Theme.TEXT, true);
            } else {
                d.rect(cx, y + 4, 1, 4, Theme.MUTED);
            }
        }
        d.unclip();
        String deg = String.valueOf(Math.round(heading) % 360);
        d.rect(x + W / 2, y + d.lineHeight() + 3, 1, 3, a);
        d.text(deg, x + W / 2 - d.width(deg) / 2, y + d.lineHeight() + 8, Theme.FAINT, false);
    }
}
