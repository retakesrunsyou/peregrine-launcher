package net.peregrine.client.core;

/** Labels drawn over the world at a 3D spot (waypoints, TNT timers). */
public final class WorldLabels {

    private WorldLabels() {
    }

    /**
     * A rounded tag with text, centered on the screen spot of (x, y, z).
     * Returns false if the spot is behind the camera.
     */
    public static boolean tag(Draw d, Platform p, double x, double y, double z, String text, int textColor,
                              int dotColor, float scale, boolean clampToScreen) {
        float[] s = p.project(x, y, z);
        if (s == null) {
            return false;
        }
        int w = d.width(text) + (dotColor != 0 ? 14 : 8);
        int h = d.lineHeight() + 5;
        float sx = s[0], sy = s[1];
        int sw = p.screenWidth(), sh = p.screenHeight();
        if (clampToScreen) {  // stays at the edge when the spot is off to the side
            float m = 4 + w * scale / 2f;
            sx = Math.max(m, Math.min(sw - m, sx));
            sy = Math.max(4 + h * scale / 2f, Math.min(sh - 4 - h * scale / 2f, sy));
        } else if (sx < -w || sx > sw + w || sy < -h || sy > sh + h) {
            return true;
        }
        if (Hooks.labelHits < 1000) {
            Hooks.labelHits++;
        }
        int bx = Math.round(sx - w * scale / 2f), by = Math.round(sy - h * scale / 2f);
        d.pushScale(bx, by, scale);
        d.roundRect(0, 0, w, h, 3, 0xA0101218);
        int tx = 4;
        if (dotColor != 0) {
            d.roundRect(4, (h - 6) / 2, 6, 6, 3, dotColor);
            tx = 13;
        }
        d.text(text, tx, 3, textColor, false);
        d.popScale();
        return true;
    }
}
