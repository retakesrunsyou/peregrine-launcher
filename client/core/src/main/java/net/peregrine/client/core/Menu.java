package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The in-game menu (Right Shift): a compact window in the middle of the screen
 * with categories down the side, a search box, and a list of features with
 * switches. Also the HUD editor, where HUD items are dragged around.
 *
 * Version adapters just forward render/mouse/key events here.
 */
public final class Menu {

    // GLFW key codes. Adapters for other input systems translate to these.
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_ENTER = 257;
    public static final int KEY_BACKSPACE = 259;

    private static final int TITLE_H = 26;
    private static final int FOOTER_H = 18;
    private static final int SIDE_W = 82;
    private static final int ROW_H = 28;
    private static final int TAB_H = 18;
    private static final Module.Category[] TABS = {
        null, Module.Category.HUD, Module.Category.UTILITY, Module.Category.VISUALS
    };

    private final Peregrine pc;
    private String search = "";
    private Module.Category tab;
    private int scroll;
    private float smoothScroll;
    private int maxScroll;
    private boolean editingHud;
    private HudModule dragging;
    private int dragDX;
    private int dragDY;
    private long openedAt;
    private long lastFrame;
    private final Map<Module, Float> knob = new IdentityHashMap<Module, Float>();

    // Layout from the last render, used for clicks.
    private int px, py, pw, ph, listX, listY, listW, listH;
    private int editX, editY, editW, editH;
    private int doneX, doneY, doneW, doneH;
    private int closeX, closeY;
    private int searchX, searchY, searchW;

    Menu(Peregrine pc) {
        this.pc = pc;
    }

