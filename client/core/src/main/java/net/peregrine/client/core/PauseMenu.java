package net.peregrine.client.core;

/**
 * Peregrine's pause menu: a glass card in the middle of the screen. Your head and name and
 * where you're playing at the top, a big Resume, four tiles (Peregrine modules, Options,
 * Locker, and the rest of Minecraft's menu), and Leave at the bottom.
 */
public final class PauseMenu {

    static final int W = 236, PAD = 12, TILE_H = 38, GAP = 6;

    private final Peregrine pc;
    private final TitleMenu.Button resume = new TitleMenu.Button("Resume game", "play", Platform.Screen.NONE, true);
    private final TitleMenu.Button modules = new TitleMenu.Button("Modules", "grid", Platform.Screen.PEREGRINE_MENU, false);
    private final TitleMenu.Button options = new TitleMenu.Button("Options", "sliders", Platform.Screen.OPTIONS, false);
    private final TitleMenu.Button locker = new TitleMenu.Button("Locker", "shirt", Platform.Screen.PEREGRINE_MENU, false);
    private final TitleMenu.Button more = new TitleMenu.Button("Advancements, stats & more", "more", Platform.Screen.VANILLA_PAUSE, false);
    private TitleMenu.Button leave = new TitleMenu.Button("Leave server", "leave", null, false);
    private final int[] box = new int[4];
    private long shownAt = -1, lastFrame;

    PauseMenu(Peregrine pc) {
        this.pc = pc;
    }

    private static final String[] IDS = {"resume", "modules", "options", "locker", "more", "leave"};
    /** Dragging a button to a new place: which one, where it was grabbed, and where the mouse is. */
    private TitleMenu.Button grabbed;
    private int grabX, grabY, dragX, dragY;
    private boolean dragging;

    private TitleMenu.Button byId(String id) {
        switch (id) {
            case "resume": return resume;
            case "modules": return modules;
            case "options": return options;
            case "locker": return locker;
            case "more": return more;
            case "leave": return leave;
            default: return null;
        }
    }

    /** The buttons in their places (your order, any missing ones after). */
    private TitleMenu.Button[] buttons() {
        TitleMenu.Button[] out = new TitleMenu.Button[6];
        int n = 0;
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (String id : order().split(",")) {
            TitleMenu.Button b = byId(id.trim());
            if (b != null && seen.add(id.trim()) && n < 6) {
                out[n++] = b;
            }
        }
        for (String id : IDS) {
            if (seen.add(id)) {
                out[n++] = byId(id);
            }
        }
        return out;
    }

    private String order() {
        net.peregrine.client.core.modules.PauseMenuToggle m = (net.peregrine.client.core.modules.PauseMenuToggle) pc.module("pause_menu");
        return m == null ? net.peregrine.client.core.modules.PauseMenuToggle.DEFAULT_ORDER : m.order.value;
    }

    private String idOf(TitleMenu.Button b) {
        for (String id : IDS) {
            if (byId(id) == b) {
                return id;
            }
        }
        return "";
    }

    /** Swaps two buttons' places and saves it. */
    private void swap(TitleMenu.Button a, TitleMenu.Button b) {
        TitleMenu.Button[] bs = buttons();
        StringBuilder sb = new StringBuilder();
        for (TitleMenu.Button x : bs) {
            TitleMenu.Button put = x == a ? b : x == b ? a : x;
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(idOf(put));
        }
        net.peregrine.client.core.modules.PauseMenuToggle m = (net.peregrine.client.core.modules.PauseMenuToggle) pc.module("pause_menu");
        if (m != null) {
            m.order.value = sb.toString();
            pc.markDirty();
        }
    }

    public void shown() {
        shownAt = System.currentTimeMillis();
        lastFrame = 0;
    }

    private void layout(Platform p) {
        String l = p.localWorld() ? "Save and quit to title" : "Leave server";
        if (!leave.label.equals(l)) {
            leave = new TitleMenu.Button(l, "leave", null, false);
        }
        int w = Math.min(W + 2 * PAD, p.screenWidth() - 16);
        int inner = w - 2 * PAD;
        int h = PAD + 30 + 24 + GAP + 2 * (TILE_H + GAP) + 18 + PAD;
        box[0] = (p.screenWidth() - w) / 2;
        box[1] = Math.max(4, (p.screenHeight() - h) / 2);
        box[2] = w;
        box[3] = h;
        int x = box[0] + PAD, y = box[1] + PAD + 30;
        TitleMenu.Button[] bs = buttons();   // places: a wide one, four tiles, a bar at the bottom
        bs[0].place(x, y, inner, 24);
        y += 24 + GAP;
        int half = (inner - GAP) / 2;
        bs[1].place(x, y, half, TILE_H);
        bs[2].place(x + half + GAP, y, inner - half - GAP, TILE_H);
        y += TILE_H + GAP;
        bs[3].place(x, y, half, TILE_H);
        bs[4].place(x + half + GAP, y, inner - half - GAP, TILE_H);
        y += TILE_H + GAP;
        bs[5].place(x, y, inner, 18);
    }

