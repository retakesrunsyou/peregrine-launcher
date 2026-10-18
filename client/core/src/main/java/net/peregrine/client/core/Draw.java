package net.peregrine.client.core;

/**
 * Drawing primitives in GUI-scaled pixels. Colors are 0xAARRGGBB.
 * Each Minecraft version's adapter implements this with its own renderer.
 */
public interface Draw {

    void rect(int x, int y, int w, int h, int argb);

    void text(String s, int x, int y, int argb, boolean shadow);

    int width(String s);

    int lineHeight();

    /** Draws an item icon (16x16) for a stack the Platform handed out. */
    void item(Object stack, int x, int y);

    /** Only draw inside this rectangle until unclip() (for scrolling lists). */
    void clip(int x, int y, int w, int h);

    void unclip();

    /** Text drawn larger (scale 2 = twice the size), with its top-left at x, y. */
    void textScaled(String s, int x, int y, int argb, float scale, boolean shadow);

    /**
     * Part of a texture, stretched over x, y, w, h. id is a resource id such as
     * "peregrine-client:textures/cape/falcon.png" (or one from Platform.loadImage); u0..v1 are
     * fractions of the texture (0 to 1). argb tints it (0xFFFFFFFF = as it is; lower alpha fades it).
     */
    default void texture(String id, int x, int y, int w, int h, float u0, float v0, float u1, float v1, int argb) {
    }

    /**
     * Part of a texture drawn onto a slanted four-sided shape (for 3D): its corner at x, y, the
     * texture's u running along (ax, ay) and v along (bx, by). argb tints it.
     */
    default void textureQuad(String id, float x, float y, float ax, float ay, float bx, float by,
                             float u0, float v0, float u1, float v1, int argb) {
    }

    /** Whether textureQuad draws anything here. */
    default boolean supportsQuads() {
        return false;
    }

    /** Draw what follows scaled by s around (x, y), until popScale(). Nests. */
    default void pushScale(int x, int y, float s) {
    }

    default void popScale() {
    }

    // ---- helpers shared by everything

    default void outline(int x, int y, int w, int h, int argb) {
        rect(x, y, w, 1, argb);
        rect(x, y + h - 1, w, 1, argb);
        rect(x, y, 1, h, argb);
        rect(x + w - 1, y, 1, h, argb);
    }

    /** A filled rectangle with softly rounded corners (2 px), drawn without overlaps
     *  so see-through colors stay even. */
    default void roundRect(int x, int y, int w, int h, int argb) {
        if (hasTextures() && w >= 5 && h >= 5) {
            roundRect(x, y, w, h, 3, argb);
            return;
        }
        if (w < 5 || h < 5) {
            rect(x, y, w, h, argb);
            return;
        }
        rect(x + 2, y, w - 4, 1, argb);
        rect(x + 1, y + 1, w - 2, 1, argb);
        rect(x, y + 2, w, h - 4, argb);
        rect(x + 1, y + h - 2, w - 2, 1, argb);
        rect(x + 2, y + h - 1, w - 4, 1, argb);
    }

    /** Whether texture() draws here (then rounded shapes get smooth, anti-aliased corners). */
    default boolean hasTextures() {
        return false;
    }

    String GUI = "peregrine-client:textures/gui/";

