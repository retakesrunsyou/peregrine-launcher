package net.peregrine.client.core;

/**
 * Peregrine's main menu, shown instead of Minecraft's title screen: the logo, your
 * name and head, a column of buttons with icons (the first in the accent color),
 * small Realms / Language / Accessibility buttons at the bottom, and a "Jump back
 * in" card for the last server you played. Version adapters draw the panorama (unless
 * drawsBackground() says a picture replaces it), then call render().
 *
 * On the right, your player (the adapter draws it at playerBox()) and the Locker button. The
 * Locker slides in over the menu: pick your Peregrine cape and the main menu's background,
 * and your player glides across to model them.
 */
public final class TitleMenu {

    public static final String CLIENT_VERSION = "1.4.0";

    static final int H = 20, GAP = 4, SMALL_H = 15;

    /** A menu button: label, icon, what it opens. Shared with the pause menu. */
    static final class Button {
        final String label, icon;
        final Platform.Screen action;
        final boolean primary;
        int x, y, w, h;
        float glow;  // hover highlight, eased in and out

        Button(String label, String icon, Platform.Screen action, boolean primary) {
            this.label = label;
            this.icon = icon;
            this.action = action;
            this.primary = primary;
        }

        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        void place(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /** Just the icon in a small square; its name shows above it while hovered. */
        void drawIcon(Draw d, int mx, int my, float dt, int accent) {
            boolean hover = contains(mx, my);
            glow = hover ? Math.min(1f, glow + dt * 9f) : Math.max(0f, glow - dt * 6f);
            d.shadow(x, y, w, h);
            d.roundRect(x, y, w, h, 3, Theme.mix(0xD81B1E25, 0xE82A2E38, glow));
            MenuIcons.draw(d, icon, x + (w - 9) / 2, y + (h - 9) / 2, Theme.mix(0xFFD3D6DE, Theme.TEXT, glow));
            if (glow > 0.5f) {
                d.text(label, x, y - 11, Theme.withAlpha(Theme.TEXT, Math.round(0xFF * glow)), true);
            }
        }

        /** Eases the hover glow, then draws the button. */
        void draw(Draw d, int mx, int my, float dt, int accent, boolean small) {
            boolean hover = contains(mx, my);
            glow = hover ? Math.min(1f, glow + dt * 9f) : Math.max(0f, glow - dt * 6f);
            float g = glow;
            int bg = primary ? Theme.mix(accent, Theme.mix(accent, 0xFFFFFFFF, 0.18f), g)
                    : Theme.mix(0xD81B1E25, 0xE82A2E38, g);
            d.shadow(x, y, w, h);
            d.roundRect(x, y, w, h, 3, bg);
            // a darker lip along the bottom, like a key you can press
            d.rect(x + 2, y + h - 2, w - 4, 1, primary ? Theme.mix(accent, 0xFF000000, 0.35f) : 0x50000000);
            if (!primary && g > 0) {  // accent bar on the left while hovered
                int bh = Math.round((h - 8) * g);
                d.rect(x + 1, y + (h - bh) / 2, 2, bh, Theme.withAlpha(accent, Math.round(0xFF * g)));
            }
            int fg = primary ? Theme.onColor(accent) : Theme.mix(0xFFD3D6DE, Theme.TEXT, g);
            int tx = x + 8;
            if (icon != null) {
                int iy = y + (h - 9) / 2;
                MenuIcons.draw(d, icon, x + 8, iy, fg);
                tx = x + 23;
            }
            String text = d.trim(label, x + w - 6 - tx);
            if (small) {
                d.text(text, tx, y + (h - d.lineHeight()) / 2 + 1, fg, false);
            } else {
                d.text(text, tx, y + (h - d.lineHeight()) / 2 + 1, fg, !primary);
            }
        }
    }

    private final Peregrine pc;
    private long shownAt = -1;
    private long lastFrame;
    private final Button[] buttons = {
        new Button("Singleplayer", "controller", Platform.Screen.SINGLEPLAYER, true),
        new Button("Multiplayer", "globe", Platform.Screen.MULTIPLAYER, false),
        new Button("Peregrine modules", "grid", Platform.Screen.PEREGRINE_MENU, false),
        new Button("Options", "sliders", Platform.Screen.OPTIONS, false),
        new Button("Quit game", "power", Platform.Screen.QUIT, false),
    };
    private final Button[] small = {
        new Button("Realms", "castle", Platform.Screen.REALMS, false),
        new Button("Language", "language", Platform.Screen.LANGUAGE, false),
        new Button("Accessibility", "person", Platform.Screen.ACCESSIBILITY, false),
    };
    private final int[] card = new int[4];  // the "Jump back in" card: x, y, w, h (w 0 = none)
    private float cardGlow;
    private int top, left;
    private boolean compact;  // small buttons as icons only (narrow screens)
    static final String COPYRIGHT = "Copyright Mojang AB. Do not distribute!";