    public void render(Draw raw, int mx, int my) {
        SelfTest.count("pause");
        float t = shownAt < 0 ? 1f : FadeDraw.progress(shownAt, 260);
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        Platform p = pc.platform();
        layout(p);
        int a = pc.accent();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int sw = p.screenWidth(), sh = p.screenHeight();
        // the game dims, a little more at the edges
        if (net.peregrine.client.core.modules.Menu3D.get() == null || !p.supports("menu_3d")) {   // a 3D panel doesn't dim the world
            d.rect(0, 0, sw, sh, 0x48000000);
            Glass.vignette(d, sw, sh, 0x90);
        }

        // the card rises into place with a little overshoot
        int rise = Math.round((1f - Glass.backOut(t)) * 14);
        int x = box[0], y = box[1] + rise;
        Glass.window(d, x, y, box[2], box[3], 0xD80F1218, a);
        Glass.falcon(d, x + box[2] - 58, y + box[3] - 62, 6f, 0x0AFFFFFF);

        // you, and where you are
        int[] face = SkinFaces.get(p.playerUuid());
        int hx = x + PAD;
        if (face != null) {
            d.roundRect(hx - 1, y + PAD - 1, 22, 22, 4, Theme.withAlpha(a, 0x60));
            SkinFaces.draw(d, face, hx + 1, y + PAD + 1, 18);
            hx += 26;
        } else {
            Glass.logo(d, hx, y + PAD, 2.2f, 0xFFFFFFFF);
            hx += 24;
        }
        d.text(p.playerName(), hx, y + PAD + 2, Theme.TEXT, true);
        String where = p.localWorld() ? "Singleplayer" : p.serverName() != null && !p.serverName().isEmpty()
                ? p.serverName() : p.serverAddress() == null ? "Playing" : p.serverAddress();
        d.text(d.trim(where, box[2] - (hx - x) - 64), hx, y + PAD + 12, Theme.MUTED, false);
        if (!p.localWorld() && p.ping() > 0) {  // a ping chip
            String ping = p.ping() + " ms";
            int pw = d.width(ping) + 14, px = x + box[2] - PAD - pw;
            int col = p.ping() < 80 ? Theme.GOOD : p.ping() < 160 ? Theme.OK : Theme.BAD;
            d.roundRect(px, y + PAD + 4, pw, 12, 6, Theme.withAlpha(col, 0x30));
            d.roundRect(px + 4, y + PAD + 8, 4, 4, 2, col);
            d.text(ping, px + 10, y + PAD + 6, Theme.TEXT, false);
        }

        int i = 0;
        for (TitleMenu.Button b : buttons()) {
            float ti = shownAt < 0 ? 1f : FadeDraw.progress(shownAt + 60 + i++ * 40L, 260);
            Draw bd = ti < 1f ? new FadeDraw(d, Math.max(0.02f, Math.min(1f, ti * 1.4f))) : d;
            int by = b.y + rise + Math.round((1f - Glass.backOut(ti)) * 8);
            int bx0 = b.x;
            if (dragging && b == grabbed) {   // the button you're moving follows the mouse
                b.x += dragX - grabX;
                by += dragY - grabY;
            }
            boolean hover = b.contains(mx, my - rise) || (dragging && b == grabbed);
            b.glow = Glass.ease(b.glow, hover, dt);
            if (b == resume) {
                Glass.pill(bd, b.x, by, b.w, b.h, true, b.glow, a);
                MenuIcons.draw(bd, "play", b.x + b.w / 2 - bd.width(b.label) / 2 - 8, by + 8, Theme.onColor(a));
                bd.text(b.label, b.x + (b.w - bd.width(b.label)) / 2 + 6, by + 8, Theme.onColor(a), false);
            } else if (b == leave) {
                int col = Theme.mix(Theme.withAlpha(Theme.BAD, 0x90), Theme.BAD, b.glow);
                if (b.glow > 0.01f) {
                    bd.glow(b.x + 4, by + 3, b.w - 8, b.h - 4, Theme.withAlpha(Theme.BAD, Math.round(0x40 * b.glow)));
                }
                bd.roundRect(b.x, by, b.w, b.h, 6, Theme.withAlpha(Theme.BAD, Math.round(0x18 + 0x50 * b.glow)));
                bd.roundOutline(b.x, by, b.w, b.h, 6, col);
                MenuIcons.draw(bd, "leave", b.x + b.w / 2 - bd.width(b.label) / 2 - 8, by + 5, Theme.mix(0xFFE8B4B4, 0xFFFFFFFF, b.glow));
                bd.text(b.label, b.x + (b.w - bd.width(b.label)) / 2 + 6, by + 5, Theme.mix(0xFFE8B4B4, 0xFFFFFFFF, b.glow), false);
            } else {
                int lift = Math.round(b.glow * 2);
                if (b.glow > 0.01f) {
                    bd.glow(b.x + 4, by - lift + 5, b.w - 8, b.h - 6, Theme.withAlpha(a, Math.round(0x38 * b.glow)));
                }
                Glass.pane(bd, b.x, by - lift, b.w, b.h, 6, Theme.mix(Glass.PANE, Glass.PANE_HOVER, b.glow));
                if (b.glow > 0.01f) {
                    bd.roundOutline(b.x, by - lift, b.w, b.h, 6, Theme.withAlpha(a, Math.round(0xB0 * b.glow)));
                }
                bd.roundRect(b.x + 7, by - lift + (b.h - 22) / 2, 22, 22, 11, Theme.mix(0x24FFFFFF, Theme.withAlpha(a, 0x70), b.glow));
                MenuIcons.draw(bd, b.icon, b.x + 13, by - lift + (b.h - 11) / 2, 11, Theme.mix(0xFFD3D6DE, 0xFFFFFFFF, b.glow));
                String label = b == more ? "More" : b.label;
                bd.text(bd.trim(label, b.w - 40), b.x + 35, by - lift + b.h / 2 - 7, Theme.TEXT, false);
                String sub = b == modules ? "Right Shift" : b == options ? "Video, sound..." : b == locker ? "Capes, skins" : "Stats, LAN...";
                bd.text(bd.trim(sub, b.w - 40), b.x + 35, by - lift + b.h / 2 + 3, Theme.FAINT, false);
            }
            b.x = bx0;
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return false;
        }
        layout(pc.platform());
        for (TitleMenu.Button b : buttons()) {
            if (b.contains(mx, my)) {
                grabbed = b;
                grabX = dragX = mx;
                grabY = dragY = my;
                dragging = false;
                return true;
            }
        }
        return false;
    }

