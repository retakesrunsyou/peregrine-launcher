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

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        d.text(s, x, y, color(argb), shadow && m.shadow);
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
}
