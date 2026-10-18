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
        line(d, x + r, y, w - 2 * r, 0x40FFFFFF);
    }

    /** A 1 px line that fades out towards both ends (a plain line where textures don't work). */
    public static void line(Draw d, int x, int y, int w, int argb) {
        if (w <= 0) {
            return;
        }
        if (d.hasTextures()) {
            d.texture(Draw.GUI + "spot.png", x, y, w, 1, 0f, 0.5f, 1f, 0.5f, argb);
            return;
        }
        d.rect(x + 1, y, w - 2, 1, argb);
    }

    /** A soft round light w x h centred on cx, cy (nothing where textures don't work). */
    public static void light(Draw d, int cx, int cy, int w, int h, int argb) {
        if (d.hasTextures() && w > 0 && h > 0 && (argb >>> 24) > 0) {
            d.texture(Draw.GUI + "spot.png", cx - w / 2, cy - h / 2, w, h, 0f, 0f, 1f, 1f, argb);
        }
    }

    /** Darkens the edges of the screen, smoothly where textures work (alpha: how dark the corners get). */
    public static void vignette(Draw d, int sw, int sh, int alpha) {
        if (alpha <= 0) {
            return;
        }
        if (d.hasTextures()) {
            d.texture(Draw.GUI + "vignette.png", 0, 0, sw, sh, 0f, 0f, 1f, 1f, Theme.withAlpha(0, Math.min(255, alpha)));
            return;
        }
        int edge = Math.max(1, sw / 5);
        for (int x = 0; x < edge; x += 2) {
            float k = 1f - x / (float) edge;
            int al = Math.round(alpha * 0.6f * k * k);
            if (al > 0) {
                d.rect(x, 0, 2, sh, Theme.withAlpha(0, al));
                d.rect(sw - x - 2, 0, 2, sh, Theme.withAlpha(0, al));
            }
        }
    }

    /** Light from the top of a rounded box, fading out by its middle; it follows the corners. */
    public static void sheen(Draw d, int x, int y, int w, int h, int r, int alpha) {
        int n = Math.max(1, h / 2);
        for (int i = 1; i < n; i++) {
            float k = 1f - i / (float) n;
            int al = Math.round(alpha * k * k);
            if (al <= 0) {
                continue;
            }
            int inset = 1;
            if (i < r) {
                double dy = r - i - 0.5;
                inset = Math.max(1, (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy))));
            }
            d.rect(x + inset, y + i, w - 2 * inset, 1, Theme.withAlpha(0xFFFFFF, al));
        }
    }

    /** The falcon logo, about 9 x scale pixels: a smooth picture where textures work, else the font glyph. */
    public static void logo(Draw d, int x, int y, float scale, int argb) {
        if (d.hasTextures()) {
            int s = Math.round(9 * scale * 1.12f);
            int c = Math.round(9 * scale / 2f);
            d.texture(Art.falcon(s), x + c - s / 2, y + c - s / 2, s, s, 0f, 0f, 1f, 1f, argb);
            return;
        }
        d.textScaled(Hooks.LOGO, x, y, argb, scale, false);
    }

    /** A window: shadow, glass, and a glow of the accent along the top edge. */
    public static void window(Draw d, int x, int y, int w, int h, int fill, int accent) {
        d.shadow(x, y, w, h);
        pane(d, x, y, w, h, 6, fill);
        if (d.hasTextures()) {
            // the accent glow: a light along the middle of the top edge, spilling over the rim
            light(d, x + w / 2, y + 1, Math.round(w * 0.7f), 14, Theme.withAlpha(accent, 0x2A));
            line(d, x + w / 8, y, w - w / 4, Theme.withAlpha(accent, 0xF0));
            return;
        }
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
        logo(d, x, y, scale, argb);
    }

    /** A pill button: glass, or the accent when primary; glow 0..1 for hover. */
    public static void pill(Draw d, int x, int y, int w, int h, boolean primary, float glow, int accent) {
        int r = Math.min(h / 2, 7);
        if (primary) {
            d.glow(x + 2, y + 3, w - 4, h - 4, Theme.withAlpha(accent, Math.round(0x40 * glow)));
            d.roundRect(x, y, w, h, r, Theme.mix(accent, Theme.mix(accent, 0xFFFFFFFF, 0.2f), glow));
            sheen(d, x, y, w, h, r, 0x22);
            line(d, x + r, y, w - 2 * r, 0x60FFFFFF);
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
        if (d.hasTextures()) {  // a soft shadow under the knob
            d.roundRect(kx, y + 2, 8, 8, 4, 0x38000000);
        }
        d.roundRect(kx, y + 1, 8, 8, 4, on > 0.5f ? 0xFFFFFFFF : 0xFFC9CDD6);
        if (on > 0.05f) {
            d.roundOutline(x - 1, y - 1, 20, 12, 6, Theme.withAlpha(accent, Math.round(0x50 * on)));
        }
    }

    /** A chevron that turns from pointing right (closed) to pointing down (open). */
    public static void chevron(Draw d, int x, int y, float open, int argb) {
        if (d.hasTextures()) {
            float cx = x + 2.5f, cy = y + 2.5f, s = 8f;
            String right = Art.icon("chevron", 8);
            if (d.supportsQuads()) {  // it turns smoothly as the group opens
                double t = Math.toRadians(90 * Math.max(0f, Math.min(1f, open)));
                float ax = (float) Math.cos(t) * s, ay = (float) Math.sin(t) * s;
                float bx = -ay, by = ax;
                d.textureQuad(right, cx - (ax + bx) / 2f, cy - (ay + by) / 2f, ax, ay, bx, by, 0f, 0f, 1f, 1f, argb);
                return;
            }
            int a = (argb >>> 24) & 0xFF;
            int ix = Math.round(cx - s / 2f), iy = Math.round(cy - s / 2f), is = Math.round(s);
            if (open < 0.999f) {
                d.texture(right, ix, iy, is, is, 0f, 0f, 1f, 1f, Theme.withAlpha(argb, Math.round(a * (1f - open))));
            }
            if (open > 0.001f) {
                d.texture(Art.icon("chevron_down", 8), ix, iy, is, is, 0f, 0f, 1f, 1f, Theme.withAlpha(argb, Math.round(a * open)));
            }
            return;
        }
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
