package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Your own crosshair: shape, size, gap, thickness, color and outline. Drawn in real pixels. */
public final class Crosshair extends Module {

    static final String[] COLOR_NAMES = {"White", "Accent", "Red", "Green", "Cyan", "Yellow", "Pink", "Black"};
    static final int[] COLORS = {0xFFFFFF, 0, 0xFF3B3B, 0x3BFF5A, 0x2BE7FF, 0xFFE23B, 0xFF5FD7, 0x000000};

    private final ChoiceSetting style = add(new ChoiceSetting("style", "Shape", 0,
            "Cross", "Cross + dot", "Dot", "Circle", "T", "Square"));
    private final SliderSetting size = add(new SliderSetting("size", "Size", 2f, 20f, 1f, 7f, "%.0f"));
    private final SliderSetting gap = add(new SliderSetting("gap", "Gap", 0f, 10f, 1f, 2f, "%.0f"));
    private final SliderSetting thickness = add(new SliderSetting("thickness", "Thickness", 1f, 6f, 1f, 2f, "%.0f"));
    private final ChoiceSetting color = add(new ChoiceSetting("color", "Color", 0, COLOR_NAMES));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 20f, 100f, 5f, 100f, "%.0f%%"));
    private final BoolSetting outline = add(new BoolSetting("outline", "Dark outline", true));

    public Crosshair() {
        super("crosshair", "Custom crosshair", "Your own crosshair: shape, size, gap, color and outline",
                Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.customCrosshair = true;
    }

    @Override
    protected void onEnable() {
        Hooks.customCrosshair = true;
    }

    @Override
    protected void onDisable() {
        Hooks.customCrosshair = false;
    }

    private int argb() {
        int rgb = COLORS[color.index];
        if (color.index == 1) {
            rgb = Peregrine.get().accent() & 0xFFFFFF;
        }
        return Math.round(opacity.value / 100f * 255f) << 24 | rgb;
    }

    /** Draws at the centre of the screen. The adapter calls this in place of Minecraft's crosshair. */
    public void render(Draw d, Platform p) {
        Hooks.crosshairDraws++;
        double g = Math.max(1, p.guiScale());
        // Work in real pixels so a 1-pixel line really is one pixel.
        int sw = (int) Math.round(p.screenWidth() * g);
        int sh = (int) Math.round(p.screenHeight() * g);
        int cx = sw / 2;
        int cy = sh / 2;
        float k = (float) (1.0 / g);
        d.pushScale(0, 0, k);
        int c = argb();
        int t = Math.max(1, Math.round(thickness.value));
        int len = Math.round(size.value) * Math.max(1, (int) Math.round(g / 2));
        int gp = Math.round(gap.value) * Math.max(1, (int) Math.round(g / 2));
        int lo = cx - t / 2;   // left/top edge of a centred line of thickness t
        int to = cy - t / 2;
        int oc = Math.round(opacity.value / 100f * 0xC0) << 24;  // outline: dark, a little see-through
        switch (style.index) {
            case 0:
            case 1:
            case 4:
                if (style.index != 4) {
                    bar(d, lo, to - gp - len, t, len, c, oc);      // up (not on a T)
                }
                bar(d, lo, to + t + gp, t, len, c, oc);            // down
                bar(d, lo - gp - len, to, len, t, c, oc);          // left
                bar(d, lo + t + gp, to, len, t, c, oc);            // right
                if (style.index == 1) {
                    bar(d, lo, to, t, t, c, oc);
                }
                break;
            case 2:
                int ds = Math.max(t, Math.round(size.value / 3f) + 1);
                bar(d, cx - ds / 2, cy - ds / 2, ds, ds, c, oc);
                break;
            case 3:
                circle(d, cx, cy, Math.max(2, len / 2 + gp), t, c, oc);
                break;
            default:
                int half = len / 2 + gp;
                ring(d, cx - half, cy - half, half * 2 + t, half * 2 + t, t, c, oc);
                break;
        }
        d.popScale();
    }

    private void bar(Draw d, int x, int y, int w, int h, int c, int oc) {
        if (outline.value) {
            d.rect(x - 1, y - 1, w + 2, h + 2, oc);
        }
        d.rect(x, y, w, h, c);
    }

    private void ring(Draw d, int x, int y, int w, int h, int t, int c, int oc) {
        if (outline.value) {
            d.outline(x - 1, y - 1, w + 2, h + 2, oc);
            d.outline(x + t, y + t, w - 2 * t, h - 2 * t, oc);
        }
        for (int i = 0; i < t; i++) {
            d.outline(x + i, y + i, w - 2 * i, h - 2 * i, c);
        }
    }

    /** A circle outline, one row of pixels at a time. */
    private void circle(Draw d, int cx, int cy, int r, int t, int c, int oc) {
        int outer = r + t;
        if (outline.value) {
            span(d, cx, cy, r - 1, outer + 1, oc);
        }
        span(d, cx, cy, r, outer, c);
    }

    private static void span(Draw d, int cx, int cy, int inner, int outer, int c) {
        for (int dy = -outer; dy < outer; dy++) {
            double yy = dy + 0.5;
            int xo = (int) Math.floor(Math.sqrt(Math.max(0, outer * outer - yy * yy)));
            int xi = inner * inner - yy * yy > 0 ? (int) Math.ceil(Math.sqrt(inner * inner - yy * yy)) : 0;
            if (xo <= xi) {
                continue;
            }
            d.rect(cx - xo, cy + dy, xo - xi, 1, c);
            d.rect(cx + xi, cy + dy, xo - xi, 1, c);
        }
    }
}
