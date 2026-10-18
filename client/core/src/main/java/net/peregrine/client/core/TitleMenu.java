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

    public static final String CLIENT_VERSION = "1.5.0";

    static final int H = 20, GAP = 4, SMALL_H = 15;

    /** A menu button: label, icon, what it opens. Shared with the pause menu. */
    static final class Button {
        String label;
        final String icon;
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
    final BackgroundEditor editor = new BackgroundEditor(this);
    private float adjustGlow;
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

    // ---- the home layout: the falcon in the middle, three big tiles, a row of round buttons
    static final int TILE_H = 58, ROUND = 20;
    private int centerX, logoY, dockY, roundY, tileW;
    private final float[] tileLift = new float[3];

    private void layout(Platform p, Draw d) {
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        net.peregrine.client.core.modules.MainMenu mm = mainMenu();
        // the player and the Locker on the right, if there's room
        int pw = Math.max(92, Math.min(124, sw / 5));
        int ph = Math.min(pw * 2 + 30, sh - 110);
        boolean player = (mm == null || mm.player.value) && ph >= 110 && sw >= 400;
        int room = player ? sw - pw - 18 - 10 : sw;
        centerX = room / 2 + (player ? 4 : 0);
        tileW = Math.max(64, Math.min(104, (Math.min(room, 420) - 28 - 16) / 3));
        int total = 74 + 14 + TILE_H + 12 + ROUND;
        logoY = Math.max(6, (sh - total) / 2 - 10);
        dockY = logoY + 74 + 14;
        roundY = dockY + TILE_H + 12;
        left = Math.max(8, centerX - (tileW * 3 + 16) / 2);
        top = logoY;
        for (int i = 0; i < 3; i++) {
            buttons[i].place(left + i * (tileW + 8), dockY, tileW, TILE_H);
        }
        Button[] round = roundButtons();
        int rw = round.length * ROUND + (round.length - 1) * 8;
        int rx = centerX - rw / 2;
        for (Button b : round) {
            b.place(rx, roundY, ROUND, ROUND);
            rx += ROUND + 8;
        }
        compact = true;
        if (player) {
            panel[0] = sw - pw - 18;
            panel[1] = Math.max(14, (sh - ph) / 2 - 22);
            panel[2] = pw;
            panel[3] = ph;
            lockerButton.place(panel[0] + 8, panel[1] + ph - 26, pw - 16, H - 2);
        } else {
            panel[2] = 0;
        }
        String last = pc.lastServerName();
        if (last != null && p.supportsJoin()) {
            int cw = Math.min(170, Math.max(130, sw / 3));
            card[0] = 8;
            card[1] = sh - 36 - 8;
            card[2] = cw;
            card[3] = 36;
        } else {
            card[2] = 0;
        }
    }

    private static int lockerLeft(int sw) {
        return Math.max(14, Math.round(sw * 0.06f));
    }

    /** The round buttons under the tiles: Options, Realms, Language, Accessibility, Quit. */
    private Button[] roundButtons() {
        return new Button[] {buttons[3], small[0], small[1], small[2], buttons[4]};
    }

    /** The game window's title: "Peregrine Client 1.2.0 - Minecraft 1.21.1 - Singleplayer". */
    public static String windowTitle(String vanilla) {
        String rest = vanilla == null ? "Minecraft" : vanilla.replaceFirst("^Minecraft\\*", "Minecraft");
        return "Peregrine Client " + CLIENT_VERSION + " - " + rest;
    }

    /** Call when the main menu screen is created, so it fades in. */
    public void shown() {
        shownAt = System.currentTimeMillis();
        UpdateNotice.atTitle();
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
        tabT = Math.min(1f, tabT + dt * 2.2f);
        float e = lockerT * lockerT * (3 - 2 * lockerT);  // smoothstep
        net.peregrine.client.core.modules.MainMenu mm = mainMenu();
        editor.update(mm, dt);
        float ed = editor.openness();  // the picture editor hides the menu while it's open

        drawBackground(base, sw, sh, now);

        // A vignette: the world darkens towards the edges and the bottom, so the middle glows
        float shade = 1f - ed;
        if (shade > 0.001f) {
            base.rect(0, 0, sw, sh, Theme.withAlpha(0, Math.round((0x28 + 0x40 * e) * shade)));
            for (int y = 0; y < sh; y += 2) {
                float v = y / (float) sh;
                float k = v > 0.55f ? (v - 0.55f) / 0.45f : v < 0.18f ? (0.18f - v) / 0.18f * 0.6f : 0f;
                int alpha = Math.round(0x80 * k * k * (1f - e) * shade);
                if (alpha > 0) {
                    base.rect(0, y, sw, 2, Theme.withAlpha(0, alpha));
                }
            }
            int edge = Math.max(1, sw / 6);
            for (int x = 0; x < edge; x += 2) {
                float k = 1f - x / (float) edge;
                int alpha = Math.round(0x50 * k * k * (1f - e) * shade);
                if (alpha > 0) {
                    base.rect(x, 0, 2, sh, Theme.withAlpha(0, alpha));
                    base.rect(sw - x - 2, 0, 2, sh, Theme.withAlpha(0, alpha));
                }
            }
        }
        Draw editorLayer = base;
        if (ed > 0.999f) {
            editor.render(editorLayer, p, mm, mx, my, dt, a);
            UpdateNotice.render(raw, p, mx, my, a);
            return;
        }
        if (ed > 0.001f) {
            base = new FadeDraw(base, 1f - ed);
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
            String up = UpdateNotice.available();
            if (up != null) {  // a quiet reminder under the card once it has slid away
                String note = "Update " + up + " ready \u00B7 restart the launcher";
                vd.text(note, sw - 6 - vd.width(note), sh - 30, Theme.withAlpha(a, 0xD0), true);
            }
        }
        editor.render(editorLayer, p, mm, mx, my, dt, a);
        UpdateNotice.render(raw, p, mx, my, a);
    }

    private void drawHome(Draw d, Platform p, int mx, int my, float dt, int a, int sw, int sh) {
        long now = System.currentTimeMillis();
        float in = shownAt < 0 ? 1f : FadeDraw.progress(shownAt, 700);

        // The falcon, dropping in, with a slow breathing glow behind it
        float drop = Glass.backOut(Math.min(1f, in * 1.2f));
        int ly = logoY + Math.round((1f - drop) * -16);
        float breathe = 0.5f + 0.5f * (float) Math.sin(now / 900.0);
        for (int i = 0; i < 7; i++) {
            int r = 12 + i * 5 + Math.round(breathe * 3);
            d.roundRect(centerX - r, ly + 20 - r, r * 2, r * 2, r, Theme.withAlpha(a, Math.round((0x0C - i) * (0.6f + 0.4f * breathe))));
        }
        d.textScaled(Hooks.BIG_LOGO, centerX - 20, ly, 0xFFFFFFFF, 4.4f, false);
        String word = "PEREGRINE";
        float ws = 2f;
        int ww = Math.round(d.width(word) * ws);
        d.textScaled(word, centerX - ww / 2, ly + 44, Theme.TEXT, ws, true);
        // welcome back, with your head
        String hi = "Welcome back, " + p.playerName();
        int[] face = SkinFaces.get(p.playerUuid());
        int hw = d.width(hi) + (face != null ? 11 : 0);
        int hx = centerX - hw / 2;
        if (face != null) {
            SkinFaces.draw(d, face, hx, ly + 63, 8);
            hx += 11;
        }
        d.text(hi, hx, ly + 63, Theme.MUTED, true);

        // The three tiles rise in one after another
        for (int i = 0; i < 3; i++) {
            Button b = buttons[i];
            float ti = shownAt < 0 ? 1f : FadeDraw.progress(shownAt + 120 + i * 70L, 380);
            float e = Glass.backOut(ti);
            boolean hover = b.contains(mx, my);
            b.glow = Glass.ease(b.glow, hover, dt);
            tileLift[i] += ((hover ? 1f : 0f) - tileLift[i]) * Math.min(1f, dt * 12f);
            int ty = b.y + Math.round((1f - e) * 14) - Math.round(tileLift[i] * 3);
            Draw td = ti < 1f ? new FadeDraw(d, Math.max(0.02f, Math.min(1f, ti * 1.5f))) : d;
            drawTile(td, b, b.x, ty, b.w, b.h, b.glow, a, i == 0, now);
        }
        // Round buttons, popping in
        Button[] round = roundButtons();
        for (int i = 0; i < round.length; i++) {
            Button b = round[i];
            float ti = shownAt < 0 ? 1f : FadeDraw.progress(shownAt + 330 + i * 45L, 300);
            boolean hover = b.contains(mx, my);
            b.glow = Glass.ease(b.glow, hover, dt);
            Draw rd = ti < 1f ? new FadeDraw(d, Math.max(0.02f, Math.min(1f, ti * 1.5f))) : d;
            float sc = Glass.backOut(ti);
            int cx = b.x + ROUND / 2, cy = b.y + ROUND / 2 - Math.round(b.glow * 2);
            int r = Math.max(2, Math.round(ROUND / 2f * sc));
            boolean quit = b.action == Platform.Screen.QUIT;
            int hot = quit ? Theme.BAD : a;
            rd.roundRect(cx - r, cy - r, r * 2, r * 2, r, Theme.mix(Glass.PANE, Theme.withAlpha(hot, 0xC0), b.glow * 0.85f));
            rd.roundOutline(cx - r, cy - r, r * 2, r * 2, r, Theme.mix(Glass.EDGE, hot, b.glow));
            MenuIcons.draw(rd, b.icon, cx - 4, cy - 4, Theme.mix(0xFFD3D6DE, 0xFFFFFFFF, b.glow));
            if (b.glow > 0.05f) {  // its name floats up under it
                String label = b.label;
                int lw = rd.width(label) + 10;
                int lx = cx - lw / 2, lyy = b.y + ROUND + 4 + Math.round((1f - b.glow) * 3);
                Draw gd = new FadeDraw(rd, b.glow);
                gd.roundRect(lx, lyy, lw, 12, 6, 0xE00C0E13);
                gd.text(label, lx + 5, lyy + 2, Theme.TEXT, false);
            }
        }
        if (card[2] > 0) {
            drawCard(d, p, mx, my, dt, a);
        }
        if (panel[2] > 0) {
            int x = panel[0], y = panel[1], w = panel[2], h = panel[3];
            float pi = shownAt < 0 ? 1f : FadeDraw.progress(shownAt + 200, 450);
            int slide = Math.round((1f - Glass.backOut(pi)) * 20);
            Draw pd = pi < 1f ? new FadeDraw(d, Math.max(0.02f, pi)) : d;
            pd.pushScale(slide, 0, 1f);
            pd.shadow(x, y, w, h);
            Glass.pane(pd, x, y, w, h, 6, 0x9C0E1117);
            // a spotlight under the player
            for (int i = 0; i < 6; i++) {
                int rw = w - 24 - i * 6;
                pd.roundRect(x + (w - rw) / 2, y + h - 48 + i, rw, 3, 1, Theme.withAlpha(a, 0x10 + i * 4));
            }
            String cape = net.peregrine.client.core.modules.Cosmetics.CAPES[
                    net.peregrine.client.core.modules.Cosmetics.capeIndex()];
            String line = "Cape: " + cape;
            pd.text(pd.trim(line, w - 8), x + (w - Math.min(w - 8, pd.width(line))) / 2, y + h - 38, Theme.MUTED, false);
            lockerButton.glow = Glass.ease(lockerButton.glow, lockerButton.contains(mx - slide, my), dt);
            Glass.pill(pd, lockerButton.x, lockerButton.y, lockerButton.w, lockerButton.h, true, lockerButton.glow, a);
            MenuIcons.draw(pd, "shirt", lockerButton.x + 8, lockerButton.y + (lockerButton.h - 9) / 2, Theme.onColor(a));
            pd.text("Locker", lockerButton.x + (lockerButton.w - pd.width("Locker")) / 2 + 5, lockerButton.y + (lockerButton.h - 8) / 2 + 1,
                    Theme.onColor(a), false);
            pd.popScale();
        }
    }

    /** A big glass tile: its icon large in the middle, its name under it; the first is filled with the accent. */
    private static void drawTile(Draw d, Button b, int x, int y, int w, int h, float g, int a, boolean primary, long now) {
        d.shadow(x, y, w, h);
        if (primary) {
            d.roundRect(x, y, w, h, 7, Theme.mix(a, Theme.mix(a, 0xFFFFFFFF, 0.15f), g));
            for (int i = 7; i < h / 2; i++) {  // light from the top of the tile
                d.rect(x + 1, y + i, w - 2, 1, Theme.withAlpha(0xFFFFFF, Math.round(0x1A * (1f - i / (h / 2f)))));
            }
            d.rect(x + 7, y, w - 14, 1, 0x60FFFFFF);
            if (g > 0.01f) {
                d.roundOutline(x - 1, y - 1, w + 2, h + 2, 8, Theme.withAlpha(a, Math.round(0x90 * g)));
            }
        } else {
            Glass.pane(d, x, y, w, h, 7, Theme.mix(Glass.PANE, Glass.PANE_HOVER, g));
            if (g > 0.01f) {
                d.roundOutline(x, y, w, h, 7, Theme.withAlpha(a, Math.round(0xC0 * g)));
            }
        }
        int fg = primary ? Theme.onColor(a) : Theme.mix(0xFFD3D6DE, 0xFFFFFFFF, g);
        // the icon, twice the size, bobbing a little while hovered
        int bob = Math.round((float) Math.sin(now / 160.0) * g * 1.5f);
        d.pushScale(x + w / 2 - 9, y + 11 + bob, 2f);
        MenuIcons.draw(d, b.icon, 0, 0, fg);
        d.popScale();
        String label = b.label.equals("Peregrine modules") ? "Modules" : b.label;
        label = d.trim(label, w - 8);
        d.text(label, x + (w - d.width(label)) / 2, y + h - 17, fg, !primary);
        if (!primary && g > 0.01f) {  // a line of the accent grows under the name
            int lw = Math.round((w - 24) * g);
            d.rect(x + (w - lw) / 2, y + h - 5, lw, 1, a);
        }
    }

    // ------------------------------------------------------------------ the Locker

    private void layoutLocker(Platform p, Draw d, int sw, int sh) {
        cards.clear();
        int lx = lockerLeft(sw);
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
            if (customBackground() != null) {  // an "Adjust" button on your picture's card
                for (int[] c : new java.util.ArrayList<int[]>(cards)) {
                    if (c[0] == 1 && c[1] == net.peregrine.client.core.modules.MainMenu.CUSTOM) {
                        int bw = (d == null ? 36 : d.width("Adjust")) + 22;
                        cards.add(new int[] {4, c[1], c[2] + c[4] - bw - 7, c[3] + 7, bw, 14});
                    }
                }
            }
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
        d.textScaled("Locker", lockerLeft(p.screenWidth()) + 70, 13, Theme.TEXT, 1.6f, true);
        int[] lp = lockerPlayer;
        d.shadow(lp[0], lp[1], lp[2], lp[3]);
        d.roundRect(lp[0], lp[1], lp[2], lp[3], 4, 0x9C15171D);
        d.roundOutline(lp[0], lp[1], lp[2], lp[3], 4, 0x22FFFFFF);
        String hint = "Drag to turn";
        d.text(hint, lp[0] + (lp[2] - d.width(hint)) / 2, lp[1] + lp[3] + 6, Theme.FAINT, false);

        net.peregrine.client.core.modules.MainMenu menuMod = mainMenu();
        int worn = net.peregrine.client.core.modules.Cosmetics.capeIndex();
        int bg = menuMod == null ? 0 : menuMod.background.index;
        int order = 0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 380.0);
        for (int[] c : cards) {
            int kind = c[0], i = c[1], x = c[2], y = c[3], w = c[4], h = c[5];
            // cards arrive one after another, each rising into place
            float fade = kind == 2 ? 1f : Math.max(0f, Math.min(1f, (tabT * 1.8f - order++ * 0.09f) / 0.55f));
            fade = 1f - (1f - fade) * (1f - fade) * (1f - fade);
            boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
            if (kind == 2) {
                tabGlow[i] = ease(tabGlow[i], hover || lockerTab == i, dt);
                boolean on = lockerTab == i;
                d.roundRect(x, y, w, h, 3, on ? Theme.mix(a, 0xFFFFFFFF, 0.1f * tabGlow[i]) : Theme.mix(0xC01B1E25, 0xE02A2E38, tabGlow[i]));
                String label = TABS[i];
                d.text(label, x + 8, y + 4, on ? Theme.onColor(a) : Theme.mix(Theme.MUTED, Theme.TEXT, tabGlow[i]), false);
                continue;
            }
            if (fade <= 0.01f) {
                continue;
            }
            Draw cd = fade < 1f ? new FadeDraw(d, fade) : d;
            int dy = Math.round((1f - fade) * 14);
            if (kind == 4) {
                adjustGlow = ease(adjustGlow, hover, dt);
                int by = y + dy - Math.round(bgGlow[i] * 2);
                cd.roundRect(x, by, w, h, 3, Theme.mix(0xD0101217, a, adjustGlow));
                cd.roundOutline(x, by, w, h, 3, Theme.withAlpha(a, 0x90 + Math.round(0x6F * adjustGlow)));
                int fg = Theme.mix(Theme.TEXT, Theme.onColor(a), adjustGlow);
                MenuIcons.draw(cd, "resize", x + 4, by + 3, fg);
                cd.text("Adjust", x + 16, by + 3, fg, false);
                continue;
            }
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
            if (picked) {  // what you wear: a ring that breathes
                cd.roundOutline(x - 1, cy - 1, w + 2, h + 2, 5, Theme.withAlpha(a, Math.round(0x30 + 0x50 * pulse)));
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
                    cd.texture("peregrine-client:textures/cape/" + net.peregrine.client.core.modules.Cosmetics.frame(file) + ".png", px, py, pw, ph,
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
        if (editor.isOpen()) {
            return editor.mouseDragged(pc, mainMenu(), mx, my);
        }
        if (pressedPlayer) {
            preview.drag(mx);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(int mx, int my) {
        if (editor.isOpen()) {
            return editor.mouseReleased(pc);
        }
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
    public boolean mouseScrolled(int mx, int my, double amount) {
        return editor.isOpen() && editor.mouseScrolled(pc, mainMenu(), mx, my, amount);
    }

    /** For the self-test: the picture editor. */
    public boolean editingBackground() {
        return editor.isOpen();
    }

    public int[] editorButton(int i) {
        return editor.barButton(i);
    }

    public int[] editorCorner(int i) {
        return editor.corner(i);
    }

    public boolean keyPressed(int key) {
        if (key == Menu.KEY_ESCAPE && editor.isOpen()) {
            editor.close(pc);
            return true;
        }
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

    /**
     * Where a tw x th picture goes: zoom 1 just covers the screen; cx, cy is where its middle
     * sits, as a fraction of the screen. Returns {x, y, w, h}.
     */
    public static float[] placement(int tw, int th, int sw, int sh, float zoom, float cx, float cy) {
        float s = Math.max(sw / (float) Math.max(1, tw), sh / (float) Math.max(1, th)) * zoom;
        float w = tw * s, h = th * s;
        return new float[] {cx * sw - w / 2, cy * sh - h / 2, w, h};
    }

    /** Draws the part of a picture placed at r that's on screen (nothing outside it). */
    static void drawPlaced(Draw d, String id, float[] r, int sw, int sh, int tint) {
        float x0 = Math.max(0, r[0]), y0 = Math.max(0, r[1]);
        float x1 = Math.min(sw, r[0] + r[2]), y1 = Math.min(sh, r[1] + r[3]);
        if (x1 - x0 < 0.5f || y1 - y0 < 0.5f) {
            return;
        }
        int ix0 = Math.round(x0), iy0 = Math.round(y0), ix1 = Math.round(x1), iy1 = Math.round(y1);
        d.texture(id, ix0, iy0, ix1 - ix0, iy1 - iy0, (ix0 - r[0]) / r[2], (iy0 - r[1]) / r[3],
                (ix1 - r[0]) / r[2], (iy1 - r[1]) / r[3], tint);
    }

    /** Your picture's size in pixels (16 x 9 until it's loaded). */
    int[] customSize() {
        return new int[] {customW, customH};
    }

    String customTexture() {
        return customBackground();
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
        int tw = custom ? customW : 16, th = custom ? customH : 9;
        float zoom = custom ? editor.zoom(m) : 1f, cx = custom ? editor.cx(m) : 0.5f, cy = custom ? editor.cy(m) : 0.5f;
        float[] r = placement(tw, th, sw, sh, zoom, cx, cy);
        boolean editing = editor.active();
        if (m.drift.value && !editing) {
            // a slow drift so the picture feels alive (like the panorama turning)
            float t = (now % 120000L) / 120000f * (float) (Math.PI * 2);
            float grow = 1.04f;
            float gw = r[2] * (grow - 1f), gh = r[3] * (grow - 1f);
            r = new float[] {r[0] - gw / 2 + (float) Math.sin(t) * gw / 2, r[1] - gh / 2 + (float) Math.cos(t * 0.7f) * gh / 2,
                    r[2] + gw, r[3] + gh};
        }
        if (r[0] > 0.5f || r[1] > 0.5f || r[0] + r[2] < sw - 0.5f || r[1] + r[3] < sh - 0.5f) {
            // the picture doesn't reach the edges: a dark, softened copy fills in behind it
            d.rect(0, 0, sw, sh, 0xFF0B0D11);
            float[] behind = placement(tw, th, sw, sh, 1.15f, 0.5f, 0.5f);
            drawPlaced(d, id, behind, sw, sh, 0xFF3A3D45);
            d.rect(0, 0, sw, sh, 0x50000000);
            d.shadow(Math.round(r[0]), Math.round(r[1]), Math.round(r[2]), Math.round(r[3]));
        }
        drawPlaced(d, id, r, sw, sh, 0xFFFFFFFF);
        if (m.dim.value > 0) {
            d.rect(0, 0, sw, sh, Theme.withAlpha(0, Math.round(m.dim.value / 100f * 255)));
        }
        editor.lastPlacement = r;
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
        if (UpdateNotice.click(mx, my)) {
            return true;
        }
        if (editor.isOpen()) {
            return editor.mouseClicked(pc, mainMenu(), mx, my);
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
                if (c[0] == 4 && mx >= c[2] && mx < c[2] + c[4] && my >= c[3] && my < c[3] + c[5]) {
                    net.peregrine.client.core.modules.MainMenu m = mainMenu();
                    if (m != null) {
                        m.background.index = net.peregrine.client.core.modules.MainMenu.CUSTOM;
                        pc.markDirty();
                        editor.open();
                    }
                    return true;
                }
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
            tabT = 0f;
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
