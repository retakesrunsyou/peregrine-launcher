package net.peregrine.client.core;

import net.peregrine.client.core.modules.MainMenu;

/**
 * Edit your main menu picture like the HUD editor: drag it to move it, drag a corner (or
 * scroll) to make it bigger or smaller, snap it to the middle and the edges, and pick
 * quick layouts from the bar at the top. Opened from the Locker's Backgrounds tab.
 */
final class BackgroundEditor {

    static final float MIN_ZOOM = 0.25f, MAX_ZOOM = 5f;
    static final int SNAP = 5, HANDLE = 6;

    private final TitleMenu menu;
    private boolean open;
    private float t;                       // 0 = closed, 1 = open (eased in render)
    private float z = -1, x, y;            // what's on screen, gliding towards the settings
    private int drag = -1;                 // -1 none, 4 moving, 0..3 a corner (TL, TR, BL, BR)
    private float pressMx, pressMy, startX, startY, ax, ay;
    private boolean snapX, snapY, snapLeft, snapRight, snapTop, snapBottom;
    private long resizedAt;                // shows the size for a moment after a resize
    private final float[] handleGlow = new float[4];
    float[] lastPlacement;                 // where the picture was drawn this frame

    private final TitleMenu.Button fill = new TitleMenu.Button("Fill screen", "fit", null, false);
    private final TitleMenu.Button whole = new TitleMenu.Button("Whole picture", "image", null, false);
    private final TitleMenu.Button drift = new TitleMenu.Button("Drift: on", "reset", null, false);
    private final TitleMenu.Button dim = new TitleMenu.Button("Darken: 0%", "sliders", null, false);
    private final TitleMenu.Button done = new TitleMenu.Button("Done", "check", null, true);
    private final TitleMenu.Button[] bar = {fill, whole, drift, dim, done};
    private final int[] barBox = new int[4];

    BackgroundEditor(TitleMenu menu) {
        this.menu = menu;
    }

    boolean active() {
        return open || t > 0.001f;
    }

    boolean isOpen() {
        return open;
    }

    float openness() {
        return t;
    }

    void open() {
        open = true;
        drag = -1;
    }

    void close(Peregrine pc) {
        open = false;
        drag = -1;
        pc.markDirty();
    }

    float zoom(MainMenu m) {
        return z < 0 ? m.zoom.value : z;
    }

    float cx(MainMenu m) {
        return z < 0 ? m.posX.value : x;
    }

    float cy(MainMenu m) {
        return z < 0 ? m.posY.value : y;
    }

    /** Glides what's shown towards the settings (so Fill and Whole picture move smoothly). */
    void update(MainMenu m, float dt) {
        float target = open ? 1f : 0f;
        t += (target - t) * Math.min(1f, dt * 10f);
        if (Math.abs(target - t) < 0.002f) {
            t = target;
        }
        if (m == null) {
            return;
        }
        if (z < 0 || drag >= 0) {
            z = m.zoom.value;
            x = m.posX.value;
            y = m.posY.value;
            return;
        }
        float k = Math.min(1f, dt * 11f);
        z += (m.zoom.value - z) * k;
        x += (m.posX.value - x) * k;
        y += (m.posY.value - y) * k;
    }

    // ------------------------------------------------------------------ drawing

