package net.peregrine.client.core;

/**
 * Applies one HUD item's style while it draws: its text and label colors, its
 * background (on, off, or a chosen opacity) and text shadow. HUD items draw with
 * the theme's colors as usual; this swaps them on the way through.
 */
final class StyleDraw implements Draw {

    private final Draw d;
    private final HudModule m;

    StyleDraw(Draw d, HudModule m) {
        this.d = d;
        this.m = m;
    }

    private int color(int argb) {
        if (argb == Theme.TEXT && m.textColor != 0) {
            return m.textColor;
        }
        if (argb == Peregrine.get().accent() && m.labelColor != 0) {
            return m.labelColor;
        }
        return argb;
    }

    @Override
    public void rect(int x, int y, int w, int h, int argb) {
        if (argb == Theme.HUD_BG) {
            if (!m.background) {
                return;
            }
            argb = Theme.withAlpha(argb, m.backgroundAlpha);
        }
        d.rect(x, y, w, h, argb);
    }

    /** The item's panel: its own corner rounding, and a border if it has one. */
    @Override
    public void roundRect(int x, int y, int w, int h, int argb) {
        if (argb != Theme.HUD_BG) {
            Draw.super.roundRect(x, y, w, h, argb);
            return;
        }
        if (m.background) {
            d.roundRect(x, y, w, h, m.radius, Theme.withAlpha(argb, m.backgroundAlpha));
        }
        if (m.border) {
            int c = m.labelColor != 0 ? m.labelColor : Peregrine.get().accent();
            d.roundOutline(x, y, w, h, m.radius, Theme.withAlpha(c, 0xD0));
        }
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        if (m.outline && !s.isEmpty()) {
            // A dark ring one pixel around the letters, then the letters (no shadow needed).
            int o = Theme.withAlpha(0xFF000000, ((argb >>> 24) * 0xC0) / 0xFF);
            String plain = strip(s);
            d.text(plain, x - 1, y, o, false);
            d.text(plain, x + 1, y, o, false);
            d.text(plain, x, y - 1, o, false);
            d.text(plain, x, y + 1, o, false);
            shadow = false;
        }
        if (m.chroma && !s.isEmpty()) {
            chroma(s, x, y, argb, shadow && m.shadow);
            return;
        }
        d.text(s, x, y, color(argb), shadow && m.shadow);
    }

    /** Text without its § color codes (the outline is one color). */
    private static String strip(String s) {
        if (s.indexOf('\u00a7') < 0) {
            return s;
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7' && i + 1 < s.length()) {
                i++;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * RGB text: each letter gets its own color from a rainbow that slides to the
     * right over time, so a color moves from one letter to the next.
     */
    private void chroma(String s, int x, int y, int argb, boolean shadow) {
        double t = (System.nanoTime() / 1e9) * m.chromaSpeed / 4.0;
        int alpha = argb >>> 24;
        int cx = x;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            if (cp != ' ') {
                double hue = t - cx / 140.0;
                d.text(ch, cx, y, (alpha << 24) | rainbow(hue), shadow);
            }
            cx += d.width(ch);
        }
    }

    /** A bright, slightly soft rainbow color for a hue (0-1, wraps around). RGB only. */
    static int rainbow(double hue) {
        double h = (hue - Math.floor(hue)) * 6.0;
        int sector = (int) h;
        double f = h - sector;
        double sat = 0.72, v = 1.0;
        double p = v * (1 - sat), q = v * (1 - sat * f), u = v * (1 - sat * (1 - f));
        double r, g, b;
        switch (sector) {
            case 0: r = v; g = u; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = u; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = u; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }

    @Override
    public int width(String s) {
        return d.width(s);
    }

    @Override
    public int lineHeight() {
        return d.lineHeight();
    }

    @Override
    public void item(Object stack, int x, int y) {
        d.item(stack, x, y);
    }

    @Override
    public void clip(int x, int y, int w, int h) {
        d.clip(x, y, w, h);
    }

    @Override
    public void unclip() {
        d.unclip();
    }

    @Override
    public void textScaled(String s, int x, int y, int argb, float scale, boolean shadow) {
        if (m.chroma && !s.isEmpty()) {
            d.pushScale(x, y, scale);
            chroma(s, 0, 0, argb, shadow && m.shadow);
            d.popScale();
            return;
        }
        d.textScaled(s, x, y, color(argb), scale, shadow && m.shadow);
    }

    @Override
    public void pushScale(int x, int y, float s) {
        d.pushScale(x, y, s);
    }

    @Override
    public void popScale() {
        d.popScale();
    }

    @Override
    public void texture(String id, int x, int y, int w, int h, float u0, float v0, float u1, float v1, int argb) {
        if (argb == Theme.HUD_BG) {  // a smooth corner of the panel: same rule as rect()
            if (!m.background) {
                return;
            }
            argb = Theme.withAlpha(argb, m.backgroundAlpha);
        }
        d.texture(id, x, y, w, h, u0, v0, u1, v1, argb);
    }

    @Override
    public void textureQuad(String id, float x, float y, float ax, float ay, float bx, float by,
                            float u0, float v0, float u1, float v1, int argb) {
        d.textureQuad(id, x, y, ax, ay, bx, by, u0, v0, u1, v1, argb);
    }

    @Override
    public boolean supportsQuads() {
        return d.supportsQuads();
    }

    @Override
    public boolean hasTextures() {
        return d.hasTextures();
    }
}
