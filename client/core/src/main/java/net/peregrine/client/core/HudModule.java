package net.peregrine.client.core;

/**
 * A module that draws something on screen. Its position is stored as a
 * fraction of the free space (0 = left/top edge, 1 = right/bottom edge), so it
 * stays in the same spot at any window size.
 */
public abstract class HudModule extends Module {

    public float fx;
    public float fy;

    // Style, set in the HUD editor (right-click an item). 0 colors mean "default".
    public float scale = DEFAULT_SCALE;
    public int textColor;
    public int labelColor;
    public boolean background = true;
    public int backgroundAlpha = DEFAULT_BG_ALPHA;
    public boolean shadow = true;
    static final int DEFAULT_BG_ALPHA = (Theme.HUD_BG >>> 24) & 0xFF;
    /** New HUD items start at half size: small and tidy, bigger is a scroll away. */
    public static final float DEFAULT_SCALE = 0.5f;
    static final float MIN_SCALE = 0.4f;
    static final float MAX_SCALE = 3f;
    private final float defaultFx;
    private final float defaultFy;

    // Where it was last drawn, for dragging in the HUD editor.
    int lastX = -1;
    int lastY = -1;
    int lastW;
    int lastH;

    protected HudModule(String id, String name, String description, boolean defaultOn, float fx, float fy) {
        super(id, name, description, Category.HUD, defaultOn);
        this.fx = fx;
        this.fy = fy;
        this.defaultFx = fx;
        this.defaultFy = fy;
    }

    /** Back to how it came: default place, size and colors. */
    public void resetStyle() {
        fx = defaultFx;
        fy = defaultFy;
        scale = DEFAULT_SCALE;
        textColor = 0;
        labelColor = 0;
        background = true;
        backgroundAlpha = DEFAULT_BG_ALPHA;
        shadow = true;
    }

    boolean styled() {
        return scale != DEFAULT_SCALE || textColor != 0 || labelColor != 0 || !background
                || backgroundAlpha != DEFAULT_BG_ALPHA || !shadow;
    }

    void setScaleKeepingCorner(float s) {
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round(s * 20) / 20f));  // 5% steps
    }

    public abstract int width(Draw d, Platform p);

    public abstract int height(Draw d, Platform p);

    public abstract void render(Draw d, Platform p, int x, int y);

    /** False when there's nothing to show right now (no effects, singleplayer ping...). */
    public boolean hasContent(Platform p) {
        return true;
    }

    void renderAt(Draw d, Platform p) {
        Draw sd = new StyleDraw(d, this);
        int w = width(sd, p);
        int h = height(sd, p);
        int sw = (int) Math.ceil(w * scale);
        int sh = (int) Math.ceil(h * scale);
        int x = Math.round(fx * Math.max(0, p.screenWidth() - sw));
        int y = Math.round(fy * Math.max(0, p.screenHeight() - sh));
        if (scale == 1f) {
            render(sd, p, x, y);
        } else {
            d.pushScale(x, y, scale);
            render(sd, p, 0, 0);
            d.popScale();
        }
        lastX = x;
        lastY = y;
        lastW = sw;
        lastH = sh;
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
        d.roundRect(x, y, w, h, Theme.HUD_BG);  // StyleDraw applies the item's background setting
    }

    /** Draws "label value" with the label in the accent color. Returns the width. */
    protected static int labelled(Draw d, int x, int y, String label, String value) {
        d.text(label, x, y, Peregrine.get().accent(), true);  // StyleDraw recolors per item
        int lw = d.width(label + " ");
        d.text(value, x + lw, y, Theme.TEXT, true);
        return lw + d.width(value);
    }
}
