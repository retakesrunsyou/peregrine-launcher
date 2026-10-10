package net.peregrine.client.core;

/**
 * Peregrine's main menu, shown instead of Minecraft's title screen: a big
 * PEREGRINE title, a welcome line, and squared-off buttons over the game's
 * moving panorama. Version adapters draw the panorama, then call render().
 */
public final class TitleMenu {

    public static final String CLIENT_VERSION = "0.9.1";

    private static final int W = 204;
    private static final int H = 22;
    private static final int GAP = 5;

    private static final class Button {
        final String label;
        final Platform.Screen action;
        int x, y, w, h;

        Button(String label, Platform.Screen action) {
            this.label = label;
            this.action = action;
        }

        float glow;  // hover highlight, eased in and out

        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Peregrine pc;
    private long shownAt = -1;
    private long lastFrame;
    private final Button[] buttons = {
        new Button("Singleplayer", Platform.Screen.SINGLEPLAYER),
        new Button("Multiplayer", Platform.Screen.MULTIPLAYER),
        new Button("Options", Platform.Screen.OPTIONS),
        new Button("Peregrine", Platform.Screen.PEREGRINE_MENU),
        new Button("Quit", Platform.Screen.QUIT),
    };

    TitleMenu(Peregrine pc) {
        this.pc = pc;
    }

    private void layout(Platform p) {
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        int x = (sw - W) / 2;
        int top = Math.max(sh / 2 - 6, 96);
        // Two wide buttons, then a row of three.
        for (int i = 0; i < 2; i++) {
            Button b = buttons[i];
            b.x = x;
            b.y = top + i * (H + GAP);
            b.w = W;
            b.h = H;
        }
        int small = (W - 2 * GAP) / 3;
        for (int i = 2; i < 5; i++) {
            Button b = buttons[i];
            b.x = x + (i - 2) * (small + GAP);
            b.y = top + 2 * (H + GAP) + 6;
            b.w = i == 4 ? W - 2 * (small + GAP) : small;
            b.h = H;
        }
    }

    /** The game window's title: "Peregrine Client 0.4.0 - Minecraft 1.21.1 - Singleplayer". */
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
        layout(p);
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        int a = pc.accent();

        // Darken the panorama slightly so the text reads clearly, more towards the bottom.
        d.rect(0, 0, sw, sh, 0x4A000000);
        for (int i = 0; i < 8; i++) {
            int band = sh / 3 / 8;
            d.rect(0, sh - (i + 1) * band, sw, band, Theme.withAlpha(0, 0x0A * (8 - i)));
        }

        // Big title: PEREGRINE in the accent color, scaled up like Minecraft's logo.
        String title = "PEREGRINE";
        float scale = Math.max(2f, Math.min(5f, sw / 110f));
        int tw = Math.round(d.width(title) * scale);
        int ty = Math.max(16, buttons[0].y - Math.round(d.lineHeight() * scale) - 46);
        d.textScaled(title, (sw - tw) / 2, ty, a, scale, true);

        String sub = "CLIENT";
        int subW = Math.round(d.width(sub) * 1.5f);
        int subY = ty + Math.round(d.lineHeight() * scale) + 4;
        d.textScaled(sub, (sw - subW) / 2, subY, Theme.TEXT, 1.5f, true);

        d.rect((sw - 24) / 2, subY + 15, 24, 1, Theme.withAlpha(a, 0xC0));
        String welcome = "Welcome back, " + p.playerName();
        d.text(welcome, (sw - d.width(welcome)) / 2, subY + 21, 0xFFC9CDD6, true);

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (Button b : buttons) {
            boolean hover = b.contains(mx, my);
            b.glow = hover ? Math.min(1f, b.glow + dt * 8f) : Math.max(0f, b.glow - dt * 6f);
            float g = b.glow;
            d.shadow(b.x, b.y, b.w, b.h);
            d.roundRect(b.x, b.y, b.w, b.h, Theme.mix(0xB4161920, 0xE0262B36, g));
            d.roundOutline(b.x, b.y, b.w, b.h, Theme.mix(0x30FFFFFF, Theme.withAlpha(a, 0xFF), g));
            if (g > 0) {
                int lw = Math.round((b.w - 16) * g);
                d.rect(b.x + (b.w - lw) / 2, b.y + b.h - 2, lw, 1, Theme.withAlpha(a, Math.round(0xFF * g)));
            }
            int lx = b.x + (b.w - d.width(b.label)) / 2;
            d.text(b.label, lx, b.y + (b.h - d.lineHeight()) / 2 + 1,
                    Theme.mix(0xFFD0D2DA, Theme.TEXT, g), true);
        }

        String left = "Peregrine Client " + CLIENT_VERSION + " for Minecraft " + p.minecraftVersion();
        d.text(left, 4, sh - 12, Theme.FAINT, true);
        String right = "Copyright Mojang AB. Do not distribute!";
        d.text(right, sw - 4 - d.width(right), sh - 12, Theme.FAINT, true);
    }

    public boolean mouseClicked(int mx, int my, int button) {
        SelfTest.count("titleclick@" + mx + "," + my + "/" + button);
        if (button != 0) {
            return false;
        }
        layout(pc.platform());
        for (Button b : buttons) {
            if (b.contains(mx, my)) {
                pc.platform().openScreen(b.action);
                return true;
            }
        }
        return false;
    }

    /** For the self-test: centre of the button that opens that screen, in GUI pixels. */
    int[] centerOf(Platform.Screen action) {
        layout(pc.platform());
        for (Button b : buttons) {
            if (b.action == action) {
                return new int[] {b.x + b.w / 2, b.y + b.h / 2};
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