    /** Call when the menu screen opens. */
    public void open() {
        openedAt = System.currentTimeMillis();
        lastFrame = 0;
        search = "";
        scroll = 0;
        smoothScroll = 0;
        editingHud = false;
        dragging = null;
        knob.clear();
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
                int y = rowY(i) + ROW_H / 2;
                return y > listY && y < listY + listH ? new int[] {listX + listW / 2, y} : null;
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

    private int count(Module.Category c, boolean onlyOn) {
        int n = 0;
        for (Module m : pc.modules()) {
            if ((c == null || m.category == c) && pc.platform().supports(m.id) && (!onlyOn || m.enabled())) {
                n++;
            }
        }
        return n;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** A medium window: about two thirds of the screen, never edge to edge. */
    private void layout(Platform p) {
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        pw = Math.min(sw - 16, clamp(Math.round(sw * 0.62f), 300, 400));
        ph = Math.min(sh - 16, clamp(Math.round(sh * 0.70f), 186, 264));
        px = (sw - pw) / 2;
        py = (sh - ph) / 2;
        listX = px + SIDE_W + 6;
        listY = py + TITLE_H + 4;
        listW = pw - SIDE_W - 12;
        listH = ph - TITLE_H - FOOTER_H - 6;
        searchW = clamp(pw / 3, 90, 130);
        searchX = px + pw - 22 - searchW;
        searchY = py + 6;
        closeX = px + pw - 16;
        closeY = py + 8;
        editW = SIDE_W - 12;
        editH = 16;
        editX = px + 6;
        editY = py + ph - FOOTER_H - editH - 2;
    }

    private int rowY(int i) {
        return listY + i * (ROW_H + 2) - Math.round(smoothScroll);
    }

    private int tabY(int i) {
        return py + TITLE_H + 6 + i * (TAB_H + 2);
    }

    // ------------------------------------------------------------ drawing

    /** How far the opening fade has got, 0-1; adapters use it to fade the background shade too. */
    public float fade() {
        return FadeDraw.progress(openedAt, 180);
    }

    public void render(Draw raw, int mx, int my) {
        Platform p = pc.platform();
        layout(p);
        SelfTest.count(editingHud ? "editor" : "menu");
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (editingHud) {
            renderEditor(raw, mx, my, p);
            return;
        }
        float t = fade();
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        if (t < 1f) {
            // Ease up into place as it fades in.
            float e = 1f - (1f - t) * (1f - t);
            int lift = Math.round((1f - e) * 10);
            py += lift;
            listY += lift;
            searchY += lift;
            closeY += lift;
            editY += lift;
        }
        int a = pc.accent();

        // Window
        d.shadow(px, py, pw, ph);
        d.roundRect(px, py, pw, ph, Theme.PANEL);
        d.roundOutline(px, py, pw, ph, Theme.HAIRLINE);
        d.rect(px + 2, py, pw - 4, 1, Theme.withAlpha(a, 0xE0));
        d.rect(px + 1, py + 1, pw - 2, 1, Theme.withAlpha(a, 0x50));

        // Title bar: name, search, close
        d.rect(px + 1, py + 2, pw - 2, TITLE_H - 2, Theme.TITLE_BAR);
        d.rect(px + 1, py + TITLE_H, pw - 2, 1, Theme.HAIRLINE);
        int ty = py + (TITLE_H - 8) / 2 + 1;
        d.rect(px + 9, ty + 1, 3, 7, a);  // a small accent mark before the name
        d.text("Peregrine", px + 16, ty, Theme.TEXT, false);
        d.text("Client", px + 16 + d.width("Peregrine "), ty, Theme.MUTED, false);

        boolean searching = !search.isEmpty();
        d.roundRect(searchX, searchY, searchW, 15, Theme.FIELD);
        d.roundOutline(searchX, searchY, searchW, 15, searching ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
        boolean caret = (now / 500) % 2 == 0;
        String shown = searching ? search + (caret ? "_" : "") : "Search";
        d.text(d.trim(shown, searchW - 10), searchX + 5, searchY + 4, searching ? Theme.TEXT : Theme.FAINT, false);

        boolean closeHover = in(mx, my, closeX - 3, closeY - 3, 13, 13);
        if (closeHover) {
            d.roundRect(closeX - 3, closeY - 3, 13, 13, Theme.ROW_HOVER);
        }
        int xc = closeHover ? Theme.TEXT : Theme.MUTED;
        for (int i = 0; i < 7; i++) {  // a small drawn ×, crisp at any GUI scale
            d.rect(closeX + i, closeY + i, 1, 1, xc);
            d.rect(closeX + 6 - i, closeY + i, 1, 1, xc);
        }

        // Sidebar: categories with counts, Edit HUD at the bottom
        d.rect(px + 1, py + TITLE_H + 1, SIDE_W, ph - TITLE_H - 3, Theme.SIDEBAR);
        d.rect(px + SIDE_W + 1, py + TITLE_H + 1, 1, ph - TITLE_H - 3, Theme.HAIRLINE);
        for (int i = 0; i < TABS.length; i++) {
            int y = tabY(i);
            boolean on = TABS[i] == tab;
            boolean hover = in(mx, my, px + 4, y, SIDE_W - 8, TAB_H);
            if (on) {
                d.roundRect(px + 4, y, SIDE_W - 8, TAB_H, Theme.withAlpha(a, 0x2C));
                d.rect(px + 4, y + 4, 2, TAB_H - 8, a);
            } else if (hover) {
                d.roundRect(px + 4, y, SIDE_W - 8, TAB_H, Theme.ROW_HOVER);
            }
            String label = TABS[i] == null ? "All" : TABS[i].label;
            d.text(label, px + 11, y + 5, on ? Theme.TEXT : hover ? Theme.TEXT : Theme.MUTED, false);
            String n = String.valueOf(count(TABS[i], true));
            d.text(n, px + SIDE_W - 9 - d.width(n), y + 5, on ? Theme.withAlpha(a, 0xFF) : Theme.FAINT, false);
        }
        boolean editHover = in(mx, my, editX, editY, editW, editH);
        d.roundRect(editX, editY, editW, editH, editHover ? Theme.withAlpha(a, 0xFF) : Theme.withAlpha(a, 0x26));
        d.roundOutline(editX, editY, editW, editH, Theme.withAlpha(a, editHover ? 0xFF : 0x90));
        String edit = "Edit HUD";
        d.text(edit, editX + (editW - d.width(edit)) / 2, editY + 4,
                editHover ? 0xFF15171C : Theme.withAlpha(a, 0xFF), false);

        // Feature list
        List<Module> mods = visible();
        maxScroll = Math.max(0, mods.size() * (ROW_H + 2) - 2 - listH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }

        d.clip(listX, listY, listW, listH);
        boolean mouseInList = in(mx, my, listX, listY, listW, listH);
        int textW = listW - 44;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int ry = rowY(i);
            if (ry + ROW_H < listY || ry > listY + listH) {
                continue;
            }
            boolean hover = mouseInList && in(mx, my, listX, ry, listW, ROW_H);
            if (hover) {
                d.roundRect(listX, ry, listW, ROW_H, Theme.ROW_HOVER);
            }
            d.text(d.trim(m.name, textW), listX + 7, ry + 5, Theme.TEXT, false);
            boolean warn = hover && m.warning() != null;
            String sub = warn ? m.warning() : m.description;
            d.text(d.trim(sub, textW), listX + 7, ry + 16, warn ? Theme.OK : Theme.FAINT, false);
            float k = animate(m, dt);
            toggle(d, listX + listW - 30, ry + (ROW_H - 10) / 2, k, a, hover);
            if (i < mods.size() - 1) {
                d.rect(listX + 7, ry + ROW_H + 1, listW - 14, 1, 0x0CFFFFFF);
            }
        }
        d.unclip();

        if (mods.isEmpty()) {
            String none = "Nothing matches \"" + d.trim(search, listW - 80) + "\"";
            d.text(none, listX + (listW - d.width(none)) / 2, listY + listH / 2 - 8, Theme.MUTED, false);
            String hint = "Backspace to clear the search";
            d.text(hint, listX + (listW - d.width(hint)) / 2, listY + listH / 2 + 4, Theme.FAINT, false);
        }
        if (maxScroll > 0) {
            int track = listH - 4;
            int barH = Math.max(14, track * listH / (listH + maxScroll));
            int barY = listY + 2 + Math.round((track - barH) * (smoothScroll / maxScroll));
            d.roundRect(px + pw - 5, barY, 2, barH, mouseInList ? Theme.withAlpha(a, 0xB0) : 0x40FFFFFF);
        }

        // Footer
        int fy = py + ph - FOOTER_H + 5;
        d.rect(listX, py + ph - FOOTER_H - 1, listW, 1, Theme.HAIRLINE);
        String hint = searching ? mods.size() + (mods.size() == 1 ? " result" : " results")
                : "Type to search";
        d.text(d.trim(hint, listW - 60), listX + 4, fy, Theme.FAINT, false);
        String counts = count(null, true) + " / " + count(null, false) + " on";
        d.text(counts, px + pw - 9 - d.width(counts), fy, Theme.FAINT, false);
    }

    /** Moves a switch's knob towards on or off; returns 0 (off) to 1 (on). */
    private float animate(Module m, float dt) {
        float target = m.enabled() ? 1f : 0f;
        Float cur = knob.get(m);
        float v = cur == null ? target : cur;
        if (v < target) {
            v = Math.min(target, v + dt * 9f);
        } else if (v > target) {
            v = Math.max(target, v - dt * 9f);
        }
        knob.put(m, v);
        return v;
    }

    /** A rounded switch; on is filled with the accent and the knob slides right. */
    private static void toggle(Draw d, int x, int y, float on, int accent, boolean hover) {
        int track = Theme.mix(hover ? 0xFF3C4352 : 0xFF2F3542, accent, on);
        d.roundRect(x, y, 22, 10, track);
        int kx = x + 2 + Math.round(on * 10);
        d.rect(kx + 1, y + 2, 6, 6, 0xFFFFFFFF);
        d.rect(kx, y + 3, 8, 4, 0xFFFFFFFF);
    }

    private void renderEditor(Draw d, int mx, int my, Platform p) {
        int a = pc.accent();
        if (!p.inWorld()) {
            // HUD items only exist in a world, so there's nothing to arrange here.
            String line1 = "Join a world to arrange your HUD";
            String line2 = "Your HUD items appear here once you're playing.";
            String done = "Back";
            int w = Math.max(d.width(line1), d.width(line2)) + 36;
            int h = 76;
            int x = (p.screenWidth() - w) / 2;
            int y = (p.screenHeight() - h) / 2;
            d.shadow(x, y, w, h);
            d.roundRect(x, y, w, h, Theme.PANEL);
            d.roundOutline(x, y, w, h, Theme.HAIRLINE);
            d.rect(x + 2, y, w - 4, 1, a);
            d.text(line1, x + (w - d.width(line1)) / 2, y + 15, Theme.TEXT, false);
            d.text(line2, x + (w - d.width(line2)) / 2, y + 29, Theme.MUTED, false);
            doneW = d.width(done) + 24;
            doneH = 16;
            doneX = x + (w - doneW) / 2;
            doneY = y + h - doneH - 11;
            boolean hover = in(mx, my, doneX, doneY, doneW, doneH);
            d.roundRect(doneX, doneY, doneW, doneH, Theme.withAlpha(a, hover ? 0xFF : 0xDD));
            d.text(done, doneX + 12, doneY + 4, 0xFF15171C, false);
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
            if (!h.hasContent(p)) {
                // Nothing to show yet (like ping in singleplayer): label the box so it isn't blank.
                d.roundRect(h.lastX, h.lastY, h.lastW, h.lastH, Theme.withAlpha(Theme.PANEL, 0x90));
                String label = d.trim(h.name, Math.max(0, h.lastW - 4));
                d.text(label, h.lastX + (h.lastW - d.width(label)) / 2,
                        h.lastY + (h.lastH - d.lineHeight()) / 2 + 1, Theme.MUTED, false);
            }
            if (hover) {
                d.roundRect(h.lastX, h.lastY, h.lastW, h.lastH, Theme.withAlpha(a, 0x30));
                int lw = d.width(h.name) + 8;
                int ly = h.lastY > 14 ? h.lastY - 13 : h.lastY + h.lastH + 2;
                d.roundRect(h.lastX, ly, lw, 12, Theme.withAlpha(a, 0xE8));
                d.text(h.name, h.lastX + 4, ly + 2, 0xFF15171C, false);
            }
            d.roundOutline(h.lastX, h.lastY, h.lastW, h.lastH, hover ? a : Theme.withAlpha(a, 0x80));
        }

        String hint = "Drag HUD items to move them";
        String done = "Done";
        doneW = d.width(done) + 18;
        doneH = 16;
        int bannerW = d.width(hint) + doneW + 26;
        int bx = (p.screenWidth() - bannerW) / 2;
        int by = 8;
        d.shadow(bx, by, bannerW, 24);
        d.roundRect(bx, by, bannerW, 24, Theme.PANEL);
        d.roundOutline(bx, by, bannerW, 24, Theme.HAIRLINE);
        d.text(hint, bx + 9, by + 8, Theme.TEXT, false);
        doneX = bx + bannerW - 4 - doneW;
        doneY = by + 4;
        boolean hover = in(mx, my, doneX, doneY, doneW, doneH);
        d.roundRect(doneX, doneY, doneW, doneH, Theme.withAlpha(a, hover ? 0xFF : 0xDD));
        d.text(done, doneX + 9, doneY + 4, 0xFF15171C, false);
    }

    // -------------------------------------------------------------- input

    /** Returns true if the click was used. button: 0 = left. */
    public boolean mouseClicked(int mx, int my, int button) {
        SelfTest.count("click@" + mx + "," + my + "/" + button);
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
        if (!in(mx, my, px, py, pw, ph) || in(mx, my, closeX - 3, closeY - 3, 13, 13)) {
            // A click outside the window (or on ×) closes it, like any pop-up.
            p.openScreen(p.inWorld() ? Platform.Screen.NONE : Platform.Screen.TITLE);
            return true;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, px + 4, tabY(i), SIDE_W - 8, TAB_H)) {
                tab = TABS[i];
                scroll = 0;
                smoothScroll = 0;
                return true;
            }
        }
        if (in(mx, my, editX, editY, editW, editH)) {
            editingHud = true;
            return true;
        }
        if (in(mx, my, listX, listY, listW, listH)) {
            List<Module> mods = visible();
            for (int i = 0; i < mods.size(); i++) {
                if (in(mx, my, listX, rowY(i), listW, ROW_H)) {
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
        scroll -= (int) Math.round(amount * (ROW_H + 2));
        scroll = clamp(scroll, 0, maxScroll);
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
