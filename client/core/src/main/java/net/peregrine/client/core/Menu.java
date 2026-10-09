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
    private final HudEditor editor;
    private float k = 1f;        // the menu is drawn at this fraction of the GUI scale, for finer detail
    private int lastMx, lastMy;  // mouse position from the last frame (screen pixels)
    private long openedAt;
    private long lastFrame;
    private final Map<Module, Float> knob = new IdentityHashMap<Module, Float>();
    private final Map<Module, Float> rowGlow = new IdentityHashMap<Module, Float>();
    private float tabSlide = -1;  // where the category highlight is, sliding to the chosen one

    // Layout from the last render, used for clicks.
    private int px, py, pw, ph, listX, listY, listW, listH;
    private int editX, editY, editW, editH;
    private int doneX, doneY, doneW, doneH;
    private int closeX, closeY;
    private int searchX, searchY, searchW;

    Menu(Peregrine pc) {
        this.pc = pc;
        this.editor = new HudEditor(pc);
    }

    /**
     * How much smaller than Minecraft's GUI the menu is drawn. At GUI scale 3 or
     * more it uses one step down (e.g. 3 -> 2), which keeps the pixel font sharp
     * while making text and buttons smaller and tidier.
     */
    static float uiScale(Platform p) {
        long g = Math.round(p.guiScale());
        return g >= 3 ? (g - 1f) / g : 1f;
    }

    private int s(int ui) {
        return Math.round(ui * k);
    }

    /** Call when the menu screen opens. */
    public void open() {
        openedAt = System.currentTimeMillis();
        lastFrame = 0;
        search = "";
        scroll = 0;
        smoothScroll = 0;
        editingHud = false;
        editor.open();
        knob.clear();
        rowGlow.clear();
        tabSlide = -1;
    }

    // ---- for the self-test (see SelfTest)

    boolean editingHud() {
        return editingHud;
    }

    String search() {
        return search;
    }

    HudEditor editor() {
        return editor;
    }

    /** Centre of something on screen, in GUI pixels, or null if it isn't showing. */
    int[] centerOf(String what) {
        if (editingHud) {
            return editor.centerOf(what);
        }
        layout(pc.platform());
        if (what.equals("edit")) {
            return editW > 0 ? new int[] {s(editX + editW / 2), s(editY + editH / 2)} : null;
        }
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            if (mods.get(i).id.equals(what)) {
                int y = rowY(i) + ROW_H / 2;
                return y > listY && y < listY + listH ? new int[] {s(listX + listW / 2), s(y)} : null;
            }
        }
        return null;
    }

    /** Call when the menu screen closes. */
    public void close() {
        editor.mouseReleased();
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
        int sw = Math.round(p.screenWidth() / k);
        int sh = Math.round(p.screenHeight() / k);
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

    public void render(Draw raw, int screenMx, int screenMy) {
        Platform p = pc.platform();
        k = uiScale(p);
        lastMx = screenMx;
        lastMy = screenMy;
        layout(p);
        SelfTest.count(editingHud ? "editor" : "menu");
        long now = System.currentTimeMillis();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (editingHud) {
            editor.render(raw, screenMx, screenMy, k);
            return;
        }
        float t = fade();
        Draw d = t < 1f ? new FadeDraw(raw, t) : raw;
        d.pushScale(0, 0, k);
        renderMenu(d, Math.round(screenMx / k), Math.round(screenMy / k), p, t, dt, now);
        d.popScale();
    }

    private void renderMenu(Draw d, int mx, int my, Platform p, float t, float dt, long now) {
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
        // Slightly darker towards the bottom, for depth.
        for (int b = 0; b < 4; b++) {
            int bh = (ph - TITLE_H) / 4;
            d.rect(px + 1, py + TITLE_H + b * bh, pw - 2, bh, Theme.withAlpha(0, 0x06 * b));
        }
        d.rect(px + 2, py, pw - 4, 1, Theme.withAlpha(a, 0xE0));
        d.rect(px + 1, py + 1, pw - 2, 1, Theme.withAlpha(a, 0x50));
        d.rect(px + 2, py + 2, pw - 4, 1, 0x10FFFFFF);  // a hairline of light along the top edge

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
        magnifier(d, searchX + 5, searchY + 4, searching ? a : Theme.FAINT);
        d.text(d.trim(shown, searchW - 20), searchX + 15, searchY + 4, searching ? Theme.TEXT : Theme.FAINT, false);

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
        int chosen = 0;
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i] == tab) {
                chosen = i;
            }
        }
        float target = tabY(chosen);
        tabSlide = tabSlide < 0 ? target : tabSlide + (target - tabSlide) * Math.min(1f, dt * 16f);
        int sy = Math.round(tabSlide);
        d.roundRect(px + 4, sy, SIDE_W - 8, TAB_H, Theme.withAlpha(a, 0x2C));
        d.rect(px + 4, sy + 4, 2, TAB_H - 8, a);
        for (int i = 0; i < TABS.length; i++) {
            int y = tabY(i);
            boolean on = TABS[i] == tab;
            boolean hover = in(mx, my, px + 4, y, SIDE_W - 8, TAB_H);
            if (!on && hover) {
                d.roundRect(px + 4, y, SIDE_W - 8, TAB_H, Theme.ROW_HOVER);
            }
            String label = TABS[i] == null ? "All" : TABS[i].label;
            d.roundRect(px + 11, y + 7, 4, 4, on ? a : hover ? Theme.MUTED : Theme.FAINT);
            d.text(label, px + 19, y + 5, on ? Theme.TEXT : hover ? Theme.TEXT : Theme.MUTED, false);
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

        // Clipping works in screen pixels on every version, so step out of the scale for it.
        d.popScale();
        d.clip(s(listX), s(listY), s(listW), s(listH));
        d.pushScale(0, 0, k);
        boolean mouseInList = in(mx, my, listX, listY, listW, listH);
        int textW = listW - 44;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int ry = rowY(i);
            if (ry + ROW_H < listY || ry > listY + listH) {
                continue;
            }
            boolean hover = mouseInList && in(mx, my, listX, ry, listW, ROW_H);
            float g = ease(rowGlow, m, hover ? 1f : 0f, dt * 10f);
            if (g > 0.01f) {
                d.roundRect(listX, ry, listW, ROW_H, Theme.withAlpha(0xFFFFFF, Math.round(0x14 * g)));
            }
            float on = knob.containsKey(m) ? knob.get(m) : (m.enabled() ? 1f : 0f);
            if (on > 0.01f) {  // a thin accent bar marks what's switched on
                d.rect(listX + 1, ry + 6, 2, ROW_H - 12, Theme.withAlpha(a, Math.round(0xFF * on)));
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
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);

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
        int cw = d.width(counts) + 10;
        d.roundRect(px + pw - 9 - cw, fy - 3, cw, 13, Theme.withAlpha(a, 0x22));
        d.text(counts, px + pw - 4 - cw, fy, Theme.withAlpha(a, 0xFF), false);
    }

    /** Eases a per-row value towards a target (for smooth hover highlights). */
    private static float ease(Map<Module, Float> map, Module m, float target, float step) {
        Float cur = map.get(m);
        float v = cur == null ? 0f : cur;
        v = v < target ? Math.min(target, v + step) : Math.max(target, v - step);
        map.put(m, v);
        return v;
    }

    /** A tiny magnifying glass, drawn pixel by pixel so it stays crisp. */
    private static void magnifier(Draw d, int x, int y, int c) {
        d.rect(x + 1, y, 3, 1, c);
        d.rect(x + 1, y + 4, 3, 1, c);
        d.rect(x, y + 1, 1, 3, c);
        d.rect(x + 4, y + 1, 1, 3, c);
        d.rect(x + 4, y + 4, 1, 1, c);
        d.rect(x + 5, y + 5, 1, 1, c);
        d.rect(x + 6, y + 6, 1, 1, c);
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
        if (on > 0.05f) {  // a soft glow around a switch that's on
            d.roundOutline(x - 1, y - 1, 24, 12, Theme.withAlpha(accent, Math.round(0x50 * on)));
        }
        d.roundRect(x, y, 22, 10, track);
        d.rect(x + 2, y + 1, 18, 1, Theme.withAlpha(0xFFFFFF, Math.round(0x18 + 0x18 * on)));  // sheen
        int kx = x + 2 + Math.round(on * 10);
        d.rect(kx + 1, y + 8, 6, 1, 0x40000000);  // the knob's shadow
        d.rect(kx + 1, y + 2, 6, 6, 0xFFFFFFFF);
        d.rect(kx, y + 3, 8, 4, 0xFFFFFFFF);
    }

    // -------------------------------------------------------------- input

    /** Returns true if the click was used. button: 0 = left, 1 = right. Screen pixels. */
    public boolean mouseClicked(int screenMx, int screenMy, int button) {
        SelfTest.count("click@" + screenMx + "," + screenMy + "/" + button);
        Platform p = pc.platform();
        if (editingHud) {
            editor.mouseClicked(screenMx, screenMy, button);
            if (editor.wantsExit()) {
                editingHud = false;
                pc.markDirty();
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        int mx = Math.round(screenMx / k);
        int my = Math.round(screenMy / k);
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
            editor.open();
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

    public boolean mouseDragged(int screenMx, int screenMy) {
        return editingHud && editor.mouseDragged(screenMx, screenMy);
    }

    public boolean mouseReleased() {
        return editingHud && editor.mouseReleased();
    }

    /** amount: positive = scroll up. */
    public boolean mouseScrolled(double amount) {
        if (editingHud) {
            return editor.mouseScrolled(amount, lastMx, lastMy);
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
