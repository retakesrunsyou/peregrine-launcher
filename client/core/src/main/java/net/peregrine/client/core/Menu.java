package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game menu (Right Shift): a see-through panel with search, category
 * tabs and a card per feature with a toggle switch. Also the HUD editor, where
 * HUD items are dragged around.
 *
 * Version adapters just forward render/mouse/key events here.
 */
public final class Menu {

    // GLFW key codes. Adapters for old versions (LWJGL 2) translate to these.
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_ENTER = 257;
    public static final int KEY_BACKSPACE = 259;

    private static final int CARD_H = 42;
    private static final int GAP = 6;
    private static final int HEADER = 54;
    private static final int FOOTER = 20;
    private static final Module.Category[] TABS = {
        null, Module.Category.HUD, Module.Category.UTILITY, Module.Category.VISUALS
    };

    private final Peregrine pc;
    private String search = "";
    private Module.Category tab;
    private int scroll;
    private int maxScroll;
    private boolean editingHud;
    private HudModule dragging;
    private int dragDX;
    private int dragDY;
    private long openedAt;

    // Layout from the last render, used for clicks.
    private int px, py, pw, ph, gridX, gridY, gridW, gridH, cols, cardW;
    private final int[] tabX = new int[TABS.length];
    private final int[] tabW = new int[TABS.length];
    private int editX, editY, editW, editH;
    private int doneX, doneY, doneW, doneH;

    Menu(Peregrine pc) {
        this.pc = pc;
    }

    /** Call when the menu screen opens. */
    public void open() {
        openedAt = System.currentTimeMillis();
        search = "";
        scroll = 0;
        editingHud = false;
        dragging = null;
    }

    // ---- for the self-test (see SelfTest)

    boolean editingHud() {
        return editingHud;
    }

    String search() {
        return search;
    }

