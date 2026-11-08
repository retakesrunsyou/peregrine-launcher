package net.peregrine.client.core;

/**
 * A pop-up colour picker: saturation and brightness in a square, hue in a bar beside it, an
 * opacity bar when the colour can be see-through, an RGB switch (a cycling rainbow) and Done.
 * Used for colour settings on a feature's page. Coordinates are menu units.
 */
final class ColorPicker {

    interface Target {
        int get();

        void set(int argb);

        boolean chroma();

        void setChroma(boolean on);

        boolean alpha();
    }

    static final int W = 124, SV_W = 84, SV_H = 62;

    private Target target;
    private float h, s = 1f, v = 1f;
    private int alpha = 255;
    private int x, y;
    private int dragging;   // 0 none, 1 square, 2 hue, 3 opacity

    boolean open() {
        return target != null;
    }

    Target target() {
        return target;
    }

    void show(Target t, int px, int py, int screenW, int screenH) {
        target = t;
        int c = t.get();
        float[] hsb = java.awt.Color.RGBtoHSB((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, null);
        h = hsb[0];
        s = hsb[1];
        v = hsb[2];
        alpha = (c >>> 24) & 0xFF;
        x = Math.max(4, Math.min(px, screenW - W - 4));
        y = Math.max(4, Math.min(py, screenH - height() - 4));
    }

    void close() {
        target = null;
        dragging = 0;
    }

    int height() {
        return 6 + SV_H + (target != null && target.alpha() ? 12 : 0) + 6 + 14 + 6 + 14 + 6;
    }

    boolean contains(int mx, int my) {
        return target != null && mx >= x && mx < x + W && my >= y && my < y + height();
    }

    private int color() {
        return (alpha << 24) | (java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF);
    }

    void render(Draw d, int mx, int my, int accent) {
        if (target == null) {
            return;
        }
        int hh = height();
        d.shadow(x, y, W, hh);
        d.roundRect(x, y, W, hh, 5, Theme.PANEL);
        d.roundOutline(x, y, W, hh, 5, Theme.HAIRLINE);
        int sx = x + 6, sy = y + 6;
        int cell = 2;
        for (int cx = 0; cx < SV_W; cx += cell) {
            for (int cy = 0; cy < SV_H; cy += cell) {
                d.rect(sx + cx, sy + cy, cell, cell,
                        0xFF000000 | java.awt.Color.HSBtoRGB(h, cx / (float) SV_W, 1f - cy / (float) SV_H));
            }
        }
        int kx = sx + Math.round(s * (SV_W - 1)), ky = sy + Math.round((1f - v) * (SV_H - 1));
        d.outline(kx - 2, ky - 2, 5, 5, 0xFFFFFFFF);
        d.outline(kx - 3, ky - 3, 7, 7, 0x90000000);
        int hx = sx + SV_W + 6, hw = W - 12 - SV_W - 6;
        for (int cy = 0; cy < SV_H; cy += cell) {
            d.rect(hx, sy + cy, hw, cell, 0xFF000000 | java.awt.Color.HSBtoRGB(cy / (float) SV_H, 1f, 1f));
        }
        int hy = sy + Math.round(h * (SV_H - 1));
        d.rect(hx - 1, hy - 1, hw + 2, 3, 0xFFFFFFFF);
        d.rect(hx, hy, hw, 1, 0xFF000000);
        int yy = sy + SV_H + 6;
        if (target.alpha()) {
            int aw = W - 12;
            for (int i = 0; i < aw; i += 4) {   // a checker under the opacity bar
                d.rect(sx + i, yy, 2, 3, 0xFF555555);
                d.rect(sx + i + 2, yy, 2, 3, 0xFF888888);
                d.rect(sx + i, yy + 3, 2, 3, 0xFF888888);
                d.rect(sx + i + 2, yy + 3, 2, 3, 0xFF555555);
            }
            for (int i = 0; i < aw; i++) {
                d.rect(sx + i, yy, 1, 6, ((i * 255 / Math.max(1, aw - 1)) << 24) | (color() & 0xFFFFFF));
            }
            int ax = sx + Math.round(alpha / 255f * (aw - 1));
            d.rect(ax - 1, yy - 2, 3, 10, 0xFFFFFFFF);
            yy += 12;
        }
        // the colour, its code and the RGB switch
        int c = color();
        d.roundRect(sx, yy, 14, 14, 3, c);
        d.roundOutline(sx, yy, 14, 14, 3, 0x40FFFFFF);
        d.text(String.format("#%06X", c & 0xFFFFFF), sx + 19, yy + 3, Theme.TEXT, false);
        int tx = x + W - 6 - 18;
        boolean rgb = target.chroma();
        d.text("RGB", tx - d.width("RGB") - 4, yy + 3, rgb ? Theme.TEXT : Theme.MUTED, false);
        d.roundRect(tx, yy + 2, 18, 10, 5, rgb ? accent : 0xFF2F3542);
        d.roundRect(tx + (rgb ? 9 : 1), yy + 3, 8, 8, 4, 0xFFFFFFFF);
        yy += 20;
        int bw = W - 12;
        boolean dh = mx >= sx && mx < sx + bw && my >= yy && my < yy + 14;
        d.roundRect(sx, yy, bw, 14, 4, Theme.withAlpha(accent, dh ? 0xF0 : 0xB0));
        d.text("Done", sx + (bw - d.width("Done")) / 2, yy + 3, Theme.onColor(accent), false);
    }

    /** A click: returns true if the picker used it (a click outside closes it and is used too). */
    boolean click(int mx, int my) {
        if (target == null) {
            return false;
        }
        if (!contains(mx, my)) {
            close();
            return true;
        }
        int sx = x + 6, sy = y + 6;
        int yy = sy + SV_H + 6 + (target.alpha() ? 12 : 0);
        if (mx >= sx - 2 && mx < sx + SV_W + 2 && my >= sy - 2 && my < sy + SV_H + 2) {
            dragging = 1;
        } else if (mx >= sx + SV_W + 4 && mx < x + W - 4 && my >= sy - 2 && my < sy + SV_H + 2) {
            dragging = 2;
        } else if (target.alpha() && my >= sy + SV_H + 4 && my < sy + SV_H + 14) {
            dragging = 3;
        } else if (my >= yy && my < yy + 14 && mx >= x + W - 50) {
            target.setChroma(!target.chroma());
            return true;
        } else if (my >= yy + 20 && my < yy + 34) {
            close();
            return true;
        } else {
            return true;
        }
        drag(mx, my);
        return true;
    }

    boolean drag(int mx, int my) {
        if (target == null || dragging == 0) {
            return false;
        }
        int sx = x + 6, sy = y + 6;
        if (dragging == 1) {
            s = clamp((mx - sx) / (float) (SV_W - 1));
            v = 1f - clamp((my - sy) / (float) (SV_H - 1));
        } else if (dragging == 2) {
            h = Math.min(0.999f, clamp((my - sy) / (float) (SV_H - 1)));
        } else {
            alpha = Math.round(clamp((mx - sx) / (float) (W - 13)) * 255);
        }
        target.set(color());
        return true;
    }

    void release() {
        dragging = 0;
    }

    private static float clamp(float f) {
        return Math.max(0f, Math.min(1f, f));
    }
}
