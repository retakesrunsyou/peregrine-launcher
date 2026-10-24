package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.peregrine.client.core.modules.Crosshair;

/**
 * The reaction test (from Custom crosshair's settings): click the moment the dot appears, five
 * times. Your average sets how early the crosshair's hit timing turns ready.
 */
final class ReactionPanel extends MenuPanel {

    private static final int ROUNDS = 5;
    private final Random random = new Random();
    private final List<Integer> times = new ArrayList<Integer>();
    private int state;            // 0 intro, 1 waiting, 2 dot shown, 3 too early, 4 results
    private long showAt, shownAt2;
    private float dx = 0.5f, dy = 0.5f;

    ReactionPanel(Peregrine pc, Menu menu) {
        super(pc, menu);
    }

    @Override
    void shown() {
        super.shown();
        state = 0;
        times.clear();
    }

    private void next() {
        state = 1;
        showAt = System.currentTimeMillis() + 1200 + random.nextInt(2300);
        dx = 0.25f + random.nextFloat() * 0.5f;
        dy = 0.35f + random.nextFloat() * 0.4f;
    }

    @Override
    void draw(Draw d, int mx, int my, float dt, long now, int accent) {
        d.textScaled("Reaction test", x, y + 1, Theme.TEXT, 1.25f, true);
        int top = y + 22, area = h - 30;
        Glass.pane(d, x, top, w, area, 8, Glass.PANE);
        spot("area", x, top, w, area);
        int cx = x + w / 2;
        if (state == 1 && now >= showAt) {
            state = 2;
            shownAt2 = now;
        }
        switch (state) {
            case 0:
                centre(d, "Click anywhere here the moment a dot appears.", cx, top + area / 2 - 14, Theme.TEXT);
                centre(d, ROUNDS + " rounds. Click to start.", cx, top + area / 2 + 2, Theme.MUTED);
                break;
            case 1:
                centre(d, "Wait for it...", cx, top + 12, Theme.FAINT);
                progress(d, cx, top + area - 18, accent);
                break;
            case 2: {
                int px = x + Math.round(w * dx), py = top + Math.round(area * dy);
                float pop = Math.min(1f, (now - shownAt2) / 90f);
                int r = Math.round(9 * (0.6f + 0.4f * pop));
                d.glow(px - r * 2, py - r * 2, r * 4, r * 4, Theme.withAlpha(accent, 0x90));
                d.roundRect(px - r, py - r, r * 2, r * 2, r, 0xFF000000 | accent);
                d.roundRect(px - r / 3, py - r / 3, r * 2 / 3, r * 2 / 3, r / 3, 0xC0FFFFFF);
                progress(d, cx, top + area - 18, accent);
                break;
            }
            case 3:
                centre(d, "Too early! Click to try that round again.", cx, top + area / 2 - 6, Theme.BAD);
                break;
            default:
                results(d, mx, my, dt, cx, top, area, accent);
                break;
        }
    }

    private void progress(Draw d, int cx, int y0, int accent) {
        int gap = 12, total = (ROUNDS - 1) * gap;
        for (int i = 0; i < ROUNDS; i++) {
            int c = i < times.size() ? (0xFF000000 | accent) : 0x40FFFFFF;
            d.roundRect(cx - total / 2 + i * gap - 3, y0, 6, 6, 3, c);
        }
        if (!times.isEmpty()) {
            String last = times.get(times.size() - 1) + " ms";
            d.text(last, cx - d.width(last) / 2, y0 - 14, Theme.MUTED, false);
        }
    }

    private void results(Draw d, int mx, int my, float dt, int cx, int top, int area, int accent) {
        int avg = average();
        String big = avg + " ms";
        d.textScaled(big, cx - Math.round(d.width(big) * 1.6f / 2), top + 16, Theme.TEXT, 1.6f, true);
        String rating = rating(avg);
        centre(d, rating, cx, top + 38, avg < 210 ? Theme.GOOD : avg < 300 ? Theme.TEXT : Theme.BAD);
        StringBuilder sb = new StringBuilder();
        for (int t : times) {
            sb.append(sb.length() == 0 ? "" : "   ").append(t);
        }
        centre(d, sb.toString(), cx, top + 54, Theme.FAINT);
        int bw = 120, bh = 16, by = top + area - 30;
        float g1 = glow("use", in(mx, my, cx - bw - 4, by, bw, bh), dt);
        Glass.pill(d, cx - bw - 4, by, bw, bh, true, g1, accent);
        d.text("Use my time", cx - bw - 4 + (bw - d.width("Use my time")) / 2, by + 4, Theme.onColor(accent), false);
        spot("use", cx - bw - 4, by, bw, bh);
        float g2 = glow("again", in(mx, my, cx + 4, by, bw, bh), dt);
        Glass.pill(d, cx + 4, by, bw, bh, false, g2, accent);
        d.text("Again", cx + 4 + (bw - d.width("Again")) / 2, by + 4, Theme.TEXT, false);
        spot("again", cx + 4, by, bw, bh);
    }

    private int average() {
        if (times.isEmpty()) {
            return 0;
        }
        // the slowest round is left out (everyone blinks once)
        List<Integer> s = new ArrayList<Integer>(times);
        java.util.Collections.sort(s);
        if (s.size() > 2) {
            s.remove(s.size() - 1);
        }
        int sum = 0;
        for (int t : s) {
            sum += t;
        }
        return sum / s.size();
    }

    static String rating(int ms) {
        if (ms < 170) {
            return "Lightning";
        }
        if (ms < 210) {
            return "Very fast";
        }
        if (ms < 250) {
            return "Fast";
        }
        if (ms < 300) {
            return "Average";
        }
        return "Take your time";
    }

    private static void centre(Draw d, String s, int cx, int y, int c) {
        d.text(s, cx - d.width(s) / 2, y, c, false);
    }

    @Override
    boolean clicked(String id, int mx, int my, int button) {
        long now = System.currentTimeMillis();
        if (id.equals("use")) {
            Crosshair c = crosshair();
            if (c != null) {
                c.setReaction(average());
                pc.markDirty();
            }
            menu.choose("cat:all");
            return true;
        }
        if (id.equals("again")) {
            shown();
            return true;
        }
        if (!id.equals("area") || state == 4) {
            return false;
        }
        switch (state) {
            case 0:
            case 3:
                next();
                break;
            case 1:
                if (now < showAt) {
                    state = 3;
                }
                break;
            case 2:
                times.add((int) Math.max(1, now - shownAt2));
                if (times.size() >= ROUNDS) {
                    state = 4;
                } else {
                    next();
                }
                break;
            default:
                break;
        }
        return true;
    }

    private Crosshair crosshair() {
        for (Module m : pc.modules()) {
            if (m instanceof Crosshair) {
                return (Crosshair) m;
            }
        }
        return null;
    }
}