    void render(Draw raw, Platform p, MainMenu m, int mx, int my, float dt, int accent) {
        if (t <= 0.001f || m == null) {
            return;
        }
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        int sw = p.screenWidth(), sh = p.screenHeight();
        float[] r = lastPlacement;
        if (r != null) {
            int rx = Math.round(r[0]), ry = Math.round(r[1]), rw = Math.round(r[2]), rh = Math.round(r[3]);
            // snap guides
            int guide = Theme.withAlpha(accent, 0xB0);
            if (drag == 4 && snapX) {
                d.rect(sw / 2, 0, 1, sh, guide);
            }
            if (drag == 4 && snapY) {
                d.rect(0, sh / 2, sw, 1, guide);
            }
            if (drag >= 0 && snapLeft) {
                d.rect(0, 0, 2, sh, guide);
            }
            if (drag >= 0 && snapRight) {
                d.rect(sw - 2, 0, 2, sh, guide);
            }
            if (drag >= 0 && snapTop) {
                d.rect(0, 0, sw, 2, guide);
            }
            if (drag >= 0 && snapBottom) {
                d.rect(0, sh - 2, sw, 2, guide);
            }
            // the frame and its corner handles
            boolean overPicture = mx >= rx && mx < rx + rw && my >= ry && my < ry + rh && !overBar(mx, my);
            int frame = Theme.withAlpha(accent, drag >= 0 || overPicture ? 0xE0 : 0x90);
            d.outline(rx, ry, rw, rh, frame);
            int[][] corners = corners(r);
            for (int i = 0; i < 4; i++) {
                boolean hot = drag == i || (drag < 0 && near(mx, my, corners[i]));
                handleGlow[i] = hot ? Math.min(1f, handleGlow[i] + dt * 10f) : Math.max(0f, handleGlow[i] - dt * 7f);
                int s = HANDLE + Math.round(handleGlow[i] * 3);
                int hx = corners[i][0] - s / 2, hy = corners[i][1] - s / 2;
                hx = Math.max(1, Math.min(sw - s - 1, hx));
                hy = Math.max(1, Math.min(sh - s - 1, hy));
                d.rect(hx - 1, hy - 1, s + 2, s + 2, Theme.withAlpha(0, 0x90));
                d.rect(hx, hy, s, s, Theme.mix(0xFFFFFFFF, accent, handleGlow[i]));
            }
            // the size, while resizing (and a moment after)
            long now = System.currentTimeMillis();
            if ((drag >= 0 && drag < 4) || now - resizedAt < 900) {
                float a = drag >= 0 && drag < 4 ? 1f : 1f - (now - resizedAt) / 900f;
                String size = Math.round(m.zoom.value * 100) + "%";
                int bw = d.width(size) + 10;
                int bx = Math.max(4, Math.min(sw - bw - 4, mx + 10)), by = Math.max(4, Math.min(sh - 18, my + 10));
                Draw sd = a < 1f ? new FadeDraw(d, a) : d;
                sd.roundRect(bx, by, bw, 14, 3, Theme.withAlpha(Theme.PANEL, 0xF0));
                sd.text(size, bx + 5, by + 3, Theme.TEXT, false);
            }
        }

        // the bar at the top drops in with a little bounce
        layoutBar(d, sw);
        float drop = backOut(Math.min(1f, t));
        int off = Math.round((1f - drop) * -40);
        drift.label = "Drift: " + (m.drift.value ? "on" : "off");
        dim.label = "Darken: " + Math.round(m.dim.value) + "%";
        layoutBar(d, sw);
        d.pushScale(0, off, 1f);
        d.shadow(barBox[0], barBox[1], barBox[2], barBox[3]);
        d.roundRect(barBox[0], barBox[1], barBox[2], barBox[3], 5, Theme.withAlpha(Theme.PANEL, 0xF0));
        d.roundOutline(barBox[0], barBox[1], barBox[2], barBox[3], 5, Theme.HAIRLINE);
        for (TitleMenu.Button b : bar) {
            b.draw(d, mx, my - off, dt, accent, true);
        }
        d.popScale();

        String hint = "Drag to move  ·  drag a corner or scroll to resize  ·  Esc when you're done";
        d.text(hint, (sw - d.width(hint)) / 2, sh - 16, Theme.withAlpha(Theme.TEXT, 0xC8), true);
    }

    private void layoutBar(Draw d, int sw) {
        int gap = 4, pad = 5, h = TitleMenu.SMALL_H + 3;
        int total = 0;
        for (TitleMenu.Button b : bar) {
            total += width(d, b) + gap;
        }
        total -= gap;
        int x = (sw - total) / 2, y = 8;
        barBox[0] = x - pad;
        barBox[1] = y - pad;
        barBox[2] = total + pad * 2;
        barBox[3] = h + pad * 2;
        for (TitleMenu.Button b : bar) {
            int w = width(d, b);
            b.place(x, y, w, h);
            x += w + gap;
        }
    }