    /** Centre of something on screen, in GUI pixels, or null if it isn't showing. */
    int[] centerOf(String what) {
        layout(pc.platform());
        if (what.equals("edit")) {
            return editW > 0 ? new int[] {editX + editW / 2, editY + editH / 2} : null;
        }
        if (what.equals("done")) {
            return doneW > 0 ? new int[] {doneX + doneW / 2, doneY + doneH / 2} : null;
        }
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            if (mods.get(i).id.equals(what)) {
                int y = cardY(i) + CARD_H / 2;
                return y > gridY && y < gridY + gridH ? new int[] {cardX(i) + cardW / 2, y} : null;
            }
        }
        return null;
    }

    /** Call when the menu screen closes. */
    public void close() {
        dragging = null;
        editingHud = false;
        pc.markDirty();
    }

    /** The adapter dims the game behind the menu, but not while editing the HUD. */
    public boolean wantsShade() {
        return !editingHud;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private List<Module> visible() {
        List<Module> out = new ArrayList<Module>();
        for (Module m : pc.modules()) {
            if ((tab == null || m.category == tab) && m.matches(search) && pc.platform().supports(m.id)) {
                out.add(m);
            }
        }
        return out;
    }

    private void layout(Platform p) {
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        pw = Math.min(sw - 20, 460);
        ph = Math.min(sh - 20, 300);
        px = (sw - pw) / 2;
        py = (sh - ph) / 2;
        gridX = px + 10;
        gridY = py + HEADER;
        gridW = pw - 20;
        gridH = ph - HEADER - FOOTER;
        cols = gridW >= 420 ? 3 : 2;
        cardW = (gridW - (cols - 1) * GAP) / cols;
    }

    private int cardX(int i) {
        return gridX + (i % cols) * (cardW + GAP);
    }

    private int cardY(int i) {
        return gridY + (i / cols) * (CARD_H + GAP) - scroll;
    }

    // ------------------------------------------------------------ drawing

    /** How far the opening fade has got, 0-1; adapters use it to fade the background shade too. */
    public float fade() {
        return FadeDraw.progress(openedAt, 160);
    }

    public void render(Draw raw, int mx, int my) {
        Platform p = pc.platform();
        layout(p);
        SelfTest.count(editingHud ? "editor" : "menu");
        if (editingHud) {
            renderEditor(raw, mx, my, p);
            return;
        }
        float t = fade();
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        if (t < 1f) {
            // Slide up a few pixels as it fades in.
            int lift = Math.round((1f - t) * 8);
            py += lift;
            gridY += lift;
        }
        int a = pc.accent();

        d.rect(px, py, pw, ph, Theme.PANEL);
        d.outline(px, py, pw, ph, Theme.BORDER);
        d.rect(px, py, pw, 2, a);
        d.text("Peregrine", px + 10, py + 10, a, true);
        d.text("Client", px + 10 + d.width("Peregrine "), py + 10, Theme.MUTED, true);

        // Search box: typing anywhere goes here.
        int sbw = Math.min(150, pw / 3);
        int sbx = px + pw - 10 - sbw;
        int sby = py + 7;
        d.rect(sbx, sby, sbw, 16, 0xCC0E1015);
        d.outline(sbx, sby, sbw, 16, search.isEmpty() ? Theme.BORDER : a);
        boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = search.isEmpty() ? "Type to search" : search + (caret ? "_" : "");
        d.text(d.trim(shown, sbw - 8), sbx + 4, sby + 4, search.isEmpty() ? Theme.FAINT : Theme.TEXT, false);

        // Tabs
        int tx = px + 10;
        int ty = py + 32;
        for (int i = 0; i < TABS.length; i++) {
            String label = TABS[i] == null ? "All" : TABS[i].label;
            int w = d.width(label);
            tabX[i] = tx;
            tabW[i] = w;
            boolean on = TABS[i] == tab;
            boolean hover = in(mx, my, tx, ty - 2, w, 14);
            d.text(label, tx, ty, on ? Theme.TEXT : hover ? Theme.TEXT : Theme.MUTED, false);
            if (on) {
                d.rect(tx, ty + 11, w, 2, a);
            }
            tx += w + 14;
        }

        // "Edit HUD layout" button
        String edit = "Edit HUD layout";
        editW = d.width(edit) + 12;
        editH = 15;
        editX = px + pw - 10 - editW;
        editY = ty - 4;
        boolean editHover = in(mx, my, editX, editY, editW, editH);
        d.rect(editX, editY, editW, editH, editHover ? Theme.CARD_HOVER : Theme.CARD);
        d.outline(editX, editY, editW, editH, editHover ? a : Theme.BORDER);
        d.text(edit, editX + 6, editY + 4, Theme.TEXT, false);

        // Cards
        List<Module> mods = visible();
        int rows = (mods.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (CARD_H + GAP) - GAP - gridH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        d.clip(gridX, gridY, gridW, gridH);
        boolean mouseInGrid = in(mx, my, gridX, gridY, gridW, gridH);
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int cx = cardX(i);
            int cy = cardY(i);
            if (cy + CARD_H < gridY || cy > gridY + gridH) {
                continue;
            }
            boolean hover = mouseInGrid && in(mx, my, cx, cy, cardW, CARD_H);
            d.rect(cx, cy, cardW, CARD_H, hover ? Theme.CARD_HOVER : Theme.CARD);
            if (m.enabled()) {
                d.rect(cx, cy, 2, CARD_H, a);
            }
            d.text(d.trim(m.name, cardW - 40), cx + 8, cy + 8, Theme.TEXT, false);
            boolean warn = hover && m.warning() != null;
            String sub = warn ? m.warning() : m.description;
            d.text(d.trim(sub, cardW - 14), cx + 8, cy + 24, warn ? Theme.OK : Theme.MUTED, false);
            toggle(d, cx + cardW - 28, cy + 7, m.enabled(), a);
        }
        d.unclip();

        if (mods.isEmpty()) {
            String none = "Nothing matches \"" + search + "\"";
            d.text(none, px + (pw - d.width(none)) / 2, gridY + 20, Theme.MUTED, false);
        }
        if (maxScroll > 0) {
            int barH = Math.max(16, gridH * gridH / (gridH + maxScroll));
            int barY = gridY + (gridH - barH) * scroll / maxScroll;
            d.rect(px + pw - 5, barY, 2, barH, Theme.BORDER);
        }

        // Footer
        int on = 0;
        int total = 0;
        for (Module m : pc.modules()) {
            if (p.supports(m.id)) {
                total++;
                on += m.enabled() ? 1 : 0;
            }
        }
        d.text("Right Shift opens this menu. Esc closes it.", px + 10, py + ph - 14, Theme.FAINT, false);
        String count = on + " of " + total + " on";
        d.text(count, px + pw - 10 - d.width(count), py + ph - 14, Theme.FAINT, false);
    }

    /** A squared-off switch, matching the launcher's toggles. */
    private static void toggle(Draw d, int x, int y, boolean on, int accent) {
        d.rect(x, y, 20, 10, on ? accent : Theme.BORDER);
        d.rect(x + (on ? 12 : 2), y + 2, 6, 6, 0xFFFFFFFF);
    }

    private void renderEditor(Draw d, int mx, int my, Platform p) {
        int a = pc.accent();
        if (!p.inWorld()) {
            // HUD items only exist in a world, so there's nothing to arrange here.
            String line1 = "Join a world to arrange your HUD";
            String line2 = "Your HUD items appear here once you're playing.";
            String done = "Back";
            int w = Math.max(d.width(line1), d.width(line2)) + 32;
            int h = 74;
            int x = (p.screenWidth() - w) / 2;
            int y = (p.screenHeight() - h) / 2;
            d.rect(x, y, w, h, Theme.PANEL);
            d.outline(x, y, w, h, Theme.BORDER);
            d.rect(x, y, w, 2, a);
            d.text(line1, x + (w - d.width(line1)) / 2, y + 14, Theme.TEXT, false);
            d.text(line2, x + (w - d.width(line2)) / 2, y + 28, Theme.MUTED, false);
            doneW = d.width(done) + 20;
            doneH = 16;
            doneX = x + (w - doneW) / 2;
            doneY = y + h - doneH - 10;
            boolean hover = in(mx, my, doneX, doneY, doneW, doneH);
            d.rect(doneX, doneY, doneW, doneH, Theme.withAlpha(a, hover ? 0xFF : 0xDD));
            d.text(done, doneX + 10, doneY + 4, 0xFF15171C, false);
            return;
        }
        for (Module m : pc.modules()) {
            if (!(m instanceof HudModule) || !m.enabled()) {
                continue;
            }
            HudModule h = (HudModule) m;
            if (h.lastX < 0) {
                continue;
            }
            boolean hover = h == dragging || h.contains(mx, my);
            if (hover) {
                d.rect(h.lastX, h.lastY, h.lastW, h.lastH, Theme.withAlpha(a, 0x30));
                int ly = h.lastY > 12 ? h.lastY - 11 : h.lastY + h.lastH + 2;
                d.text(h.name, h.lastX, ly, a, true);
            }
            d.outline(h.lastX, h.lastY, h.lastW, h.lastH, hover ? a : Theme.withAlpha(a, 0x90));
        }

        String hint = "Drag HUD items to move them";
        String done = "Done";
        doneW = d.width(done) + 16;
        doneH = 16;
        int bannerW = d.width(hint) + doneW + 24;
        int bx = (p.screenWidth() - bannerW) / 2;
        int by = 8;
        d.rect(bx, by, bannerW, 24, Theme.PANEL);
        d.outline(bx, by, bannerW, 24, Theme.BORDER);
        d.text(hint, bx + 8, by + 8, Theme.TEXT, false);
        doneX = bx + bannerW - 4 - doneW;
        doneY = by + 4;
        boolean hover = in(mx, my, doneX, doneY, doneW, doneH);
        d.rect(doneX, doneY, doneW, doneH, hover ? Theme.withAlpha(a, 0xFF) : Theme.withAlpha(a, 0xDD));
        d.text(done, doneX + 8, doneY + 4, 0xFF15171C, false);
    }

    // -------------------------------------------------------------- input

    /** Returns true if the click was used. button: 0 = left. */
    public boolean mouseClicked(int mx, int my, int button) {
        if (button != 0) {
            return false;
        }
        Platform p = pc.platform();
        if (editingHud) {
            if (in(mx, my, doneX, doneY, doneW, doneH)) {
                editingHud = false;
                return true;
            }
            if (!p.inWorld()) {
                return true;
            }
            List<Module> mods = pc.modules();
            for (int i = mods.size() - 1; i >= 0; i--) {  // topmost first
                Module m = mods.get(i);
                if (m instanceof HudModule && m.enabled() && ((HudModule) m).contains(mx, my)) {
                    dragging = (HudModule) m;
                    dragDX = mx - dragging.lastX;
                    dragDY = my - dragging.lastY;
                    return true;
                }
            }
            return true;
        }
        layout(p);
        if (!in(mx, my, px, py, pw, ph)) {
            return false;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, tabX[i], py + 30, tabW[i], 14)) {
                tab = TABS[i];
                scroll = 0;
                return true;
            }
        }
        if (in(mx, my, editX, editY, editW, editH)) {
            editingHud = true;
            return true;
        }
        if (in(mx, my, gridX, gridY, gridW, gridH)) {
            List<Module> mods = visible();
            for (int i = 0; i < mods.size(); i++) {
                if (in(mx, my, cardX(i), cardY(i), cardW, CARD_H)) {
                    Module m = mods.get(i);
                    m.setEnabled(!m.enabled());
                    pc.markDirty();
                    return true;
                }
            }
        }
        return true;
    }

    public boolean mouseDragged(int mx, int my) {
        if (dragging == null) {
            return false;
        }
        dragging.moveTo(mx - dragDX, my - dragDY, pc.platform());
        pc.markDirty();
        return true;
    }

    public boolean mouseReleased() {
        boolean was = dragging != null;
        dragging = null;
        return was;
    }

    /** amount: positive = scroll up. */
    public boolean mouseScrolled(double amount) {
        if (editingHud) {
            return false;
        }
        scroll -= (int) Math.round(amount * 24);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        return true;
    }

    /** Returns true if the key was used. Esc is only used in the HUD editor. */
    public boolean keyPressed(int key) {
        if (editingHud) {
            if (key == KEY_ESCAPE || key == KEY_ENTER) {
                editingHud = false;
                return true;
            }
            return false;
        }
        if (key == KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            scroll = 0;
            return true;
        }
        if (key == KEY_ENTER) {
            List<Module> mods = visible();
            if (mods.size() == 1) {  // search narrowed to one: Enter toggles it
                mods.get(0).setEnabled(!mods.get(0).enabled());
                pc.markDirty();
                return true;
            }
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (editingHud || Character.isISOControl(c) || search.length() >= 24) {
            return false;
        }
        if (search.isEmpty() && c == ' ') {
            return true;
        }
        search += c;
        scroll = 0;
        return true;
    }
}
