package net.peregrine.client.core.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;

/** Items on cooldown (ender pearls, chorus fruit, wind charges, shields...), with time left. */
public final class CooldownsHud extends HudModule {

    private static final int ROW = 20;

    private final BoolSetting seconds = add(new BoolSetting("seconds", "Show seconds left", true));

    /** When each cooldown was first seen, and at what fraction: to work out the time left. */
    private final Map<String, long[]> seen = new HashMap<String, long[]>();

    public CooldownsHud() {
        super("cooldowns", "Cooldowns", "Ender pearl, chorus fruit, wind charge and other item cooldowns",
                false, 0.5f, 0.62f);
    }

    @Override
    public boolean hasContent(Platform p) {
        return !p.cooldowns().isEmpty();
    }

    @Override
    public int width(Draw d, Platform p) {
        return 76;
    }

    @Override
    public int height(Draw d, Platform p) {
        int n = Math.max(1, p.cooldowns().size());
        return n * ROW + 4;
    }

    /** Seconds left, from how fast the bar has been going down (or a guess at first). */
    private String left(String key, float frac) {
        long now = System.currentTimeMillis();
        long[] s = seen.get(key);
        if (s == null || frac > Float.intBitsToFloat((int) s[1]) + 0.01f) {
            seen.put(key, new long[] {now, Float.floatToIntBits(frac)});
            return "";
        }
        float start = Float.intBitsToFloat((int) s[1]);
        float gone = start - frac;
        long ms = now - s[0];
        if (gone < 0.02f || ms < 80) {
            return "";
        }
        float leftMs = ms * frac / gone;
        return String.format("%.1fs", leftMs / 1000f);
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        List<Object[]> list = p.cooldowns();
        int w = width(d, p);
        panel(d, x, y, w, height(d, p));
        int a = Peregrine.get().accent();
        int ry = y + 2;
        java.util.Set<String> live = new java.util.HashSet<String>();
        for (Object[] c : list) {
            float frac = (Float) c[1];
            String key = String.valueOf(c[2]);
            live.add(key);
            d.item(c[0], x + 3, ry + 2);
            int bx = x + 24, bw = w - 30;
            d.roundRect(bx, ry + 12, bw, 3, 0x40FFFFFF);
            d.roundRect(bx, ry + 12, Math.max(2, Math.round(bw * frac)), 3, a);
            if (seconds.value) {
                String t = left(key, frac);
                if (!t.isEmpty()) {
                    d.text(t, bx, ry + 2, Theme.TEXT, true);
                }
            }
            ry += ROW;
        }
        seen.keySet().retainAll(live);
    }
}
