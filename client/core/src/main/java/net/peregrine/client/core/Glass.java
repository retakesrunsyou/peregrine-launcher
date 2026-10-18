package net.peregrine.client.core;

/**
 * Peregrine's look: frosted glass panels, a soft light along the top edge, the amber accent
 * glowing where things are active, and the falcon watermarked in the corner. Shared by the
 * Right Shift menu, the main menu and the pause menu.
 */
public final class Glass {

    public static final int DEEP = 0xFF0C0E13;      // the darkest glass
    public static final int GLASS = 0xC80F1218;     // a window
    public static final int PANE = 0x9C161A22;      // a card on a window
    public static final int PANE_HOVER = 0xC0222833;
    public static final int EDGE = 0x2CFFFFFF;      // the bright rim of a pane
    public static final int SHEEN = 0x12FFFFFF;

    private Glass() {
    }

    /** A frosted pane: fill, a light gradient along the top, and a thin bright rim. */
    public static void pane(Draw d, int x, int y, int w, int h, int r, int fill) {
        if (w <= 0 || h <= 0) {
            return;
        }
        d.roundRect(x, y, w, h, r, fill);
        int sheen = Math.min(h / 2, 16);
        for (int i = 0; i < sheen; i++) {  // a soft light from above, fading out
            float k = 1f - i / (float) sheen;
            int alpha = Math.round(0x0C * k * k);
            if (alpha > 0) {
                int inset = i < r ? r - i : 1;
                d.rect(x + inset, y + 1 + i, w - 2 * inset, 1, Theme.withAlpha(0xFFFFFF, alpha));
            }
        }
        d.roundOutline(x, y, w, h, r, EDGE);
        d.rect(x + r + 1, y, w - 2 * r - 2, 1, 0x40FFFFFF);
    }

    /** A window: shadow, glass, and a glow of the accent along the top edge. */
    public static void window(Draw d, int x, int y, int w, int h, int fill, int accent) {
        d.shadow(x, y, w, h);
        pane(d, x, y, w, h, 6, fill);
        // the accent glow: brightest in the middle of the top edge, fading out to the sides
        int half = w / 2;
        for (int i = 0; i < half - 8; i += 2) {
            float k = 1f - i / (float) (half - 8);
            int c = Theme.withAlpha(accent, Math.round(0xD0 * k * k));
            d.rect(x + half + i, y, 2, 1, c);
            d.rect(x + half - i - 2, y, 2, 1, c);
        }
        for (int j = 1; j < 6; j++) {  // a soft haze below it
            d.rect(x + half - (half - 8) / 2, y + j, half - 8, 1, Theme.withAlpha(accent, 0x10 - j * 2));
        }
    }

    /** The falcon, large and faint, as a watermark. */
    public static void falcon(Draw d, int x, int y, float scale, int argb) {
        d.textScaled(Hooks.LOGO, x, y, argb, scale, false);
    }

    /** A pill button: glass, or the accent when primary; glow 0..1 for hover. */
    public static void pill(Draw d, int x, int y, int w, int h, boolean primary, float glow, int accent) {
        int r = Math.min(h / 2, 7);
        if (primary) {
            d.roundRect(x, y, w, h, r, Theme.mix(accent, Theme.mix(accent, 0xFFFFFFFF, 0.2f), glow));
            d.rect(x + r, y, w - 2 * r, 1, 0x50FFFFFF);
            if (glow > 0) {
                d.roundOutline(x - 1, y - 1, w + 2, h + 2, r + 1, Theme.withAlpha(accent, Math.round(0x70 * glow)));
            }
        } else {
            pane(d, x, y, w, h, r, Theme.mix(PANE, PANE_HOVER, glow));
            if (glow > 0) {
                d.roundOutline(x, y, w, h, r, Theme.withAlpha(accent, Math.round(0xB0 * glow)));
            }
        }
    }

    /** A small switch: a rounded track with a sliding dot, filled with the accent when on. 18 x 10. */
    public static void toggle(Draw d, int x, int y, float on, int accent, boolean hover) {
        int track = Theme.mix(hover ? 0x60FFFFFF : 0x38FFFFFF, accent, on);
        d.roundRect(x, y, 18, 10, 5, track);
        int kx = x + 1 + Math.round(on * 8);
        d.roundRect(kx, y + 1, 8, 8, 4, on > 0.5f ? 0xFFFFFFFF : 0xFFC9CDD6);
        if (on > 0.05f) {
            d.roundOutline(x - 1, y - 1, 20, 12, 6, Theme.withAlpha(accent, Math.round(0x50 * on)));
        }
    }

    /** A chevron that turns from pointing right (closed) to pointing down (open). */
    public static void chevron(Draw d, int x, int y, float open, int argb) {
        if (open < 0.5f) {
            for (int i = 0; i < 3; i++) {
                d.rect(x + 1 + i, y + i, 1, 1, argb);
                d.rect(x + 1 + i, y + 4 - i, 1, 1, argb);
            }
        } else {
            for (int i = 0; i < 3; i++) {
                d.rect(x + i, y + 1 + i, 1, 1, argb);
                d.rect(x + 4 - i, y + 1 + i, 1, 1, argb);
            }
        }
    }

    /** Eases v towards on (1) or off (0). */
    public static float ease(float v, boolean on, float dt) {
        return on ? Math.min(1f, v + dt * 9f) : Math.max(0f, v - dt * 6f);
    }

    /** 0..1 with a gentle overshoot, for things popping into place. */
    public static float backOut(float t) {
        float c1 = 1.5f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
