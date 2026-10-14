package net.peregrine.client.core;

/**
 * Peregrine's pause menu: one card in the middle of the screen with the logo, your
 * name and head, Resume (in the accent color), Peregrine modules, Options, the rest
 * of Minecraft's menu (advancements, statistics, open to LAN...) and Leave.
 */
public final class PauseMenu {

    static final int W = 200, H = 20, GAP = 5, PAD = 12;

    private final Peregrine pc;
    private final TitleMenu.Button resume = new TitleMenu.Button("Resume game", "play", Platform.Screen.NONE, true);
    private final TitleMenu.Button modules = new TitleMenu.Button("Peregrine modules", "grid", Platform.Screen.PEREGRINE_MENU, false);
    private final TitleMenu.Button options = new TitleMenu.Button("Options", "sliders", Platform.Screen.OPTIONS, false);
    private final TitleMenu.Button more = new TitleMenu.Button("Advancements, stats & more", "more", Platform.Screen.VANILLA_PAUSE, false);
    private TitleMenu.Button leave = new TitleMenu.Button("Leave server", "leave", null, false);
    private final int[] box = new int[4];
    private long shownAt = -1, lastFrame;

    PauseMenu(Peregrine pc) {
        this.pc = pc;
    }

    private TitleMenu.Button[] buttons() {
        return new TitleMenu.Button[] {resume, modules, options, more, leave};
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
        TitleMenu.Button[] bs = buttons();
        int h = PAD + 34 + bs.length * (H + GAP) - GAP + PAD;
        int w = W + 2 * PAD;
        box[0] = (p.screenWidth() - w) / 2;
        box[1] = Math.max(4, (p.screenHeight() - h) / 2);
        box[2] = w;
        box[3] = h;
        int y = box[1] + PAD + 34;
        for (TitleMenu.Button b : bs) {
            b.place(box[0] + PAD, y, W, H);
            y += H + GAP;
        }
    }

    public void render(Draw raw, int mx, int my) {
        SelfTest.count("pause");
        float t = shownAt < 0 ? 1f : FadeDraw.progress(shownAt, 180);
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        Platform p = pc.platform();
        layout(p);
        int a = pc.accent();
        d.rect(0, 0, p.screenWidth(), p.screenHeight(), 0x50000000);
        int x = box[0], y = box[1];
        // the card rises a few pixels as it fades in
        int rise = Math.round((1f - t) * 6);
        y += rise;
        d.shadow(x, y, box[2], box[3]);
        d.roundRect(x, y, box[2], box[3], 4, 0xF0141619);
        d.roundOutline(x, y, box[2], box[3], 4, Theme.HAIRLINE);

        d.textScaled(Hooks.LOGO, x + PAD, y + PAD, 0xFFFFFFFF, 3f, false);
        d.textScaled("Game menu", x + PAD + 30, y + PAD + 1, Theme.TEXT, 1.5f, true);
        int nx = x + PAD + 30, ny = y + PAD + 15;
        int[] face = SkinFaces.get(p.playerUuid());
        if (face != null) {
            SkinFaces.draw(d, face, nx, ny, 8);
            nx += 11;
        }
        d.text(p.playerName(), nx, ny, Theme.MUTED, false);

        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (TitleMenu.Button b : buttons()) {
            b.y += rise;
            b.draw(d, mx, my, dt, a, false);
            b.y -= rise;
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return false;
        }
        Platform p = pc.platform();
        layout(p);
        for (TitleMenu.Button b : buttons()) {
            if (b.contains(mx, my)) {
                if (b.action == null) {
                    p.leaveWorld();
                } else {
                    p.openScreen(b.action);
                }
                return true;
            }
        }
        return false;
    }

    /** For the self-test: centre of a button, by what it opens (null: Leave). */
    int[] centerOf(Platform.Screen action) {
        layout(pc.platform());
        for (TitleMenu.Button b : buttons()) {
            if (b.action == action) {
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
