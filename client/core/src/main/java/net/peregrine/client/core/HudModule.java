package net.peregrine.client.core;

/**
 * A module that draws something on screen. Its position is stored as a
 * fraction of the free space (0 = left/top edge, 1 = right/bottom edge), so it
 * stays in the same spot at any window size.
 */
public abstract class HudModule extends Module {

    public float fx;
    public float fy;

    // Where it was last drawn, for dragging in the HUD editor.
    int lastX = -1;
    int lastY = -1;
    int lastW;
    int lastH;

    protected HudModule(String id, String name, String description, boolean defaultOn, float fx, float fy) {
        super(id, name, description, Category.HUD, defaultOn);
        this.fx = fx;
        this.fy = fy;
    }

    public abstract int width(Draw d, Platform p);

    public abstract int height(Draw d, Platform p);

    public abstract void render(Draw d, Platform p, int x, int y);

    /** False when there's nothing to show right now (no effects, singleplayer ping...). */
    public boolean hasContent(Platform p) {
        return true;
    }

    void renderAt(Draw d, Platform p) {
        int w = width(d, p);
        int h = height(d, p);
        int x = Math.round(fx * Math.max(0, p.screenWidth() - w));
        int y = Math.round(fy * Math.max(0, p.screenHeight() - h));
        render(d, p, x, y);
        lastX = x;
        lastY = y;
        lastW = w;
        lastH = h;
    }

    boolean contains(int mx, int my) {
        return lastX >= 0 && mx >= lastX && mx < lastX + lastW && my >= lastY && my < lastY + lastH;
    }

    void moveTo(int x, int y, Platform p) {
        int freeW = Math.max(1, p.screenWidth() - lastW);
        int freeH = Math.max(1, p.screenHeight() - lastH);
        fx = Math.max(0f, Math.min(1f, x / (float) freeW));
        fy = Math.max(0f, Math.min(1f, y / (float) freeH));
    }

    // ---- shared look for HUD panels

    protected static void panel(Draw d, int x, int y, int w, int h) {
        d.rect(x, y, w, h, Theme.HUD_BG);
    }

    /** Draws "label value" with the label in the accent color. Returns the width. */
    protected static int labelled(Draw d, int x, int y, String label, String value) {
        d.text(label, x, y, Peregrine.get().accent(), true);
        int lw = d.width(label + " ");
        d.text(value, x + lw, y, Theme.TEXT, true);
        return lw + d.width(value);
    }
}
