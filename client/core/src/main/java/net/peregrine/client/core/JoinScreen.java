package net.peregrine.client.core;

/**
 * Smooth join: after you join a world, a calm loading screen stays up until the world around you
 * has finished building (or a few seconds pass), so the first rush of chunks happens behind it
 * instead of while you play.
 */
final class JoinScreen {

    private static final long MAX_MS = 9000;    // never longer than this
    private static final long MIN_MS = 700;     // and long enough not to flash
    private static final long FADE_MS = 350;

    private boolean wasInWorld;
    private long since;       // when the hold began (0: not holding)
    private long readyAt;     // when the world was ready (the fade starts)

    void render(Draw d, Platform p, int accent, boolean on) {
        long now = System.currentTimeMillis();
        boolean in = p.inWorld();
        if (in && !wasInWorld) {
            since = on && !p.terrainReady() ? now : 0;   // only when there's something to wait for
            readyAt = 0;
        }
        wasInWorld = in;
        if (!in || since == 0) {
            return;
        }
        long age = now - since;
        if (readyAt == 0 && age > MIN_MS && (p.terrainReady() || age > MAX_MS)) {
            readyAt = now;
        }
        float alpha = 1f;
        if (readyAt != 0) {
            alpha = 1f - (now - readyAt) / (float) FADE_MS;
            if (alpha <= 0f) {
                since = 0;
                return;
            }
        }
        Draw f = alpha < 1f ? new FadeDraw(d, alpha) : d;
        int w = p.screenWidth(), h = p.screenHeight();
        f.rect(0, 0, w, h, 0xF5101217);
        f.texture(Draw.GUI + "vignette.png", 0, 0, w, h, 0, 0, 1, 1, 0x60000000);
        int cx = w / 2, cy = h / 2;
        Glass.logo(f, cx - 12, cy - 34, 2.4f, accent);
        String title = "Loading the world";
        f.text(title, cx - f.width(title) / 2, cy + 2, Theme.TEXT, false);
        // a bar that fills towards the time limit, and a dot that runs along it
        int bw = 120, bx = cx - bw / 2, by = cy + 18;
        float t = Math.min(1f, age / (float) MAX_MS);
        float eased = 1f - (1f - t) * (1f - t) * (1f - t);
        f.roundRect(bx, by, bw, 3, 1, 0x30FFFFFF);
        f.roundRect(bx, by, Math.max(3, Math.round(bw * eased)), 3, 1, accent);
        String sub = "Building the chunks around you so it runs smoothly";
        f.text(sub, cx - f.width(sub) / 2, by + 9, Theme.FAINT, false);
    }
}
