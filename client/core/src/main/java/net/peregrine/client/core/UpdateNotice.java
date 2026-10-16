package net.peregrine.client.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * "Peregrine 1.4.1 is out": a card that slides in at the top right when a newer launcher has
 * been released, on the main menu and while playing. The launcher says which version it is
 * and where its releases are (-Dperegrine.version, -Dperegrine.updates); with update checks
 * switched off in the launcher it passes nothing and this stays quiet.
 */
public final class UpdateNotice {

    static final long SHOW_MS = 12000;
    static final long CHECK_EVERY = 2 * 3600_000L;
    static final int W_MIN = 168, H = 42;

    private static volatile String latest;   // a newer version, or null
    private static String shownFor = "";       // the version the card last slid in for
    private static long shownAt = -1;          // when it slid in (-1 = not showing)
    private static long holdUntil;             // hovered: keep it up a little longer
    private static float slide;                // 0 = off screen, 1 = in place
    private static float closeGlow;
    private static long lastFrame;
    private static boolean titleReminder;      // shown again once you're back at the main menu
    private static final int[] box = new int[4];
    private static Thread checker;

    private UpdateNotice() {
    }

    /** Starts checking in the background (once). */
    public static synchronized void start() {
        if (checker != null) {
            return;
        }
        final String version = System.getProperty("peregrine.version", "");
        final String repo = System.getProperty("peregrine.updates", "");
        if (version.isEmpty() || !repo.matches("[\\w.-]+/[\\w.-]+")) {
            checker = new Thread();
            return;
        }
        checker = new Thread(() -> {
            try {
                Thread.sleep(15000);  // let the game finish loading first
                while (true) {
                    try {
                        String tag = latestTag(repo);
                        if (tag != null && newer(tag, version)) {
                            latest = tag.replaceFirst("^[vV]", "");
                        }
                    } catch (Exception e) {
                        // offline or GitHub busy: try again later
                    }
                    Thread.sleep(CHECK_EVERY);
                }
            } catch (InterruptedException e) {
                // the game is closing
            }
        }, "Peregrine update check");
        checker.setDaemon(true);
        checker.start();
    }

    static String latestTag(String repo) throws java.io.IOException {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest")
                .openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "PeregrineClient");
        if (c.getResponseCode() != 200) {
            return null;
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && b.size() < 1 << 20) {
                b.write(buf, 0, n);
            }
            com.google.gson.JsonObject o = com.google.gson.JsonParser
                    .parseString(new String(b.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("tag_name") ? o.get("tag_name").getAsString() : null;
        }
    }

