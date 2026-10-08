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

    // ---- helpers shared by everything

    default void outline(int x, int y, int w, int h, int argb) {
        rect(x, y, w, 1, argb);
        rect(x, y + h - 1, w, 1, argb);
        rect(x, y, 1, h, argb);
        rect(x + w - 1, y, 1, h, argb);
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
