package net.peregrine.client.core;

/**
 * Peregrine's main menu, shown instead of Minecraft's title screen: the logo, your
 * name and head, a column of buttons with icons (the first in the accent color),
 * small Realms / Language / Accessibility buttons at the bottom, and a "Jump back
 * in" card for the last server you played. Version adapters draw the panorama,
 * then call render().
 */
public final class TitleMenu {

    public static final String CLIENT_VERSION = "1.2.0";

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
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        Platform p = pc.platform();
        layout(p, d);
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        int a = pc.accent();

        // Darken the panorama, most on the left behind the buttons.
        d.rect(0, 0, sw, sh, 0x38000000);
        int band = Math.max(1, sw / 2 / 10);
        for (int i = 0; i < 10; i++) {
            d.rect(i * band, 0, band, sh, Theme.withAlpha(0, 0x0C * (10 - i)));
        }

        // Header: the logo, "Peregrine", your head and name.
        int hy = top;
        d.textScaled(Hooks.LOGO, left, hy, 0xFFFFFFFF, 4f, false);
        d.textScaled("Peregrine", left + 38, hy + 1, Theme.TEXT, 2f, true);
        int[] face = SkinFaces.get(p.playerUuid());
        int ny = hy + 21;
        int nx = left + 38;
        if (face != null) {
            SkinFaces.draw(d, face, nx, ny, 8);
            nx += 11;
        }
        d.text(p.playerName(), nx, ny, Theme.MUTED, true);

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
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

        String ver = versionLine(p);
        d.text(ver, sw - 6 - d.width(ver), sh - 20, Theme.FAINT, true);
        d.text(COPYRIGHT, sw - 6 - d.width(COPYRIGHT), sh - 10, Theme.FAINT, true);
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