    /** Is version a newer than b? ("v1.4.10" > "1.4.9") */
    public static boolean newer(String a, String b) {
        int[] x = parts(a), y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) {
                return p > q;
            }
        }
        return false;
    }

    private static int[] parts(String v) {
        String[] s = v.trim().replaceFirst("^[vV]", "").split("\\.");
        int[] out = new int[s.length];
        for (int i = 0; i < s.length; i++) {
            String digits = s[i].replaceAll("\\D.*$", "");
            out[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        return out;
    }

    /** The newer version found, or null. */
    public static String available() {
        return latest;
    }

    /** For tests: pretend this version was just released. */
    public static void pretend(String version) {
        latest = version;
        shownFor = "";
    }

    /** The main menu opened: if there's an update, remind once more. */
    public static void atTitle() {
        if (latest != null && shownAt < 0 && !titleReminder && !shownFor.isEmpty()) {
            titleReminder = true;
            shownFor = "";
        }
    }

    public static boolean showing() {
        return shownAt >= 0 || slide > 0.01f;
    }

    /** Draws the card if it's due. mx/my: the mouse, or -1 while playing. */
    public static void render(Draw d, Platform p, int mx, int my, int accent) {
        start();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        String v = latest;
        if (v != null && !v.equals(shownFor)) {
            shownFor = v;
            shownAt = now;
            holdUntil = 0;
        }
        String title = "Peregrine " + v + " is out!";
        String line = "Restart the launcher to update";
        int w = v == null ? W_MIN : Math.max(W_MIN, Math.max(d.width(title), d.width(line)) + 46);
        int sw = p.screenWidth();
        boolean hover = shownAt >= 0 && mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3];
        if (hover) {
            holdUntil = now + 2500;
        }
        boolean in = shownAt >= 0 && (now - shownAt < SHOW_MS || now < holdUntil);
        if (!in && shownAt >= 0) {
            shownAt = -1;
        }
        // in: an overshooting spring; out: a quick ease away
        slide = in ? Math.min(1f, slide + dt * 3.2f) : Math.max(0f, slide - dt * 4f);
        if (slide <= 0f || v == null) {
            return;
        }
        float e = in ? backOut(slide) : slide * slide;
        int x = Math.round(sw - 6 - w + (1f - e) * (w + 14));
        int y = 6;
        box[0] = x;
        box[1] = y;
        box[2] = w;
        box[3] = H;
        Draw f = slide < 1f ? new FadeDraw(d, Math.min(1f, slide * 1.6f)) : d;

        d.shadow(x, y, w, H);
        f.roundRect(x, y, w, H, 4, Theme.withAlpha(Theme.PANEL, 0xF2));
        f.roundOutline(x, y, w, H, 4, Theme.mix(Theme.HAIRLINE, Theme.withAlpha(accent, 0x90), hover ? 1f : 0.45f));
        // an accent edge that breathes gently
        float pulse = 0.65f + 0.35f * (float) Math.sin(now / 420.0);
        f.rect(x + 1, y + 6, 2, H - 12, Theme.withAlpha(accent, Math.round(0xFF * pulse)));

        // the falcon in an accent circle
        int cx = x + 9, cy = y + (H - 22) / 2;
        f.roundRect(cx, cy, 22, 22, 11, Theme.withAlpha(accent, 0x38));
        f.roundOutline(cx, cy, 22, 22, 11, Theme.withAlpha(accent, 0x80));
        MenuIcons.draw(f, "download", cx + 6, cy + 6, accent);

        int tx = x + 38;
        f.text(title, tx, y + 9, Theme.TEXT, false);
        f.text(line, tx, y + 21, Theme.MUTED, false);

        // close button, when the mouse is over the card
        closeGlow = hover ? Math.min(1f, closeGlow + dt * 8f) : Math.max(0f, closeGlow - dt * 6f);
        if (closeGlow > 0.01f) {
            boolean overX = mx >= x + w - 13 && mx < x + w - 3 && my >= y + 3 && my < y + 13;
            int c = Theme.withAlpha(overX ? Theme.TEXT : Theme.MUTED, Math.round(0xFF * closeGlow));
            for (int i = 0; i < 5; i++) {
                f.rect(x + w - 11 + i, y + 6 + i, 1, 1, c);
                f.rect(x + w - 7 - i, y + 6 + i, 1, 1, c);
            }
        }

        // how long it stays: a thin bar that runs out
        float left = shownAt < 0 ? 0f : 1f - Math.min(1f, (now - shownAt) / (float) SHOW_MS);
        if (now < holdUntil) {
            left = Math.max(left, 0.15f);
        }
        int bw = Math.round((w - 12) * left);
        f.rect(x + 6, y + H - 4, w - 12, 1, Theme.withAlpha(Theme.HAIRLINE, 0x30));
        if (bw > 0) {
            f.rect(x + 6, y + H - 4, bw, 1, Theme.withAlpha(accent, 0xC0));
        }
    }

    /** A click: true if it was on the card (which then slides away). */
    public static boolean click(int mx, int my) {
        if (shownAt < 0 || mx < box[0] || mx >= box[0] + box[2] || my < box[1] || my >= box[1] + box[3]) {
            return false;
        }
        shownAt = -1;
        holdUntil = 0;
        return true;
    }

    private static float backOut(float t) {
        float c1 = 1.4f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
