package net.peregrine.client.core;

/**
 * Peregrine's main menu, shown instead of Minecraft's title screen: a big
 * PEREGRINE title, a welcome line, and squared-off buttons over the game's
 * moving panorama. Version adapters draw the panorama, then call render().
 */
public final class TitleMenu {

    public static final String CLIENT_VERSION = "0.2.0";

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

        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final Peregrine pc;
    private long shownAt = -1;
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

    /** Call when the main menu screen is created, so it fades in. */
    public void shown() {
        shownAt = System.currentTimeMillis();
    }

    public void render(Draw raw, int mx, int my) {
        float t = shownAt < 0 ? 1f : FadeDraw.progress(shownAt, 400);
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        Platform p = pc.platform();
        layout(p);
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        int a = pc.accent();

        // Darken the panorama slightly so the text reads clearly.
        d.rect(0, 0, sw, sh, 0x40000000);

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

        String welcome = "Welcome back, " + p.playerName();
        d.text(welcome, (sw - d.width(welcome)) / 2, subY + 20, Theme.MUTED, true);

        for (Button b : buttons) {
            boolean hover = b.contains(mx, my);
            d.rect(b.x, b.y, b.w, b.h, hover ? 0xD0272C37 : 0xB0171A21);
            d.outline(b.x, b.y, b.w, b.h, hover ? a : Theme.BORDER);
            if (hover) {
                d.rect(b.x, b.y, 2, b.h, a);
            }
            int lx = b.x + (b.w - d.width(b.label)) / 2;
            d.text(b.label, lx, b.y + (b.h - d.lineHeight()) / 2 + 1, hover ? Theme.TEXT : 0xFFD0D2DA, true);
        }

        String left = "Peregrine Client " + CLIENT_VERSION + " for Minecraft " + p.minecraftVersion();
        d.text(left, 4, sh - 12, Theme.FAINT, true);
        String right = "Copyright Mojang AB. Do not distribute!";
        d.text(right, sw - 4 - d.width(right), sh - 12, Theme.FAINT, true);
    }

    public boolean mouseClicked(int mx, int my, int button) {
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

    /** Whether to replace Minecraft's title screen (the "Peregrine main menu" toggle). */
    public boolean enabled() {
        Module m = pc.module("main_menu");
        return m == null || m.enabled();
    }
}