    /** Dragging a button: once it's moved a few pixels it follows the mouse. */
    public boolean mouseDragged(int mx, int my) {
        if (grabbed == null) {
            return false;
        }
        dragX = mx;
        dragY = my;
        if (!dragging && Math.abs(mx - grabX) + Math.abs(my - grabY) > 5) {
            dragging = true;
        }
        return true;
    }

    /** Letting go: dropped on another button swaps them; a plain click does what the button does. */
    public boolean mouseReleased(int mx, int my) {
        TitleMenu.Button b = grabbed;
        grabbed = null;
        if (b == null) {
            return false;
        }
        if (dragging) {
            dragging = false;
            layout(pc.platform());
            for (TitleMenu.Button o : buttons()) {
                if (o != b && o.contains(mx, my)) {
                    swap(b, o);
                    break;
                }
            }
            return true;
        }
        return press(b);
    }

    private boolean press(TitleMenu.Button b) {
        Platform p = pc.platform();
        if (b.action == null) {
            p.leaveWorld();
        } else {
            if (b == locker) {
                pc.menu().choose("locker");
            } else if (b == modules) {
                pc.menu().choose("cat:all");
            }
            p.openScreen(b.action);
        }
        return true;
    }

    /** For the self-test: centre of a button, by what it opens (null: Leave). */
    int[] centerOf(Platform.Screen action) {
        layout(pc.platform());
        for (TitleMenu.Button b : buttons()) {
            if (b.action == action && b != locker) {
                return new int[] {b.x + b.w / 2, b.y + b.h / 2};
            }
        }
        return null;
    }

    /** Whether to replace Minecraft's pause menu (the "Peregrine pause menu" toggle). */
    public boolean enabled() {
        Module m = pc.module("pause_menu");
        return m == null || m.enabled();
    }
}