    // ---- the right-hand panel and the Locker
    private final Button lockerButton = new Button("Locker", "shirt", null, true);
    private final Button backButton = new Button("Back", "back", null, false);
    private final int[] panel = new int[4];      // the player panel on the right: x, y, w, h (w 0 = none)
    private boolean lockerOpen;
    private float lockerT;                       // 0 = menu, 1 = Locker, eased
    private int lockerTab;                       // 0 = capes, 1 = skins, 2 = backgrounds
    static final String[] TABS = {"Capes", "Skins", "Backgrounds"};
    private final LockerSkins skins = new LockerSkins();
    private int trying = -1;                     // the skin card being tried on
    private final Button wearButton = new Button("Wear it", "check", null, true);
    private final float[] skinGlow = new float[64];
    private float tabT = 1f;
    private final float[] capeGlow = new float[8];
    private final float[] bgGlow = new float[8];
    private final float[] tabGlow = new float[3];
    /** Locker cards laid out this frame: {kind (0 cape, 1 background, 2 tab), index, x, y, w, h}. */
    private final java.util.List<int[]> cards = new java.util.ArrayList<int[]>();
    private int[] lockerPlayer = new int[4];
    private final PlayerPreview preview = new PlayerPreview();
    private boolean pressedPlayer;
    private int pressX;

    // ---- backgrounds
    private String customId;
    private long customStamp = -1;
    private int customW = 16, customH = 9;
    private long customCheckedAt;

    static String versionLine(Platform p) {
        return "Peregrine " + CLIENT_VERSION + "  \u00B7  Minecraft " + p.minecraftVersion();
    }

    TitleMenu(Peregrine pc) {
        this.pc = pc;
    }

