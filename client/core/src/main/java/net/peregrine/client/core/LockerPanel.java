package net.peregrine.client.core;

import java.util.List;

import net.peregrine.client.core.modules.Cosmetics;

/** The Locker inside the Right Shift menu: your player, your capes and your skins. */
final class LockerPanel extends MenuPanel {

    static final String[] TABS = {"Capes", "Skins"};

    private final PlayerPreview preview = new PlayerPreview();
    private final LockerSkins skins = new LockerSkins();
    private int tab;
    private float tabSlide = -1;
    private int trying = -1;
    private boolean dragging;
    private int pressX;
    private final int[] player = new int[4];

    LockerPanel(Peregrine pc, Menu menu) {
        super(pc, menu);
    }

    @Override
    void shown() {
        super.shown();
        preview.reload();
    }

    @Override
    String placeholder() {
        return "";
    }

    @Override
    void draw(Draw d, int mx, int my, float dt, long now, int a) {
        Platform p = pc.platform();
        // the player on the left, in a tall glass case
        int pw = Math.max(70, Math.min(110, w / 3)), ph = h - 26;
        player[0] = x;
        player[1] = y;
        player[2] = pw;
        player[3] = ph;
        Glass.pane(d, x, y, pw, ph, 6, 0x8C0E1117);
        // a soft spotlight under the player
        for (int i = 0; i < 6; i++) {
            int rw = pw - 20 - i * 6;
            d.roundRect(x + (pw - rw) / 2, y + ph - 16 + i, rw, 3, 1, Theme.withAlpha(a, 0x10 + i * 4));
        }
        preview.draw(d, p, x + 6, y + 6, pw - 12, ph - 22, Cosmetics.capeIndex());
        String hint = "Drag to turn";
        d.text(hint, x + (pw - d.width(hint)) / 2, y + ph - 12, Theme.FAINT, false);
        spot("player", x, y, pw, ph);
        if (trying >= 0) {
            int bw = pw, by = y + ph + 6;
            float g = glow("wear", in(mx, my, x, by, bw, 16), dt);
            Glass.pill(d, x, by, bw, 16, true, g, a);
            MenuIcons.draw(d, "check", x + 6, by + 4, Theme.onColor(a));
            d.text("Wear it", x + 18, by + 4, Theme.onColor(a), false);
            spot("wear", x, by, bw, 16);
        }

        // tabs and the grid on the right
        int gx = x + pw + 12, gw = w - pw - 12;
        int tx = gx;
        float target = 0;
        int[] tabX = new int[TABS.length], tabW = new int[TABS.length];
        for (int i = 0; i < TABS.length; i++) {
            tabW[i] = d.width(TABS[i]) + 18;
            tabX[i] = tx;
            if (i == tab) {
                target = tx;
            }
            tx += tabW[i] + 4;
        }
        tabSlide = tabSlide < 0 ? target : tabSlide + (target - tabSlide) * Math.min(1f, dt * 16f);
        d.roundRect(Math.round(tabSlide), y, tabW[tab], 15, 7, a);
        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == tab;
            float g = glow("tab" + i, in(mx, my, tabX[i], y, tabW[i], 15), dt);
            if (!on && g > 0) {
                d.roundRect(tabX[i], y, tabW[i], 15, 7, Theme.withAlpha(0xFFFFFF, Math.round(0x18 * g)));
            }
            d.text(TABS[i], tabX[i] + 9, y + 4, on ? Theme.onColor(a) : Theme.mix(Theme.MUTED, Theme.TEXT, g), false);
            spot("tab:" + i, tabX[i], y, tabW[i], 15);
        }
        String st = skins.status;
        if (tab == 1 && st != null && !st.isEmpty()) {
            d.text(d.trim(st, gw - (tx - gx) - 6), tx + 6, y + 4, skins.busy ? Theme.MUTED : Theme.TEXT, false);
        }
        int top = y + 22, gh = y + h - top;
        int n = tab == 0 ? Cosmetics.shown() : Math.min(48, skins.list(p).size());
        if (n == 0) {
            d.text("No skins yet.", gx, top + 6, Theme.TEXT, false);
            d.text("Add some in the launcher's Locker, then wear them here.", gx, top + 18, Theme.MUTED, false);
            return;
        }
        int cw = 64, chH = tab == 0 ? 86 : 96, gap = 7;
        int cols = Math.max(1, (gw + gap) / (cw + gap));
        cw = (gw - gap * (cols - 1)) / cols;
        int rows = (n + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (chH + gap) - gap - gh);
        int worn = Cosmetics.capeIndex();
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 380.0);
        menu.clipArea(d, gx - 2, top - 2, gw + 8, gh + 2);
        List<LockerSkins.Skin> list = tab == 1 ? skins.list(p) : null;
        for (int i = 0; i < n; i++) {
            int cx = gx + (i % cols) * (cw + gap);
            int cy = top + (i / cols) * (chH + gap) - Math.round(smooth);
            if (cy + chH < top || cy > top + gh) {
                continue;
            }
            float ap = Glass.backOut(appear(i));
            Draw cd = ap < 1f ? new FadeDraw(d, Math.max(0.05f, Math.min(1f, ap))) : d;
            cy += Math.round((1f - Math.min(1f, ap)) * 10);
            boolean hover = in(mx, my, cx, cy, cw, chH) && in(mx, my, gx, top, gw, gh);
            float g = glow("c" + tab + ":" + i, hover, dt);
            cy -= Math.round(g * 2);
            boolean picked = tab == 0 ? i == worn : i == trying;
            Glass.pane(cd, cx, cy, cw, chH, 5, Theme.mix(Glass.PANE, Glass.PANE_HOVER, g));
            if (picked) {
                cd.roundOutline(cx - 1, cy - 1, cw + 2, chH + 2, 6, Theme.withAlpha(a, Math.round(0x40 + 0x60 * pulse)));
                cd.roundOutline(cx, cy, cw, chH, 5, a);
            } else if (g > 0) {
                cd.roundOutline(cx, cy, cw, chH, 5, Theme.withAlpha(a, Math.round(0x90 * g)));
            }
            String name, sub;
            if (tab == 0) {
                String file = Cosmetics.FILES[i];
                int ph2 = chH - 36, pw2 = ph2 * 10 / 16;
                int px2 = cx + (cw - pw2) / 2, py2 = cy + 6;
                String capeTex = Cosmetics.textureId(file);
                if (capeTex != null) {
                    cd.texture(capeTex, px2, py2, pw2, ph2,
                            1f / 64, 1f / 32, 11f / 64, 17f / 32, 0xFFFFFFFF);
                } else {
                    cd.roundOutline(px2, py2, pw2, ph2, 2, 0x40FFFFFF);
                }
                name = Cosmetics.CAPES[i];
                sub = picked ? "Wearing" : i == Cosmetics.CUSTOM ? "Only you" : "Peregrine";
            } else {
                LockerSkins.Skin sk = list.get(i);
                String tex = skins.texture(p, sk);
                if (tex != null) {
                    PlayerPreview.drawFront(cd, tex, sk.slim, sk.legacy, cx + 4, cy + 5, cw - 8, chH - 32);
                }
                name = sk.name;
                sub = picked ? "Trying on" : sk.slim ? "Slim" : "Classic";
            }
            String nm = cd.trim(name, cw - 6);
            cd.text(nm, cx + (cw - cd.width(nm)) / 2, cy + chH - 22, Theme.TEXT, false);
            cd.text(sub, cx + (cw - cd.width(sub)) / 2, cy + chH - 11, picked ? a : Theme.FAINT, false);
            spot("card:" + i, cx, cy, cw, chH);
        }
        menu.unclipArea(d);
    }

    @Override
    boolean clicked(String id, int mx, int my, int button) {
        Platform p = pc.platform();
        if (id.equals("player")) {
            preview.press(mx);
            dragging = true;
            pressX = mx;
            return true;
        }
        if (id.startsWith("tab:")) {
            int t = Integer.parseInt(id.substring(4));
            if (t != tab) {
                tab = t;
                scroll = 0;
                smooth = 0;
                shownAt = System.currentTimeMillis();
            }
            return true;
        }
        if (id.equals("wear")) {
            List<LockerSkins.Skin> list = skins.list(p);
            if (trying >= 0 && trying < list.size()) {
                skins.wear(p, list.get(trying), () -> {
                    trying = -1;
                    preview.stopTrying();
                    preview.reload();
                });
            }
            return true;
        }
        if (id.startsWith("card:")) {
            int i = Integer.parseInt(id.substring(5));
            if (tab == 0) {
                Cosmetics.setCape(i);
                if (i > 0) {
                    preview.showBack();
                }
            } else {
                List<LockerSkins.Skin> list = skins.list(p);
                if (trying == i || i >= list.size()) {
                    trying = -1;
                    preview.stopTrying();
                } else {
                    LockerSkins.Skin sk = list.get(i);
                    String tex = skins.texture(p, sk);
                    if (tex != null) {
                        trying = i;
                        preview.tryOn(tex, sk.slim, sk.legacy);
                        skins.status = "Trying on " + sk.name;
                    }
                }
            }
            return true;
        }
        return false;
    }

    boolean dragged(int mx) {
        if (dragging) {
            preview.drag(mx);
            return true;
        }
        return false;
    }

    boolean released(int mx) {
        if (!dragging) {
            return false;
        }
        dragging = false;
        preview.release();
        if (Math.abs(mx - pressX) < 3) {
            preview.spin();
        }
        return true;
    }

    /** For the self-test. */
    int[] cardCenter(int t, int i) {
        for (Spot s : spots) {
            if (s.id.equals(t == tab ? "card:" + i : "tab:" + t)) {
                return new int[] {s.x + s.w / 2, s.y + s.h / 2};
            }
        }
        return null;
    }
}
