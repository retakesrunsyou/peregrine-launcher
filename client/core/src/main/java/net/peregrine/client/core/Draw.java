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

    /** A 1 px border that follows roundRect's corners. */
    default void roundOutline(int x, int y, int w, int h, int argb) {
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
        roundOutline(x - 1, y - 1, w + 2, h + 2, 0x50000000);
        roundOutline(x - 2, y - 1, w + 4, h + 3, 0x28000000);
        roundOutline(x - 3, y, w + 6, h + 4, 0x14000000);
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