    private static int width(Draw d, TitleMenu.Button b) {
        return (d == null ? b.label.length() * 6 : d.width(b.label)) + 31;
    }

    private boolean overBar(int mx, int my) {
        return mx >= barBox[0] && mx < barBox[0] + barBox[2] && my >= barBox[1] && my < barBox[1] + barBox[3];
    }

    private static int[][] corners(float[] r) {
        int x0 = Math.round(r[0]), y0 = Math.round(r[1]), x1 = Math.round(r[0] + r[2]), y1 = Math.round(r[1] + r[3]);
        return new int[][] {{x0, y0}, {x1, y0}, {x0, y1}, {x1, y1}};
    }

    private boolean near(int mx, int my, int[] c) {
        Platform p = Peregrine.get().platform();
        int cx = Math.max(4, Math.min(p.screenWidth() - 4, c[0])), cy = Math.max(4, Math.min(p.screenHeight() - 4, c[1]));
        return Math.abs(mx - cx) <= HANDLE + 2 && Math.abs(my - cy) <= HANDLE + 2;
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(Peregrine pc, MainMenu m, int mx, int my) {
        if (!open || m == null) {
            return false;
        }
        Platform p = pc.platform();
        int sw = p.screenWidth(), sh = p.screenHeight();
        layoutBar(null, sw);
        if (overBar(mx, my)) {
            if (done.contains(mx, my)) {
                close(pc);
            } else if (fill.contains(mx, my)) {
                m.zoom.set(1f);
                m.posX.set(0.5f);
                m.posY.set(0.5f);
            } else if (whole.contains(mx, my)) {
                int[] size = menu.customSize();
                float cover = Math.max(sw / (float) size[0], sh / (float) size[1]);
                float contain = Math.min(sw / (float) size[0], sh / (float) size[1]);
                m.zoom.set(contain / cover);
                m.posX.set(0.5f);
                m.posY.set(0.5f);
            } else if (drift.contains(mx, my)) {
                m.drift.value = !m.drift.value;
            } else if (dim.contains(mx, my)) {
                float v = m.dim.value + 20f;
                m.dim.set(v > 60.5f ? 0f : v);
            }
            pc.markDirty();
            return true;
        }
        float[] r = lastPlacement;
        if (r == null) {
            return true;
        }
        int[][] c = corners(r);
        for (int i = 0; i < 4; i++) {
            if (near(mx, my, c[i])) {
                drag = i;
                ax = i % 2 == 0 ? r[0] + r[2] : r[0];  // the opposite corner stays put
                ay = i < 2 ? r[1] + r[3] : r[1];
                return true;
            }
        }
        drag = 4;  // anywhere else: move the picture
        pressMx = mx;
        pressMy = my;
        startX = m.posX.value;
        startY = m.posY.value;
        return true;
    }

    boolean mouseDragged(Peregrine pc, MainMenu m, int mx, int my) {
        if (!open || drag < 0 || m == null) {
            return open;
        }
        Platform p = pc.platform();
        int sw = p.screenWidth(), sh = p.screenHeight();
        int[] size = menu.customSize();
        float cover = Math.max(sw / (float) size[0], sh / (float) size[1]);
        if (drag == 4) {
            float cx = startX + (mx - pressMx) / sw, cy = startY + (my - pressMy) / sh;
            float[] r = TitleMenu.placement(size[0], size[1], sw, sh, m.zoom.value, cx, cy);
            float px = cx * sw, py = cy * sh;
            snapX = Math.abs(px - sw / 2f) < SNAP;
            snapY = Math.abs(py - sh / 2f) < SNAP;
            if (snapX) {
                px = sw / 2f;
            }
            if (snapY) {
                py = sh / 2f;
            }
            snapLeft = !snapX && Math.abs(r[0]) < SNAP;
            snapRight = !snapX && !snapLeft && Math.abs(r[0] + r[2] - sw) < SNAP;
            snapTop = !snapY && Math.abs(r[1]) < SNAP;
            snapBottom = !snapY && !snapTop && Math.abs(r[1] + r[3] - sh) < SNAP;
            if (snapLeft) {
                px = r[2] / 2;
            } else if (snapRight) {
                px = sw - r[2] / 2;
            }
            if (snapTop) {
                py = r[3] / 2;
            } else if (snapBottom) {
                py = sh - r[3] / 2;
            }
            m.posX.set(px / sw);
            m.posY.set(py / sh);
        } else {
            // keep the shape: the dragged corner follows the mouse along the picture's diagonal
            float aspect = size[0] / (float) size[1];
            float w = Math.max(Math.abs(mx - ax), Math.abs(my - ay) * aspect);
            float zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, w / (size[0] * cover)));
            w = size[0] * cover * zoom;
            float h = w / aspect;
            float sx = drag % 2 == 0 ? -1 : 1, sy = drag < 2 ? -1 : 1;
            float cx = ax + sx * w / 2, cy = ay + sy * h / 2;
            // snap the moving edges to the screen's edges
            float nx = drag % 2 == 0 ? cx - w / 2 : cx + w / 2;
            snapLeft = drag % 2 == 0 && Math.abs(nx) < SNAP;
            snapRight = drag % 2 == 1 && Math.abs(nx - sw) < SNAP;
            snapTop = snapBottom = snapX = snapY = false;
            if (snapLeft || snapRight) {
                w = Math.abs((snapLeft ? 0 : sw) - ax);
                zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, w / (size[0] * cover)));
                w = size[0] * cover * zoom;
                h = w / aspect;
                cx = ax + sx * w / 2;
                cy = ay + sy * h / 2;
            }
            m.zoom.set(zoom);
            m.posX.set(cx / sw);
            m.posY.set(cy / sh);
            resizedAt = System.currentTimeMillis();
        }
        return true;
    }

    boolean mouseReleased(Peregrine pc) {
        if (!open) {
            return false;
        }
        if (drag >= 0) {
            drag = -1;
            snapX = snapY = snapLeft = snapRight = snapTop = snapBottom = false;
            pc.markDirty();
        }
        return true;
    }

    /** Scroll: bigger or smaller, keeping the spot under the mouse where it is. */
    boolean mouseScrolled(Peregrine pc, MainMenu m, int mx, int my, double amount) {
        if (!open || m == null || amount == 0) {
            return open;
        }
        Platform p = pc.platform();
        int sw = p.screenWidth(), sh = p.screenHeight();
        float old = m.zoom.value;
        float zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, old * (amount > 0 ? 1.08f : 1f / 1.08f)));
        float k = zoom / old;
        float cx = m.posX.value * sw, cy = m.posY.value * sh;
        m.zoom.set(zoom);
        m.posX.set((mx - (mx - cx) * k) / sw);
        m.posY.set((my - (my - cy) * k) / sh);
        z = m.zoom.value;  // the wheel answers straight away
        x = m.posX.value;
        y = m.posY.value;
        resizedAt = System.currentTimeMillis();
        pc.markDirty();
        return true;
    }

    /** For the self-test: the middle of a bar button (0 fill, 1 whole, 2 drift, 3 darken, 4 done). */
    int[] barButton(int i) {
        layoutBar(null, Peregrine.get().platform().screenWidth());
        TitleMenu.Button b = bar[i];
        return new int[] {b.x + b.w / 2, b.y + b.h / 2};
    }

    /** For the self-test: a corner of the picture (0 TL, 1 TR, 2 BL, 3 BR), kept on screen. */
    int[] corner(int i) {
        if (lastPlacement == null) {
            return null;
        }
        Platform p = Peregrine.get().platform();
        int[] c = corners(lastPlacement)[i];
        return new int[] {Math.max(4, Math.min(p.screenWidth() - 4, c[0])), Math.max(4, Math.min(p.screenHeight() - 4, c[1]))};
    }

    private static float backOut(float t) {
        float c1 = 1.3f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }
}
