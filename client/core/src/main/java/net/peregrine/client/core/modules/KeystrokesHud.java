package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Platform.Key;
import net.peregrine.client.core.Theme;

public final class KeystrokesHud extends HudModule {

    private static final int GAP = 2;

    private final net.peregrine.client.core.settings.BoolSetting mouse = add(
            new net.peregrine.client.core.settings.BoolSetting("mouse", "Mouse buttons", true));
    private final net.peregrine.client.core.settings.BoolSetting cps = add(
            new net.peregrine.client.core.settings.BoolSetting("cps", "Clicks per second on the mouse keys", true));
    private final net.peregrine.client.core.settings.BoolSetting space = add(
            new net.peregrine.client.core.settings.BoolSetting("space", "Space bar", true));
    private final net.peregrine.client.core.settings.BoolSetting sneak = add(
            new net.peregrine.client.core.settings.BoolSetting("sneak", "Sneak key", false));
    private final net.peregrine.client.core.settings.SliderSetting size = add(
            new net.peregrine.client.core.settings.SliderSetting("size", "Key size", 16f, 30f, 1f, 22f, "%.0f px"));
    private final net.peregrine.client.core.settings.SliderSetting fade = add(
            new net.peregrine.client.core.settings.SliderSetting("fade", "Press fade", 0f, 300f, 10f, 90f, "%.0f ms"));

    /** How pressed each key looks right now (0 up, 1 down), for the fade. */
    private final java.util.Map<String, Float> press = new java.util.HashMap<String, Float>();
    private long lastFrame;
    private float dt;

    public KeystrokesHud() {
        super("keystrokes", "Keystrokes", "Shows WASD, mouse buttons and jump as you press them",
                false, 0f, 1f);
    }

    private int s() {
        return Math.round(size.value);
    }

    @Override
    public int width(Draw d, Platform p) {
        return s() * 3 + GAP * 2;
    }

    @Override
    public int height(Draw d, Platform p) {
        int S = s();
        return S * 2 + GAP + (mouse.value ? S + GAP : 0) + (space.value ? 12 + GAP : 0) + (sneak.value ? 12 + GAP : 0);
    }

    private float level(String id, boolean down) {
        Float cur = press.get(id);
        float v = cur == null ? (down ? 1f : 0f) : cur;
        float ms = fade.value;
        if (ms <= 0) {
            v = down ? 1f : 0f;
        } else {
            float stepUp = dt * 1000f / Math.max(1f, ms * 0.35f);  // pressing shows almost at once
            float stepDown = dt * 1000f / ms;
            v = down ? Math.min(1f, v + stepUp) : Math.max(0f, v - stepDown);
        }
        press.put(id, v);
        return v;
    }

    private static int mix(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    private void key(Draw d, int x, int y, int w, int h, String id, String label, boolean down) {
        float t = level(id, down);
        int accent = Peregrine.get().accent();
        // The panel color goes through the item's style (background, corners, border).
        d.roundRect(x, y, w, h, Theme.HUD_BG);
        if (t > 0f) {
            d.roundRect(x, y, w, h, radius, Theme.withAlpha(accent, Math.round(0xD0 * t)));
        }
        if (label.isEmpty()) {
            return;
        }
        int tx = x + (w - d.width(label)) / 2;
        int ty = y + (h - d.lineHeight()) / 2 + 1;
        d.text(label, tx, ty, t > 0.5f ? mix(Theme.TEXT, 0xFF15171C, (t - 0.5f) * 2f) : Theme.TEXT, t < 0.5f);
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        long now = System.nanoTime();
        dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        int S = s();
        int col2 = x + S + GAP;
        int col3 = x + (S + GAP) * 2;
        key(d, col2, y, S, S, "w", "W", p.isDown(Key.FORWARD));
        int row2 = y + S + GAP;
        key(d, x, row2, S, S, "a", "A", p.isDown(Key.LEFT));
        key(d, col2, row2, S, S, "s", "S", p.isDown(Key.BACK));
        key(d, col3, row2, S, S, "d", "D", p.isDown(Key.RIGHT));
        int row = row2 + S + GAP;
        int full = S * 3 + GAP * 2;
        if (mouse.value) {
            int half = (full - GAP) / 2;
            Peregrine pc = Peregrine.get();
            key(d, x, row, half, S, "lmb", cps.value ? "LMB " + pc.cps(0) : "LMB", p.isDown(Key.ATTACK));
            key(d, x + half + GAP, row, full - half - GAP, S, "rmb", cps.value ? "RMB " + pc.cps(1) : "RMB", p.isDown(Key.USE));
            row += S + GAP;
        }
        if (space.value) {
            boolean down = p.isDown(Key.JUMP);
            key(d, x, row, full, 12, "space", "", down);
            float t = press.get("space");
            d.rect(x + S, row + 5, S + GAP * 2, 2, t > 0.5f ? 0xFF15171C : Theme.TEXT);
            row += 12 + GAP;
        }
        if (sneak.value) {
            key(d, x, row, full, 12, "sneak", "Shift", p.isDown(Key.SNEAK));
        }
    }
}