    private void layout(Platform p, Draw d) {
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        int w = Math.max(150, Math.min(190, sw * 2 / 5));
        left = Math.max(14, Math.round(sw * 0.07f));
        int total = 46 + buttons.length * (H + GAP);
        top = Math.max(8, (sh - total) / 2 - 8);
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].place(left, top + 46 + i * (H + GAP), w, H);
        }
        if (d != null) {  // (a click keeps where they were drawn)
            // labels if they fit next to the version and copyright lines, icons alone if not
            int full = 0;
            for (Button b : small) {
                full += 23 + d.width(b.label) + 8 + 5;
            }
            int text = Math.max(d.width(versionLine(p)), d.width(COPYRIGHT));
            compact = left + full + 8 > sw - 6 - text;
            int sx = left;
            for (Button b : small) {
                int bw = compact ? 21 : 23 + d.width(b.label) + 8;
                b.place(sx, sh - SMALL_H - 8, bw, SMALL_H);
                sx += bw + 5;
            }
        }
        net.peregrine.client.core.modules.MainMenu mm = mainMenu();
        if (mm == null || mm.player.value) {
            int pw = Math.max(92, Math.min(124, sw / 5));
            int ph = Math.min(pw * 2 + 30, sh - 110);
            panel[0] = sw - pw - 18;
            panel[1] = Math.max(14, (sh - ph) / 2 - 22);
            panel[2] = pw;
            panel[3] = ph;
            lockerButton.place(panel[0] + 8, panel[1] + ph - 26, pw - 16, H - 2);
            if (ph < 110 || panel[0] < left + w + 24) {
                panel[2] = 0;  // no room: just the menu
            }
        } else {
            panel[2] = 0;
        }
        String last = pc.lastServerName();
        if (last != null && p.supportsJoin()) {
            int cw = Math.min(170, Math.max(130, sw / 3));
            card[0] = sw - cw - 10;
            card[1] = sh - 36 - 22;
            card[2] = cw;
            card[3] = 36;
        } else {
            card[2] = 0;
        }
    }

    /** The game window's title: "Peregrine Client 1.2.0 - Minecraft 1.21.1 - Singleplayer". */
    public static String windowTitle(String vanilla) {
        String rest = vanilla == null ? "Minecraft" : vanilla.replaceFirst("^Minecraft\\*", "Minecraft");
        return "Peregrine Client " + CLIENT_VERSION + " - " + rest;
    }

    /** Call when the main menu screen is created, so it fades in. */
    public void shown() {
        shownAt = System.currentTimeMillis();
    }

    public void render(Draw raw, int mx, int my) {
        SelfTest.count("title");
        float t = shownAt < 0 ? 1f : FadeDraw.progress(shownAt, 400);
        Draw base = t < 1f ? new FadeDraw(raw, t) : raw;
        Platform p = pc.platform();
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        int a = pc.accent();
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float target = lockerOpen ? 1f : 0f;
        lockerT += (target - lockerT) * Math.min(1f, dt * 9f);
        if (Math.abs(target - lockerT) < 0.002f) {
            lockerT = target;
        }
        tabT = Math.min(1f, tabT + dt * 6f);
        float e = lockerT * lockerT * (3 - 2 * lockerT);  // smoothstep

        drawBackground(base, sw, sh, now);

        // Darken behind the buttons: one smooth fade from the left (no visible steps).
        base.rect(0, 0, sw, sh, Theme.withAlpha(0, 0x38 + Math.round(0x40 * e)));
        int fadeW = Math.max(1, sw * 11 / 20);
        for (int x = 0; x < fadeW; x += 2) {
            float k = 1f - x / (float) fadeW;
            int alpha = Math.round(0x70 * k * k * (1f - e));
            if (alpha > 0) {
                base.rect(x, 0, 2, sh, Theme.withAlpha(0, alpha));
            }
        }

        layout(p, base);
        layoutLocker(p, base, sw, sh);

        // the menu slides left and fades as the Locker comes in
        if (e < 0.999f) {
            int off = Math.round(-e * sw * 0.25f);
            Draw d = e > 0.001f ? new FadeDraw(base, 1f - e) : base;
            d.pushScale(off, 0, 1f);
            drawHome(d, p, mx - off, my, dt, a, sw, sh);
            d.popScale();
        }
        if (e > 0.001f) {
            int off = Math.round((1f - e) * sw * 0.2f);
            Draw d = e < 0.999f ? new FadeDraw(base, e) : base;
            d.pushScale(off, 0, 1f);
            drawLocker(d, p, mx - off, my, dt, a, sw, sh);
            drawLockerExtras(d, p, mx - off, my, dt, a);
            d.popScale();
        }

        int[] box = playerBox();
        if (box != null) {
            preview.draw(base, p, box[0], box[1], box[2], box[3], net.peregrine.client.core.modules.Cosmetics.capeIndex());
        }

        if (e < 0.98f) {
            Draw vd = e > 0.01f ? new FadeDraw(base, 1f - e) : base;
            String ver = versionLine(p);
            vd.text(ver, sw - 6 - vd.width(ver), sh - 20, Theme.FAINT, true);
            vd.text(COPYRIGHT, sw - 6 - vd.width(COPYRIGHT), sh - 10, Theme.FAINT, true);
        }
    }

    private void drawHome(Draw d, Platform p, int mx, int my, float dt, int a, int sw, int sh) {
        // Header: the falcon (sharp at any size), "Peregrine", your head and name.
        int hy = top;
        d.textScaled(Hooks.BIG_LOGO, left, hy, 0xFFFFFFFF, 4f, false);
        d.textScaled("Peregrine", left + 38, hy + 1, Theme.TEXT, 2f, true);
        int[] face = SkinFaces.get(p.playerUuid());
        int ny = hy + 21;
        int nx = left + 38;
        if (face != null) {
            SkinFaces.draw(d, face, nx, ny, 8);
            nx += 11;
        }
        d.text(p.playerName(), nx, ny, Theme.MUTED, true);

        for (Button b : buttons) {
            b.draw(d, mx, my, dt, a, false);
        }
        for (Button b : small) {
            if (compact) {
                b.drawIcon(d, mx, my, dt, a);
            } else {
                b.draw(d, mx, my, dt, a, true);
            }
        }
        if (card[2] > 0) {
            drawCard(d, p, mx, my, dt, a);
        }
        if (panel[2] > 0) {
            int x = panel[0], y = panel[1], w = panel[2], h = panel[3];
            d.shadow(x, y, w, h);
            d.roundRect(x, y, w, h, 4, 0x9C15171D);
            d.roundOutline(x, y, w, h, 4, 0x22FFFFFF);
            // a soft spotlight behind the player
            for (int i = 0; i < 6; i++) {
                int r = (6 - i) * 7;
                d.roundRect(x + w / 2 - r, y + h / 2 - r - 8, r * 2, r * 2, r, Theme.withAlpha(a, 0x07));
            }
            String cape = net.peregrine.client.core.modules.Cosmetics.CAPES[
                    net.peregrine.client.core.modules.Cosmetics.capeIndex()];
            String line = "Cape: " + cape;
            d.text(d.trim(line, w - 8), x + (w - Math.min(w - 8, d.width(line))) / 2, y + h - 38, Theme.MUTED, false);
            lockerButton.draw(d, mx, my, dt, a, false);
        }
    }

    // ------------------------------------------------------------------ the Locker

    private void layoutLocker(Platform p, Draw d, int sw, int sh) {
        cards.clear();
        int lx = left;
        backButton.place(lx, 12, 60, 18);
        int pw = Math.max(90, Math.min(130, sw / 5));
        int ph = Math.min(sh - 96, pw * 2);
        lockerPlayer = new int[] {lx, 58, pw, ph};
        int gx = lx + pw + 24;
        int gw = sw - gx - 14;
        // tabs
        int tx = gx;
        String[] tabs = TABS;
        for (int i = 0; i < tabs.length; i++) {
            int tw = 16 + (d == null ? tabs[i].length() * 6 : d.width(tabs[i]));
            cards.add(new int[] {2, i, tx, 36, tw, 16});
            tx += tw + 6;
        }
        int y0 = 62;
        int gh = sh - y0 - 8;
        if (lockerTab == 0) {
            grid(0, net.peregrine.client.core.modules.Cosmetics.CAPES.length, 58, 82, 0, gx, y0, gw, gh);
        } else if (lockerTab == 1) {
            grid(3, Math.min(skins.list(p).size(), skinGlow.length), 58, 92, 0, gx, y0, gw, gh);
        } else {
            grid(1, net.peregrine.client.core.modules.MainMenu.BACKGROUNDS.length, 150, 0, 14, gx, y0, gw, gh);
        }
    }

    /**
     * Lays n cards out in the space given, as big as fits (up to w0 x h0; h0 0 = a 16:9 picture
     * plus extra for the label).
     */
    private void grid(int kind, int n, int w0, int h0, int extra, int gx, int gy, int gw, int gh) {
        int gap = 8;
        if (n == 0) {
            return;
        }
        float best = 0;
        int bestCols = 1;
        for (int cols = 1; cols <= n; cols++) {
            int rows = (n + cols - 1) / cols;
            float fw = (gw - gap * (cols - 1)) / (float) cols / w0;
            int hh = h0 > 0 ? h0 : w0 * 9 / 16 + extra;
            float fh = (gh - gap * (rows - 1)) / (float) rows / hh;
            float f = Math.min(1f, Math.min(fw, fh));
            if (f > best + 0.001f) {
                best = f;
                bestCols = cols;
            }
        }
        int cw = Math.max(30, Math.round(w0 * best));
        int ch = h0 > 0 ? Math.max(40, Math.round(h0 * best)) : cw * 9 / 16 + extra;
        for (int i = 0; i < n; i++) {
            cards.add(new int[] {kind, i, gx + (i % bestCols) * (cw + gap), gy + (i / bestCols) * (ch + gap), cw, ch});
        }
    }

    private void drawLocker(Draw d, Platform p, int mx, int my, float dt, int a, int sw, int sh) {
        int[] lpw = lockerPlayer;
        wearButton.place(lpw[0], lpw[1] + lpw[3] + 18, lpw[2], H - 2);
        backButton.draw(d, mx, my, dt, a, true);
        d.textScaled("Locker", left + 70, 13, Theme.TEXT, 1.6f, true);
        int[] lp = lockerPlayer;
        d.shadow(lp[0], lp[1], lp[2], lp[3]);
        d.roundRect(lp[0], lp[1], lp[2], lp[3], 4, 0x9C15171D);
        d.roundOutline(lp[0], lp[1], lp[2], lp[3], 4, 0x22FFFFFF);
        String hint = "Drag to turn";
        d.text(hint, lp[0] + (lp[2] - d.width(hint)) / 2, lp[1] + lp[3] + 6, Theme.FAINT, false);

        net.peregrine.client.core.modules.MainMenu menuMod = mainMenu();
        int worn = net.peregrine.client.core.modules.Cosmetics.capeIndex();
        int bg = menuMod == null ? 0 : menuMod.background.index;
        float fade = tabT;
        for (int[] c : cards) {
            int kind = c[0], i = c[1], x = c[2], y = c[3], w = c[4], h = c[5];
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
            if (kind == 2) {
                tabGlow[i] = ease(tabGlow[i], hover || lockerTab == i, dt);
                boolean on = lockerTab == i;
                d.roundRect(x, y, w, h, 3, on ? Theme.mix(a, 0xFFFFFFFF, 0.1f * tabGlow[i]) : Theme.mix(0xC01B1E25, 0xE02A2E38, tabGlow[i]));
                String label = TABS[i];
                d.text(label, x + 8, y + 4, on ? Theme.onColor(a) : Theme.mix(Theme.MUTED, Theme.TEXT, tabGlow[i]), false);
                continue;
            }
            Draw cd = fade < 1f ? new FadeDraw(d, fade) : d;
            int dy = Math.round((1f - fade) * 8);
            if (kind == 3) {
                java.util.List<LockerSkins.Skin> list = skins.list(p);
                if (i >= list.size()) {
                    continue;
                }
                LockerSkins.Skin sk = list.get(i);
                skinGlow[i] = ease(skinGlow[i], hover, dt);
                float g = skinGlow[i];
                int cy = y + dy - Math.round(g * 2);
                cd.shadow(x, cy, w, h);
                cd.roundRect(x, cy, w, h, 4, Theme.mix(0xC01B1E25, 0xE02A2E38, g));
                if (trying == i) {
                    cd.roundOutline(x, cy, w, h, 4, a);
                } else if (g > 0) {
                    cd.roundOutline(x, cy, w, h, 4, Theme.withAlpha(a, Math.round(0x90 * g)));
                }
                String tex = skins.texture(p, sk);
                if (tex != null) {
                    PlayerPreview.drawFront(cd, tex, sk.slim, sk.legacy, x + 4, cy + 5, w - 8, h - 32);
                }
                String n = cd.trim(sk.name, w - 6);
                cd.text(n, x + (w - cd.width(n)) / 2, cy + h - 22, Theme.TEXT, false);
                String sub = sk.slim ? "Slim" : "Classic";
                cd.text(sub, x + (w - cd.width(sub)) / 2, cy + h - 11, Theme.FAINT, false);
                continue;
            }
            float[] glow = kind == 0 ? capeGlow : bgGlow;
            boolean picked = kind == 0 ? i == worn : i == bg;
            glow[i] = ease(glow[i], hover, dt);
            float g = glow[i];
            int lift = Math.round(g * 2);
            int cy = y + dy - lift;
            cd.shadow(x, cy, w, h);
            cd.roundRect(x, cy, w, h, 4, Theme.mix(0xC01B1E25, 0xE02A2E38, g));
            if (picked) {
                cd.roundOutline(x, cy, w, h, 4, a);
                cd.roundOutline(x + 1, cy + 1, w - 2, h - 2, 3, Theme.withAlpha(a, 0x70));
            } else if (g > 0) {
                cd.roundOutline(x, cy, w, h, 4, Theme.withAlpha(a, Math.round(0x90 * g)));
            }
            if (kind == 0) {
                String file = net.peregrine.client.core.modules.Cosmetics.FILES[i];
                int ph = Math.max(16, h - 44), pw = ph * 10 / 16;
                int px = x + (w - pw) / 2, py = cy + 8;
                if (file != null) {
                    // the outside of the cape: 10x16 pixels at (1, 1) of its 64x32 layout
                    cd.texture("peregrine-client:textures/cape/" + file + ".png", px, py, pw, ph,
                            1f / 64, 1f / 32, 11f / 64, 17f / 32, 0xFFFFFFFF);
                } else {
                    cd.roundOutline(px, py, pw, ph, 2, 0x40FFFFFF);
                    cd.text("-", px + pw / 2 - 2, py + ph / 2 - 4, Theme.FAINT, false);
                }
                String name = net.peregrine.client.core.modules.Cosmetics.CAPES[i];
                String n = cd.trim(name, w - 6);
                cd.text(n, x + (w - cd.width(n)) / 2, cy + h - 22, Theme.TEXT, false);
                String sub = picked ? "Wearing" : "Peregrine";
                cd.text(sub, x + (w - cd.width(sub)) / 2, cy + h - 11, picked ? a : Theme.FAINT, false);
            } else {
                int iw = w - 8, ih = iw * 9 / 16;
                int ix = x + 4, iy = cy + 4;
                String id = backgroundTexture(i);
                if (id != null) {
                    float[] uv = cover(i == net.peregrine.client.core.modules.MainMenu.CUSTOM ? customW : 16,
                            i == net.peregrine.client.core.modules.MainMenu.CUSTOM ? customH : 9, iw, ih);
                    cd.texture(id, ix, iy, iw, ih, uv[0], uv[1], uv[2], uv[3], 0xFFFFFFFF);
                } else if (i == 0) {
                    cd.rect(ix, iy, iw, ih, 0xFF2B3A55);
                    cd.rect(ix, iy + ih * 2 / 3, iw, ih / 3, 0xFF3D5A3A);
                    MenuIcons.draw(cd, "globe", ix + iw / 2 - 4, iy + ih / 2 - 6, Theme.TEXT);
                } else {
                    cd.roundOutline(ix, iy, iw, ih, 2, 0x40FFFFFF);
                    MenuIcons.draw(cd, "image", ix + iw / 2 - 4, iy + ih / 2 - 8, Theme.MUTED);
                    String tip = "Pick one in the launcher";
                    cd.text(cd.trim(tip, iw - 4), ix + (iw - Math.min(iw - 4, cd.width(tip))) / 2, iy + ih / 2 + 4, Theme.FAINT, false);
                }
                String name = net.peregrine.client.core.modules.MainMenu.BACKGROUNDS[i];
                cd.text(cd.trim(name, w - 20), x + 5, cy + h - 11, Theme.TEXT, false);
                if (picked) {
                    MenuIcons.draw(cd, "check", x + w - 13, cy + h - 12, a);
                }
            }
        }
    }

    private void drawLockerExtras(Draw d, Platform p, int mx, int my, float dt, int a) {
        if (lockerTab == 1 && skins.list(p).isEmpty()) {
            int gx = lockerPlayer[0] + lockerPlayer[2] + 24;
            d.text("No skins yet.", gx, 66, Theme.TEXT, true);
            d.text("Add some in the launcher's Locker (Skins tab),", gx, 80, Theme.MUTED, false);
            d.text("then wear them right here.", gx, 91, Theme.MUTED, false);
        }
        if (trying >= 0) {
            wearButton.draw(d, mx, my, dt, a, false);
        }
        String st = skins.status;
        if (st != null && !st.isEmpty()) {
            int[] lp = lockerPlayer;
            String t = d.trim(st, Math.max(lp[2] + 120, 200));
            d.text(t, lp[0], lp[1] + lp[3] + 42, skins.busy ? Theme.MUTED : Theme.TEXT, false);
        }
    }

    private static float ease(float v, boolean on, float dt) {
        return on ? Math.min(1f, v + dt * 9f) : Math.max(0f, v - dt * 6f);
    }

    private net.peregrine.client.core.modules.MainMenu mainMenu() {
        Module m = pc.module("main_menu");
        return m instanceof net.peregrine.client.core.modules.MainMenu ? (net.peregrine.client.core.modules.MainMenu) m : null;
    }

    /** Where the adapter should draw your player this frame (screen GUI pixels), or null. It glides
     *  from the menu's panel to the Locker as that opens. */
    public int[] playerBox() {
        float e = lockerT * lockerT * (3 - 2 * lockerT);
        int[] from = panel[2] > 0 ? new int[] {panel[0] + 8, panel[1] + 8, panel[2] - 16, panel[3] - 52} : null;
        int[] to = new int[] {lockerPlayer[0] + 6, lockerPlayer[1] + 6, lockerPlayer[2] - 12, lockerPlayer[3] - 12};
        if (from == null) {
            return e > 0.5f ? to : null;
        }
        int[] r = new int[4];
        for (int i = 0; i < 4; i++) {
            r[i] = Math.round(from[i] + (to[i] - from[i]) * e);
        }
        return r;
    }

    /** For the self-test: which skin card is being tried on (-1 none). */
    public int tryingSkin() {
        return trying;
    }

    public boolean lockerOpen() {
        return lockerOpen;
    }

    /** Turning your player by dragging it. */
    public boolean mouseDragged(int mx, int my) {
        if (pressedPlayer) {
            preview.drag(mx);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int mx, int my) {
        if (!pressedPlayer) {
            return false;
        }
        pressedPlayer = false;
        preview.release();
        if (Math.abs(mx - pressX) < 3) {
            preview.spin();  // a click: spin round
        }
        return true;
    }

    /** Esc in the Locker goes back to the menu. */
    public boolean keyPressed(int key) {
        if (key == Menu.KEY_ESCAPE && lockerOpen) {
            lockerOpen = false;
            trying = -1;
            preview.stopTrying();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ backgrounds

    /** True when a picture replaces Minecraft's panorama (so the adapter doesn't draw it). */
    public boolean drawsBackground() {
        net.peregrine.client.core.modules.MainMenu m = mainMenu();
        return m != null && m.background.index != 0 && backgroundTexture(m.background.index) != null;
    }

    private String backgroundTexture(int index) {
        if (index > 0 && index < net.peregrine.client.core.modules.MainMenu.FILES.length
                && net.peregrine.client.core.modules.MainMenu.FILES[index] != null) {
            return "peregrine-client:textures/background/" + net.peregrine.client.core.modules.MainMenu.FILES[index] + ".png";
        }
        if (index == net.peregrine.client.core.modules.MainMenu.CUSTOM) {
            return customBackground();
        }
        return null;
    }

    /** Your own picture: client/backgrounds/custom.png in Peregrine's data folder (the launcher puts it there). */
    public static java.nio.file.Path customBackgroundFile() {
        String data = System.getProperty("peregrine.data");
        java.nio.file.Path base = data != null && !data.isEmpty() ? java.nio.file.Paths.get(data, "client")
                : Peregrine.get().platform().configFile().getParent();
        return base.resolve("backgrounds").resolve("custom.png");
    }

    private String customBackground() {
        long now = System.currentTimeMillis();
        if (now - customCheckedAt < 2000 && customStamp != -1) {
            return customId;
        }
        customCheckedAt = now;
        java.nio.file.Path f = customBackgroundFile();
        long stamp;
        try {
            stamp = java.nio.file.Files.isRegularFile(f) ? java.nio.file.Files.getLastModifiedTime(f).toMillis() : 0;
        } catch (java.io.IOException e) {
            stamp = 0;
        }
        if (stamp != customStamp) {
            customStamp = stamp;
            customId = null;
            if (stamp != 0) {
                int[] size = pngSize(f);
                if (size != null) {
                    customW = size[0];
                    customH = size[1];
                    customId = pc.platform().loadImage(f);
                }
            }
        }
        return customId;
    }

    static int[] pngSize(java.nio.file.Path f) {
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(f)) {
            byte[] b = new byte[24];
            int n = 0;
            while (n < 24) {
                int r = in.read(b, n, 24 - n);
                if (r < 0) {
                    return null;
                }
                n += r;
            }
            if ((b[0] & 0xFF) != 0x89 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') {
                return null;
            }
            int w = ((b[16] & 0xFF) << 24) | ((b[17] & 0xFF) << 16) | ((b[18] & 0xFF) << 8) | (b[19] & 0xFF);
            int h = ((b[20] & 0xFF) << 24) | ((b[21] & 0xFF) << 16) | ((b[22] & 0xFF) << 8) | (b[23] & 0xFF);
            return w > 0 && h > 0 ? new int[] {w, h} : null;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** The part of a tw x th picture that fills a w x h box without stretching: {u0, v0, u1, v1}. */
    static float[] cover(int tw, int th, int w, int h) {
        float ta = tw / (float) th, ba = w / (float) Math.max(1, h);
        if (ba > ta) {
            float vh = ta / ba;
            return new float[] {0f, (1f - vh) / 2, 1f, (1f + vh) / 2};
        }
        float uw = ba / ta;
        return new float[] {(1f - uw) / 2, 0f, (1f + uw) / 2, 1f};
    }

    private void drawBackground(Draw d, int sw, int sh, long now) {
        net.peregrine.client.core.modules.MainMenu m = mainMenu();
        if (m == null || m.background.index == 0) {
            return;
        }
        String id = backgroundTexture(m.background.index);
        if (id == null) {
            return;
        }
        boolean custom = m.background.index == net.peregrine.client.core.modules.MainMenu.CUSTOM;
        float[] uv = cover(custom ? customW : 16, custom ? customH : 9, sw, sh);
        // a slow drift so the picture feels alive (like the panorama turning)
        float zoom = 0.04f;
        float t = (now % 120000L) / 120000f * (float) (Math.PI * 2);
        float du = (uv[2] - uv[0]) * zoom, dv = (uv[3] - uv[1]) * zoom;
        float ox = (float) Math.sin(t) * du / 2, oy = (float) Math.cos(t * 0.7f) * dv / 2;
        d.texture(id, 0, 0, sw, sh, uv[0] + du / 2 + ox, uv[1] + dv / 2 + oy, uv[2] - du / 2 + ox, uv[3] - dv / 2 + oy,
                0xFFFFFFFF);
    }

    private void drawCard(Draw d, Platform p, int mx, int my, float dt, int a) {
        int x = card[0], y = card[1], w = card[2], h = card[3];
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        cardGlow = hover ? Math.min(1f, cardGlow + dt * 9f) : Math.max(0f, cardGlow - dt * 6f);
        d.shadow(x, y, w, h);
        d.roundRect(x, y, w, h, 3, Theme.mix(0xD81B1E25, 0xE82A2E38, cardGlow));
        if (cardGlow > 0) {
            d.roundOutline(x, y, w, h, 3, Theme.withAlpha(a, Math.round(0xC0 * cardGlow)));
        }
        int[] icon = ServerIcons.get(pc.lastServerAddress(), null);
        int is = 24, ix = x + 6, iy = y + (h - is) / 2;
        if (icon != null) {
            d.pushScale(ix, iy, is / (float) ServerIcons.CELLS);
            for (int i = 0; i < icon.length; i++) {
                if ((icon[i] >>> 24) != 0) {
                    d.rect(i % ServerIcons.CELLS, i / ServerIcons.CELLS, 1, 1, icon[i]);
                }
            }
            d.popScale();
        } else {
            d.roundRect(ix, iy, is, is, 3, Theme.withAlpha(a, 0x60));
            MenuIcons.draw(d, "globe", ix + 7, iy + 7, Theme.TEXT);
        }
        int tx = ix + is + 7;
        d.text("Jump back in", tx, y + 7, Theme.MUTED, false);
        d.text(d.trim(pc.lastServerName(), x + w - 16 - tx), tx, y + 19, Theme.TEXT, true);
        MenuIcons.draw(d, "chevron", x + w - 14 + Math.round(cardGlow * 2), y + (h - 9) / 2, Theme.mix(Theme.MUTED, Theme.TEXT, cardGlow));
    }

    public boolean mouseClicked(int mx, int my, int button) {
        SelfTest.count("titleclick@" + mx + "," + my + "/" + button);
        if (button != 0) {
            return false;
        }
        Platform p = pc.platform();
        layout(p, null);
        int[] box = playerBox();
        if (box != null && mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3]) {
            preview.press(mx);
            pressedPlayer = true;
            pressX = mx;
            return true;
        }
        if (lockerOpen) {
            layoutLocker(p, null, p.screenWidth(), p.screenHeight());
            if (backButton.contains(mx, my)) {
                lockerOpen = false;
                trying = -1;
                preview.stopTrying();
                return true;
            }
            if (trying >= 0 && wearButton.contains(mx, my)) {
                java.util.List<LockerSkins.Skin> list = skins.list(p);
                if (trying < list.size()) {
                    skins.wear(p, list.get(trying), new Runnable() {
                        public void run() {
                            trying = -1;
                            preview.stopTrying();
                            preview.reload();
                        }
                    });
                }
                return true;
            }
            for (int[] c : cards) {
                if (mx >= c[2] && mx < c[2] + c[4] && my >= c[3] && my < c[3] + c[5]) {
                    if (c[0] == 2) {
                        if (lockerTab != c[1]) {
                            lockerTab = c[1];
                            tabT = 0f;
                        }
                    } else if (c[0] == 3) {
                        java.util.List<LockerSkins.Skin> list = skins.list(p);
                        if (trying == c[1] || c[1] >= list.size()) {
                            trying = -1;
                            preview.stopTrying();
                        } else {
                            LockerSkins.Skin sk = list.get(c[1]);
                            String tex = skins.texture(p, sk);
                            if (tex != null) {
                                trying = c[1];
                                preview.tryOn(tex, sk.slim, sk.legacy);
                                skins.status = "Trying on " + sk.name;
                            }
                        }
                    } else if (c[0] == 0) {
                        net.peregrine.client.core.modules.Cosmetics.setCape(c[1]);
                        if (c[1] > 0) {
                            preview.showBack();
                        }
                    } else {
                        net.peregrine.client.core.modules.MainMenu m = mainMenu();
                        if (m != null) {
                            m.background.index = c[1];
                            pc.markDirty();
                        }
                    }
                    return true;
                }
            }
            return false;
        }
        if (panel[2] > 0 && lockerButton.contains(mx, my)) {
            lockerOpen = true;
            return true;
        }
        for (Button b : buttons) {
            if (b.contains(mx, my)) {
                p.openScreen(b.action);
                return true;
            }
        }
        for (Button b : small) {
            if (b.contains(mx, my)) {
                p.openScreen(b.action);
                return true;
            }
        }
        if (card[2] > 0 && mx >= card[0] && mx < card[0] + card[2] && my >= card[1] && my < card[1] + card[3]) {
            p.joinServer(pc.lastServerAddress(), pc.lastServerName());
            return true;
        }
        return false;
    }

    /** For the self-test: centre of the Locker button (or the back button while the Locker is open). */
    public int[] lockerButtonCenter() {
        layout(pc.platform(), null);
        Button b = lockerOpen ? backButton : lockerButton;
        return panel[2] > 0 || lockerOpen ? new int[] {b.x + b.w / 2, b.y + b.h / 2} : null;
    }

    /** For the self-test: centre of a Locker card (kind 0 cape, 1 background, 2 tab). */
    public int[] lockerCardCenter(int kind, int index) {
        Platform p = pc.platform();
        layout(p, null);
        layoutLocker(p, null, p.screenWidth(), p.screenHeight());
        for (int[] c : cards) {
            if (c[0] == kind && c[1] == index) {
                return new int[] {c[2] + c[4] / 2, c[3] + c[5] / 2};
            }
        }
        return null;
    }

    /** For the self-test: centre of the button that opens that screen, in GUI pixels. */
    public int[] centerOf(Platform.Screen action) {
        layout(pc.platform(), null);
        for (Button[] row : new Button[][] {buttons, small}) {
            for (Button b : row) {
                if (b.action == action) {
                    return new int[] {b.x + b.w / 2, b.y + b.h / 2};
                }
            }
        }
        return null;
    }

    /** Whether to replace Minecraft's title screen (the "Peregrine main menu" toggle). */
    public boolean enabled() {
        Module m = pc.module("main_menu");
        return m == null || m.enabled();
    }
}