    /** A filled rectangle with corners rounded by r pixels (0 = square). */
    default void roundRect(int x, int y, int w, int h, int r, int argb) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r >= 2 && hasTextures()) {
            // smooth corners: a quarter of a soft-edged circle in each, plain fills between them (no overlaps)
            String c = r <= 16 ? Art.round(r) : GUI + "circle.png";
            texture(c, x, y, r, r, 0f, 0f, 0.5f, 0.5f, argb);
            texture(c, x + w - r, y, r, r, 0.5f, 0f, 1f, 0.5f, argb);
            texture(c, x, y + h - r, r, r, 0f, 0.5f, 0.5f, 1f, argb);
            texture(c, x + w - r, y + h - r, r, r, 0.5f, 0.5f, 1f, 1f, argb);
            rect(x + r, y, w - 2 * r, r, argb);
            rect(x, y + r, w, h - 2 * r, argb);
            rect(x + r, y + h - r, w - 2 * r, r, argb);
            return;
        }
        if (r <= 2) {
            if (r == 2) {
                roundRect(x, y, w, h, argb);
            } else if (r == 1) {
                rect(x + 1, y, w - 2, 1, argb);
                rect(x, y + 1, w, h - 2, argb);
                rect(x + 1, y + h - 1, w - 2, 1, argb);
            } else {
                rect(x, y, w, h, argb);
            }
            return;
        }
        for (int i = 0; i < r; i++) {  // one row of each rounded end per pixel, no overlaps
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            rect(x + inset, y + i, w - 2 * inset, 1, argb);
            rect(x + inset, y + h - 1 - i, w - 2 * inset, 1, argb);
        }
        rect(x, y + r, w, h - 2 * r, argb);
    }

    /** A 1 px border that follows roundRect(x, y, w, h, r, ...)'s corners. */
    default void roundOutline(int x, int y, int w, int h, int r, int argb) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r >= 2 && r <= 16 && hasTextures()) {
            String c = Art.ring(r);
            texture(c, x, y, r, r, 0f, 0f, 0.5f, 0.5f, argb);
            texture(c, x + w - r, y, r, r, 0.5f, 0f, 1f, 0.5f, argb);
            texture(c, x, y + h - r, r, r, 0f, 0.5f, 0.5f, 1f, argb);
            texture(c, x + w - r, y + h - r, r, r, 0.5f, 0.5f, 1f, 1f, argb);
            rect(x + r, y, w - 2 * r, 1, argb);
            rect(x + r, y + h - 1, w - 2 * r, 1, argb);
            rect(x, y + r, 1, h - 2 * r, argb);
            rect(x + w - 1, y + r, 1, h - 2 * r, argb);
            return;
        }
        if (r <= 2) {
            if (r == 2) {
                roundOutline(x, y, w, h, argb);
            } else {
                outline(x, y, w, h, argb);
            }
            return;
        }
        int prev = r;
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            int len = Math.max(1, prev - inset);
            if (i == 0) {
                rect(x + inset, y, w - 2 * inset, 1, argb);
                rect(x + inset, y + h - 1, w - 2 * inset, 1, argb);
            } else {
                rect(x + inset, y + i, len, 1, argb);
                rect(x + w - inset - len, y + i, len, 1, argb);
                rect(x + inset, y + h - 1 - i, len, 1, argb);
                rect(x + w - inset - len, y + h - 1 - i, len, 1, argb);
            }
            prev = inset;
        }
        rect(x, y + r, 1, h - 2 * r, argb);
        rect(x + w - 1, y + r, 1, h - 2 * r, argb);
    }

    /** A 1 px border that follows roundRect's corners. */
    default void roundOutline(int x, int y, int w, int h, int argb) {
        if (hasTextures() && w >= 5 && h >= 5) {
            roundOutline(x, y, w, h, 3, argb);
            return;
        }
        if (w < 5 || h < 5) {
            outline(x, y, w, h, argb);
            return;
        }
        rect(x + 2, y, w - 4, 1, argb);
        rect(x + 2, y + h - 1, w - 4, 1, argb);
        rect(x, y + 2, 1, h - 4, argb);
        rect(x + w - 1, y + 2, 1, h - 4, argb);
        rect(x + 1, y + 1, 1, 1, argb);
        rect(x + w - 2, y + 1, 1, 1, argb);
        rect(x + 1, y + h - 2, 1, 1, argb);
        rect(x + w - 2, y + h - 2, 1, 1, argb);
    }

    /** A soft shadow around a box, for floating windows. */
    default void shadow(int x, int y, int w, int h) {
        if (hasTextures() && w >= 16 && h >= 16) {
            nineSlice(GUI + "shadow.png", x, y, w, h, 0xFFFFFFFF);
            return;
        }
        roundOutline(x - 1, y - 1, w + 2, h + 2, 0x50000000);
        roundOutline(x - 2, y - 1, w + 4, h + 3, 0x28000000);
        roundOutline(x - 3, y, w + 6, h + 4, 0x14000000);
    }

    /** A soft coloured light around (and under) a box, for things that are hovered or on. */
    default void glow(int x, int y, int w, int h, int argb) {
        if (hasTextures() && w > 0 && h > 0 && (argb >>> 24) > 0) {
            int gw = Math.max(16, w), gh = Math.max(16, h);  // the slices need a box at least 16 square
            nineSlice(GUI + "glow.png", x - (gw - w) / 2, y - (gh - h) / 2, gw, gh, argb);
        }
    }

    /** A 96 x 96 blur texture stretched around a box: 8 px corners outside it, edges in between. */
    default void nineSlice(String t, int x, int y, int w, int h, int argb) {
        int c = 8;
        int ox = x - c, oy = y - c, ow = w + 2 * c, oh = h + 2 * c;
        int cs = 2 * c;  // each corner piece covers 8 px outside the box and 8 inside
        texture(t, ox, oy, cs, cs, 0f, 0f, 0.5f, 0.5f, argb);
        texture(t, ox + ow - cs, oy, cs, cs, 0.5f, 0f, 1f, 0.5f, argb);
        texture(t, ox, oy + oh - cs, cs, cs, 0f, 0.5f, 0.5f, 1f, argb);
        texture(t, ox + ow - cs, oy + oh - cs, cs, cs, 0.5f, 0.5f, 1f, 1f, argb);
        texture(t, ox + cs, oy, ow - 2 * cs, cs, 0.49f, 0f, 0.51f, 0.5f, argb);
        texture(t, ox + cs, oy + oh - cs, ow - 2 * cs, cs, 0.49f, 0.5f, 0.51f, 1f, argb);
        texture(t, ox, oy + cs, cs, oh - 2 * cs, 0f, 0.49f, 0.5f, 0.51f, argb);
        texture(t, ox + ow - cs, oy + cs, cs, oh - 2 * cs, 0.5f, 0.49f, 1f, 0.51f, argb);
        if (t.endsWith("glow.png")) {  // the glow is solid in the middle too
            texture(t, ox + cs, oy + cs, ow - 2 * cs, oh - 2 * cs, 0.49f, 0.49f, 0.51f, 0.51f, argb);
        }
    }

    default String trim(String s, int maxWidth) {
        if (width(s) <= maxWidth) {
            return s;
        }
        String dots = "…";
        int end = s.length();
        while (end > 0 && width(s.substring(0, end) + dots) > maxWidth) {
            end--;
        }
        return s.substring(0, end) + dots;
    }
}
