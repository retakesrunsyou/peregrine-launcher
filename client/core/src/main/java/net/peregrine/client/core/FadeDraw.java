package net.peregrine.client.core;

/** Wraps a Draw and fades everything drawn through it (0 = invisible, 1 = solid). */
final class FadeDraw implements Draw {

    private final Draw d;
    private final float alpha;

    FadeDraw(Draw d, float alpha) {
        this.d = d;
        this.alpha = alpha;
    }

    private int fade(int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
            a = 0xFF;  // Minecraft treats alpha 0 text as solid
        }
        return ((Math.round(a * alpha) & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    /** Text with almost no alpha can render oddly, so skip it until it's visible. */
    private boolean visible() {
        return alpha > 0.06f;
    }

    @Override
    public void rect(int x, int y, int w, int h, int argb) {
        d.rect(x, y, w, h, fade(argb));
    }

    @Override
    public void text(String s, int x, int y, int argb, boolean shadow) {
        if (visible()) {
            d.text(s, x, y, fade(argb), shadow && alpha > 0.6f);
        }
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
        if (alpha > 0.6f) {
            d.item(stack, x, y);
        }
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
        if (visible()) {
            d.textScaled(s, x, y, fade(argb), scale, shadow && alpha > 0.6f);
        }
    }

    /** Smooth 0→1 over durationMs since startMs (ease-out). */
    static float progress(long startMs, long durationMs) {
        float t = Math.min(1f, (System.currentTimeMillis() - startMs) / (float) durationMs);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
