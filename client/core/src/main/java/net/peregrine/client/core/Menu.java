package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.peregrine.client.core.settings.ActionSetting;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.Setting;
import net.peregrine.client.core.settings.SliderSetting;

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

    private static final int TITLE_H = 28;
    private static final int TABBAR_H = 18;
    private static final int FOOTER_H = 0;
    private static final int SIDE_W = 96;
    private static final int TILE_GAP = 6;
    /** The three views along the top: the mods grid, client settings, every keybind. */
    private static final String[] VIEWS = {"Mods", "Settings", "Keybinds"};
    private static final int CARD_H = 50;
    private static final int GAP = 5;
    private static final int TAB_H = 18;
    private static final Module.Category[] TABS = {
        null, Module.Category.HUD, Module.Category.UTILITY, Module.Category.VISUALS, Module.Category.PERFORMANCE
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
    private Module page;          // the feature whose settings page is open, or null for the list
    private long pageOpenedAt;
    private SliderSetting draggingSlider;
    private final Map<Setting, Float> settingKnob = new IdentityHashMap<Setting, Float>();
    private Module binding;       // waiting for a key for this feature's keybind
    private long boundAt;         // when a key was just bound (its typed character is swallowed)
    private int cols = 2;
    private int tileW = 80, tileH = 76;
    private long listShownAt;
    private int view;             // 0 mods, 1 settings, 2 keybinds
    private boolean onlyOn;       // the ≡ button: show only what's switched on
    private int tabsY, tabX0, tabW, menuBtnX;

    /** Something clickable on a settings page, from the last frame (menu pixels). */
    private static final class Hit {
        final String id;
        final int x, y, w, h;
        final Setting setting;

        Hit(String id, int x, int y, int w, int h, Setting setting) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.setting = setting;
        }
    }

    private final List<Hit> hits = new ArrayList<Hit>();

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
        listShownAt = openedAt;
        binding = null;
        lastFrame = 0;
        search = "";
        scroll = 0;
        smoothScroll = 0;
        editingHud = false;
        editor.open();
        knob.clear();
        rowGlow.clear();
        tabSlide = -1;
        page = null;
        draggingSlider = null;
        hits.clear();
    }

    /** Opens a feature's settings page (the gear on its row, or right-click). */
    void openPage(Module m) {
        page = m;
        pageOpenedAt = System.currentTimeMillis();
        scroll = 0;
        smoothScroll = 0;
        hits.clear();
        settingKnob.clear();
    }

    public Module page() {
        return page;
    }

    /** Every feature has a page: its keybind, plus options and (for HUD items) its look. */
    static boolean hasPage(Module m) {
        return true;
    }

    public Module binding() {
        return binding;
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
        if (what.startsWith("view:")) {
            int i = java.util.Arrays.asList(VIEWS).indexOf(what.substring(5));
            return i < 0 ? null : new int[] {s(tabX0 + i * (tabW + 4) + tabW / 2), s(tabsY + TABBAR_H / 2)};
        }
        if (page != null || view != 0) {
            for (Hit h : hits) {
                if (h.id.equals(what)) {
                    return new int[] {s(h.x + h.w / 2), s(h.y + h.h / 2)};
                }
            }
            return null;
        }
        List<Module> mods = visible();
        for (int i = 0; i < mods.size(); i++) {
            int[] c = card(i);
            if (what.equals("gear:" + mods.get(i).id)) {  // the tile itself opens its options
                int y = c[1] + tileH / 2;
                return y > listY && y < listY + listH ? new int[] {s(c[0] + c[2] / 2), s(y)} : null;
            }
            if (mods.get(i).id.equals(what)) {  // its switch
                int[] tg = toggleAt(c);
                int y = tg[1] + 5;
                return y > listY && y < listY + listH ? new int[] {s(tg[0] + 12), s(y)} : null;
            }
        }
        return null;
    }

    /** Call when the menu screen closes. */
    public void close() {
        binding = null;
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
            if ((tab == null || m.category == tab) && m.matches(search) && pc.platform().supports(m.id)
                    && (!onlyOn || m.enabled() || view != 0)) {
                out.add(m);
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Module>() {  // A to Z, like a shelf
            public int compare(Module x, Module y) {
                return x.name.compareToIgnoreCase(y.name);
            }
        });
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

    /** A large window in the middle of the screen, never edge to edge. */
    private void layout(Platform p) {
        int sw = Math.round(p.screenWidth() / k);
        int sh = Math.round(p.screenHeight() / k);
        pw = Math.min(sw - 16, clamp(Math.round(sw * 0.72f), 330, 540));
        ph = Math.min(sh - 16, clamp(Math.round(sh * 0.80f), 210, 330));
        px = (sw - pw) / 2;
        py = (sh - ph) / 2;
        tabsY = py + TITLE_H + 4;
        int bodyY = tabsY + TABBAR_H + 6;
        listX = px + SIDE_W + 14;
        listY = bodyY;
        listW = pw - SIDE_W - 24;
        listH = py + ph - 8 - bodyY;
        cols = Math.max(2, (listW + TILE_GAP) / (78 + TILE_GAP));
        tileW = (listW - TILE_GAP * (cols - 1)) / cols;
        tileH = clamp(Math.round(tileW * 0.9f), 64, 86);
        searchW = clamp(Math.round(pw * 0.24f), 80, 130);
        searchX = px + pw - 6 - searchW;
        searchY = tabsY;
        menuBtnX = searchX - 4 - TABBAR_H;
        tabX0 = px + 6;
        tabW = Math.min(96, (menuBtnX - 6 - tabX0 - 4 * (VIEWS.length - 1)) / VIEWS.length);
        closeX = px + pw - 22;
        closeY = py + 6;
        editW = SIDE_W;
        editH = 17;
        editX = px + 6;
        editY = py + ph - 8 - editH;
    }

    /** Tile i of the grid: x, y, width (menu pixels, scrolled). */
    private int[] card(int i) {
        int col = i % cols, row = i / cols;
        return new int[] {listX + col * (tileW + TILE_GAP), listY + row * (tileH + TILE_GAP) - Math.round(smoothScroll), tileW};
    }

    /** A tile's switch: x, y (24 x 11), centred along its bottom. */
    private int[] toggleAt(int[] c) {
        return new int[] {c[0] + (c[2] - 24) / 2, c[1] + tileH - 17};
    }

    private int tabY(int i) {
        return listY + 14 + i * (TAB_H + 3);
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
            tabsY += lift;
        }
        int a = pc.accent();

        // Window: one dark panel, a slightly lighter title strip, a thin accent line on top
        d.shadow(px, py, pw, ph);
        d.roundRect(px, py, pw, ph, Theme.PANEL);
        d.roundOutline(px, py, pw, ph, Theme.HAIRLINE);
        d.rect(px + 2, py, pw - 4, 1, Theme.withAlpha(a, 0xE0));
        d.rect(px + 1, py + 1, pw - 2, TITLE_H - 1, Theme.TITLE_BAR);

        // Title: logo and name; a boxed × on the right
        int ty = py + (TITLE_H - 8) / 2;
        d.textScaled(Hooks.LOGO, px + 8, ty - 3, 0xFFFFFFFF, 1.5f, false);
        d.text("§lPEREGRINE", px + 25, ty, Theme.TEXT, false);
        d.text("CLIENT", px + 25 + d.width("§lPEREGRINE") + 4, ty, a, false);
        boolean closeHover = in(mx, my, closeX, closeY, 16, 16);
        d.roundRect(closeX, closeY, 16, 16, closeHover ? Theme.withAlpha(Theme.BAD, 0xC0) : Theme.FIELD);
        d.roundOutline(closeX, closeY, 16, 16, closeHover ? Theme.BAD : Theme.HAIRLINE);
        int xc = closeHover ? 0xFFFFFFFF : Theme.MUTED;
        for (int i = 0; i < 7; i++) {  // a small drawn ×, crisp at any GUI scale
            d.rect(closeX + 4 + i, closeY + 4 + i, 1, 1, xc);
            d.rect(closeX + 10 - i, closeY + 4 + i, 1, 1, xc);
        }

        // Tabs along the top, then the ≡ button and search on the right
        for (int i = 0; i < VIEWS.length; i++) {
            int x = tabX0 + i * (tabW + 4);
            boolean on = view == i;
            boolean hover = in(mx, my, x, tabsY, tabW, TABBAR_H);
            d.roundRect(x, tabsY, tabW, TABBAR_H, on ? a : hover ? Theme.CARD_HOVER : Theme.CARD);
            if (!on) {
                d.roundOutline(x, tabsY, tabW, TABBAR_H, Theme.HAIRLINE);
            }
            d.text(VIEWS[i], x + (tabW - d.width(VIEWS[i])) / 2, tabsY + 5, on ? 0xFF15171C : hover ? Theme.TEXT : Theme.MUTED, false);
        }
        boolean mh = in(mx, my, menuBtnX, tabsY, TABBAR_H, TABBAR_H);
        d.roundRect(menuBtnX, tabsY, TABBAR_H, TABBAR_H, onlyOn ? Theme.withAlpha(a, 0x50) : mh ? Theme.CARD_HOVER : Theme.CARD);
        d.roundOutline(menuBtnX, tabsY, TABBAR_H, TABBAR_H, onlyOn ? a : Theme.HAIRLINE);
        for (int i = 0; i < 3; i++) {  // ≡ : show only what's on
            d.rect(menuBtnX + 5, tabsY + 5 + i * 3, 8, 1, onlyOn ? a : mh ? Theme.TEXT : Theme.MUTED);
        }
        boolean searching = !search.isEmpty();
        d.roundRect(searchX, searchY, searchW, TABBAR_H, Theme.FIELD);
        d.roundOutline(searchX, searchY, searchW, TABBAR_H, searching ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
        boolean caret = (now / 500) % 2 == 0;
        String shown = searching ? search + (caret ? "_" : "") : "Search...";
        magnifier(d, searchX + 6, searchY + 6, searching ? a : Theme.FAINT);
        d.text(d.trim(shown, searchW - 22), searchX + 16, searchY + 5, searching ? Theme.TEXT : Theme.FAINT, false);

        // Sidebar: "Filter", the categories with their icons, Edit HUD at the bottom
        int sideTop = listY;
        d.roundRect(px + 6, sideTop, SIDE_W, py + ph - 8 - sideTop, Theme.SIDEBAR);
        String filter = "Filter";
        d.text(filter, px + 6 + (SIDE_W - d.width(filter)) / 2, sideTop + 4, Theme.MUTED, false);
        int chosen = 0;
        for (int i = 0; i < TABS.length; i++) {
            if (TABS[i] == tab) {
                chosen = i;
            }
        }
        float target = tabY(chosen);
        tabSlide = tabSlide < 0 ? target : tabSlide + (target - tabSlide) * Math.min(1f, dt * 16f);
        if (view == 0) {
            d.roundRect(px + 10, Math.round(tabSlide), SIDE_W - 8, TAB_H, a);
        }
        for (int i = 0; i < TABS.length; i++) {
            int y = tabY(i);
            boolean on = view == 0 && TABS[i] == tab;
            boolean hover = in(mx, my, px + 10, y, SIDE_W - 8, TAB_H);
            if (!on && hover) {
                d.roundRect(px + 10, y, SIDE_W - 8, TAB_H, Theme.ROW_HOVER);
            }
            String label = TABS[i] == null ? "All" : TABS[i].label;
            int c = on ? 0xFF15171C : hover ? Theme.TEXT : Theme.MUTED;
            categoryIcon(d, TABS[i], px + 15, y + 5, c);
            d.text(d.trim(label, SIDE_W - 30), px + 28, y + 5, c, false);
        }
        boolean editHover = in(mx, my, editX, editY, editW, editH);
        d.roundRect(editX, editY, editW, editH, editHover ? a : Theme.CARD);
        d.roundOutline(editX, editY, editW, editH, editHover ? a : Theme.HAIRLINE);
        String edit = "Edit HUD";
        d.text(edit, editX + (editW - d.width(edit)) / 2, editY + 5, editHover ? 0xFF15171C : Theme.TEXT, false);

        if (page != null) {
            renderPage(d, mx, my, p, dt, now, a);
            return;
        }
        if (view == 1) {
            renderSettingsView(d, mx, my, dt, a);
            return;
        }
        if (view == 2) {
            renderKeybindView(d, mx, my, dt, now, a);
            return;
        }

        // Mod tiles
        List<Module> mods = visible();
        int rows = (mods.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (tileH + TILE_GAP) - TILE_GAP - listH);
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
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int[] c = card(i);
            int cx = c[0], cy = c[1], cw = c[2];
            if (cy + tileH < listY || cy > listY + listH) {
                continue;
            }
            // Tiles fade in one after another when the menu (or a new category) opens.
            float appear = 0.15f + 0.85f * FadeDraw.progress(listShownAt + Math.min(i, 16) * 12L, 170);
            Draw cd = appear < 1f ? new FadeDraw(d, appear) : d;
            if (appear < 1f) {
                cy += Math.round((1f - appear) * 4);
                c = new int[] {cx, cy, cw};
            }
            boolean hover = mouseInList && in(mx, my, cx, cy, cw, tileH);
            float g = ease(rowGlow, m, hover ? 1f : 0f, dt * 10f);
            float on = animate(m, dt);
            cd.roundRect(cx, cy, cw, tileH, Theme.mix(Theme.CARD, Theme.CARD_HOVER, g));
            cd.roundOutline(cx, cy, cw, tileH, Theme.mix(Theme.withAlpha(0xFFFFFF, 0x30), a, g * 0.9f));
            cd.rect(cx + 2, cy + 1, cw - 4, 1, Theme.withAlpha(0xFFFFFF, 0x10 + Math.round(0x10 * g)));  // top sheen

            boolean warn = hover && m.warning() != null;
            String title = cd.trim(m.name, cw - 10);
            cd.text(title, cx + (cw - d.width(title)) / 2, cy + 7, warn ? Theme.OK : Theme.TEXT, false);
            if (m.key >= 0) {  // its keybind, as a small key cap in the corner
                String kn = Keys.name(m.key);
                int kw = d.width(kn) + 6;
                cd.roundRect(cx + cw - 4 - kw, cy + tileH - 30, kw, 10, Theme.FIELD);
                cd.text(kn, cx + cw - 1 - kw, cy + tileH - 29, Theme.MUTED, false);
            }
            // The icon: a Minecraft item that fits the feature, drawn twice the size
            int iconY = cy + 18 + (tileH - 18 - 20 - 32) / 2;
            Object stack = p.icon(Icons.of(m));
            if (stack != null) {
                cd.pushScale(cx + cw / 2 - 16, iconY, 2f);
                cd.item(stack, 0, 0);
                cd.popScale();
            } else {
                cd.roundRect(cx + cw / 2 - 12, iconY + 4, 24, 24, Theme.withAlpha(a, 0x40));
                categoryIcon(cd, m.category, cx + cw / 2 - 4, iconY + 12, Theme.TEXT);
            }
            int[] tg = toggleAt(c);
            switchKnob(cd, tg[0], tg[1], on, a, hover && in(mx, my, tg[0] - 2, tg[1] - 2, 28, 15));
        }
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);

        if (mods.isEmpty()) {
            String none = onlyOn && search.isEmpty() ? "Nothing switched on here" : "Nothing matches \"" + d.trim(search, listW - 80) + "\"";
            d.text(none, listX + (listW - d.width(none)) / 2, listY + listH / 2 - 8, Theme.MUTED, false);
            String hint = onlyOn ? "The ≡ button shows everything again" : "Backspace to clear the search";
            d.text(hint, listX + (listW - d.width(hint)) / 2, listY + listH / 2 + 4, Theme.FAINT, false);
        }
        scrollbar(d, listY, listH, in(mx, my, listX, listY, listW + 8, listH), a);
    }

    private void scrollbar(Draw d, int top, int viewH, boolean active, int a) {
        if (maxScroll <= 0) {
            return;
        }
        int track = viewH - 4;
        int barH = Math.max(14, track * viewH / (viewH + maxScroll));
        int barY = top + 2 + Math.round((track - barH) * (smoothScroll / maxScroll));
        d.roundRect(px + pw - 8, top + 2, 3, track, 0x18FFFFFF);
        d.roundRect(px + pw - 8, barY, 3, barH, active ? a : Theme.withAlpha(a, 0x90));
    }

    /**
     * An on/off switch with a round knob: off is grey with a cross in the knob,
     * on is the accent color with a tick. 24 x 11.
     */
    private static void switchKnob(Draw d, int x, int y, float on, int accent, boolean hover) {
        int track = Theme.mix(hover ? 0xFF4A505E : 0xFF3A3F4B, accent, on);
        d.roundRect(x, y, 24, 11, track);
        int kx = x + 1 + Math.round(on * 13);
        d.rect(kx + 1, y + 1, 8, 9, 0xFFFFFFFF);
        d.rect(kx, y + 2, 10, 7, 0xFFFFFFFF);
        int gc = on > 0.5f ? accent : 0xFF4A505E;
        if (on > 0.5f) {  // ✓
            d.rect(kx + 2, y + 5, 1, 1, gc);
            d.rect(kx + 3, y + 6, 1, 1, gc);
            d.rect(kx + 4, y + 7, 1, 1, gc);
            d.rect(kx + 5, y + 6, 1, 1, gc);
            d.rect(kx + 6, y + 5, 1, 1, gc);
            d.rect(kx + 7, y + 4, 1, 1, gc);
            d.rect(kx + 2, y + 6, 1, 1, gc);
            d.rect(kx + 4, y + 6, 1, 1, gc);
            d.rect(kx + 6, y + 4, 1, 1, gc);
        } else {  // ×
            for (int i = 0; i < 5; i++) {
                d.rect(kx + 3 + i, y + 3 + i, 1, 1, gc);
                d.rect(kx + 7 - i, y + 3 + i, 1, 1, gc);
            }
        }
    }

    /** Small pixel icons for the filter list (9 x 9). */
    private static void categoryIcon(Draw d, Module.Category c, int x, int y, int col) {
        if (c == null) {  // All: three lines
            for (int i = 0; i < 3; i++) {
                d.rect(x, y + i * 3, 9, 2, col);
            }
        } else if (c == Module.Category.HUD) {  // a screen
            d.outline(x, y, 9, 7, col);
            d.rect(x + 2, y + 2, 3, 1, col);
            d.rect(x + 2, y + 4, 5, 1, col);
            d.rect(x + 3, y + 8, 3, 1, col);
        } else if (c == Module.Category.UTILITY) {  // a wrench
            d.rect(x + 1, y, 3, 1, col);
            d.rect(x, y + 1, 1, 3, col);
            d.rect(x + 4, y + 1, 1, 2, col);
            d.rect(x + 1, y + 4, 2, 1, col);
            for (int i = 0; i < 5; i++) {
                d.rect(x + 3 + i, y + 3 + i, 2, 1, col);
            }
        } else if (c == Module.Category.VISUALS) {  // an eye
            d.rect(x + 2, y + 1, 5, 1, col);
            d.rect(x + 1, y + 2, 1, 1, col);
            d.rect(x + 7, y + 2, 1, 1, col);
            d.rect(x, y + 3, 1, 3, col);
            d.rect(x + 8, y + 3, 1, 3, col);
            d.rect(x + 1, y + 6, 1, 1, col);
            d.rect(x + 7, y + 6, 1, 1, col);
            d.rect(x + 2, y + 7, 5, 1, col);
            d.rect(x + 3, y + 3, 3, 3, col);
        } else {  // Performance: a lightning bolt
            d.rect(x + 4, y, 3, 1, col);
            d.rect(x + 3, y + 1, 3, 1, col);
            d.rect(x + 2, y + 2, 3, 1, col);
            d.rect(x + 1, y + 3, 6, 1, col);
            d.rect(x + 4, y + 4, 2, 1, col);
            d.rect(x + 3, y + 5, 2, 1, col);
            d.rect(x + 2, y + 6, 2, 1, col);
            d.rect(x + 2, y + 7, 1, 1, col);
        }
    }

    // ------------------------------------------------------- Settings and Keybinds views

    /** Accent colors to pick from in Settings. */
    static final int[] ACCENTS = {
        Theme.AMBER, 0xFF4FB3FF, 0xFF6BE38B, 0xFFFF6B6B, 0xFFB57BFF, 0xFFFF7AD9, 0xFF5CE1E6, 0xFFFFE066, 0xFFEDEEF2,
    };

    private void renderSettingsView(Draw d, int mx, int my, float dt, int a) {
        hits.clear();
        int x = listX, w = listW, y = listY;
        maxScroll = 0;
        scroll = 0;
        smoothScroll = 0;
        heading(d, "Look", x, y);
        y += 14;
        d.text("Accent color", x + 6, y + 7, Theme.MUTED, false);
        int sx = x + w - 6 - ACCENTS.length * 14 + 2;
        for (int i = 0; i < ACCENTS.length; i++) {
            int cx = sx + i * 14;
            boolean chosen = (ACCENTS[i] & 0xFFFFFF) == (a & 0xFFFFFF);
            boolean hv = in(mx, my, cx - 1, y + 4, 12, 12);
            if (chosen || hv) {
                d.roundOutline(cx - 2, y + 3, 14, 14, chosen ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            d.roundRect(cx, y + 5, 10, 10, ACCENTS[i]);
            hit("accent:" + i, cx - 1, y + 4, 12, 12, null);
        }
        y += SET_H + 4;
        y = moduleRow(d, mx, my, dt, a, "main_menu", "Peregrine main menu", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "clean_menus", "Keep resource packs out of menus", x, y, w);
        y += 8;
        heading(d, "Reset", x, y);
        y += 16;
        y = actionRow(d, mx, my, a, "reset-hud", "HUD layout", "Reset positions & looks", x, y, w);
        y = actionRow(d, mx, my, a, "clear-keys", "Keybinds", "Clear them all", x, y, w);
    }

    private void heading(Draw d, String text, int x, int y) {
        d.text(text.toUpperCase(), x + 2, y + 2, Theme.FAINT, false);
        d.rect(x + 4 + d.width(text.toUpperCase()), y + 6, listW - 8 - d.width(text.toUpperCase()), 1, Theme.HAIRLINE);
    }

    private int moduleRow(Draw d, int mx, int my, float dt, int a, String id, String label, int x, int y, int w) {
        Module m = pc.module(id);
        if (m == null || !pc.platform().supports(id)) {
            return y;
        }
        boolean rh = in(mx, my, x, y, w, SET_H);
        if (rh) {
            d.roundRect(x, y, w, SET_H, Theme.ROW_HOVER);
        }
        d.text(label, x + 6, y + 7, rh ? Theme.TEXT : Theme.MUTED, false);
        switchKnob(d, x + w - 30, y + 5, animate(m, dt), a, rh);
        hit("module:" + id, x, y, w, SET_H, null);
        return y + SET_H + 2;
    }

    private int actionRow(Draw d, int mx, int my, int a, String id, String label, String button, int x, int y, int w) {
        d.text(label, x + 6, y + 7, Theme.MUTED, false);
        int bw = d.width(button) + 16;
        int bx = x + w - 6 - bw;
        boolean bh = in(mx, my, bx, y + 3, bw, 16);
        d.roundRect(bx, y + 3, bw, 16, bh ? Theme.withAlpha(Theme.BAD, 0x50) : Theme.CARD);
        d.roundOutline(bx, y + 3, bw, 16, bh ? Theme.BAD : Theme.HAIRLINE);
        d.text(button, bx + 8, y + 7, bh ? Theme.TEXT : Theme.MUTED, false);
        hit(id, bx, y + 3, bw, 16, null);
        return y + SET_H + 2;
    }

    /** Every feature with its keybind, to set them all in one place. */
    private void renderKeybindView(Draw d, int mx, int my, float dt, long now, int a) {
        hits.clear();
        List<Module> mods = visible();
        int rowH = 18;
        maxScroll = Math.max(0, mods.size() * rowH - listH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }
        d.popScale();
        d.clip(s(listX), s(listY), s(listW), s(listH));
        d.pushScale(0, 0, k);
        int y = listY - Math.round(smoothScroll);
        for (Module m : mods) {
            if (y + rowH >= listY && y <= listY + listH) {
                boolean waiting = binding == m;
                boolean rh = in(mx, my, listX, y, listW, rowH) && in(mx, my, listX, listY, listW, listH);
                if (rh || waiting) {
                    d.roundRect(listX, y, listW, rowH - 1, waiting ? Theme.withAlpha(a, 0x20) : Theme.ROW_HOVER);
                }
                Object stack = pc.platform().icon(Icons.of(m));
                if (stack != null) {
                    d.item(stack, listX + 3, y + 1);
                }
                d.text(d.trim(m.name, listW - 100), listX + 23, y + 5, rh ? Theme.TEXT : Theme.MUTED, false);
                String val = waiting ? "Press a key…" : Keys.name(m.key);
                int bw = Math.max(44, d.width(val) + 12);
                int bx = listX + listW - 6 - bw;
                float pulse = waiting ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
                d.roundRect(bx, y + 2, bw, rowH - 5, waiting ? Theme.withAlpha(a, 0x30 + Math.round(0x30 * pulse)) : Theme.FIELD);
                d.roundOutline(bx, y + 2, bw, rowH - 5, waiting ? a : Theme.HAIRLINE);
                d.text(val, bx + (bw - d.width(val)) / 2, y + 5, waiting ? a : m.key >= 0 ? Theme.TEXT : Theme.FAINT, false);
                if (y + rowH / 2 >= listY && y + rowH / 2 <= listY + listH) {
                    hit("bindrow:" + m.id, bx, y + 2, bw, rowH - 5, null);
                }
            }
            y += rowH;
        }
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);
        scrollbar(d, listY, listH, in(mx, my, listX, listY, listW + 8, listH), a);
    }

    // ------------------------------------------------------- settings page

    private static final int SET_H = 22;

    private void hit(String id, int x, int y, int w, int h, Setting setting) {
        hits.add(new Hit(id, x, y, w, h, setting));
    }

    private void renderPage(Draw d, int mx, int my, Platform p, float dt, long now, int a) {
        Module m = page;
        hits.clear();
        float t = FadeDraw.progress(pageOpenedAt, 160);
        float e = 1f - (1f - t) * (1f - t);
        int ox = Math.round((1f - e) * 8);  // slides in from the right a little
        int x = listX + ox;
        int w = listW;

        // Header: back, name and description, the feature's own switch
        boolean bh = in(mx, my, x, listY + 3, 16, 16);
        d.roundRect(x, listY + 3, 16, 16, bh ? Theme.withAlpha(a, 0x40) : Theme.FIELD);
        d.roundOutline(x, listY + 3, 16, 16, bh ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
        arrowLeft(d, x + 6, listY + 7, bh ? Theme.TEXT : Theme.MUTED);
        hit("back", x, listY + 3, 16, 16, null);
        d.text(d.trim(m.name, w - 66), x + 22, listY + 2, Theme.TEXT, false);
        d.text(d.trim(m.description, w - 66), x + 22, listY + 13, Theme.FAINT, false);
        boolean th = in(mx, my, x + w - 30, listY + 6, 22, 10);
        switchKnob(d, x + w - 32, listY + 6, animate(m, dt), a, th);
        hit("toggle", x + w - 32, listY + 3, 26, 16, null);
        d.rect(x, listY + 25, w - 4, 1, Theme.HAIRLINE);

        int top = listY + 28;
        int viewH = listH - 28;
        int contentH = SET_H + 6;  // the keybind row
        if (m instanceof HudModule) {
            contentH += 40;
        }
        contentH += m.settings().size() * (SET_H + 1);
        if (!m.settings().isEmpty()) {
            contentH += 24;
        }
        maxScroll = Math.max(0, contentH - viewH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }

        d.popScale();
        d.clip(s(listX), s(top), s(listW), s(viewH));
        d.pushScale(0, 0, k);
        boolean inView = in(mx, my, listX, top, listW, viewH);
        int y = top + 2 - Math.round(smoothScroll);

        // Keybind: a key that switches this feature while playing.
        {
            boolean waiting = binding == m;
            boolean rh = inView && in(mx, my, x, y, w - 4, SET_H);
            if (rh || waiting) {
                d.roundRect(x, y, w - 4, SET_H, Theme.withAlpha(waiting ? a : 0xFFFFFF, waiting ? 0x18 : 0x0E));
            }
            d.text("Keybind", x + 6, y + 7, rh || waiting ? Theme.TEXT : Theme.MUTED, false);
            String hint = waiting ? "Esc to cancel, Backspace for none" : "Press it in game to switch on or off";
            d.text(d.trim(hint, w / 2 - 20), x + 6 + d.width("Keybind  "), y + 7, Theme.FAINT, false);
            String val = waiting ? "Press a key\u2026" : Keys.name(m.key);
            int bw2 = Math.max(44, d.width(val) + 16);
            int right = x + w - 8;
            int clearW = !waiting && m.key >= 0 ? 14 : 0;
            int bx = right - bw2 - clearW;
            boolean bhv = inView && in(mx, my, bx, y + 3, bw2, 16);
            float pulse = waiting ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
            d.roundRect(bx, y + 3, bw2, 16, waiting ? Theme.withAlpha(a, 0x30 + Math.round(0x30 * pulse))
                    : bhv ? Theme.ROW_HOVER : Theme.FIELD);
            d.roundOutline(bx, y + 3, bw2, 16, waiting || bhv ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
            d.text(val, bx + (bw2 - d.width(val)) / 2, y + 7, waiting ? a : m.key >= 0 ? Theme.TEXT : Theme.FAINT, false);
            hit("bind", bx, y + 3, bw2, 16, null);
            if (clearW > 0) {
                int cx = right - 9, cy = y + 8;
                boolean chv = inView && in(mx, my, cx - 3, cy - 3, 12, 12);
                int xc = chv ? Theme.TEXT : Theme.FAINT;
                for (int i = 0; i < 5; i++) {
                    d.rect(cx + i, cy + i, 1, 1, xc);
                    d.rect(cx + 4 - i, cy + i, 1, 1, xc);
                }
                hit("unbind", cx - 3, cy - 3, 12, 12, null);
            }
            y += SET_H + 1;
            d.rect(x + 6, y, w - 16, 1, 0x0CFFFFFF);
            y += 5;
        }

        if (m instanceof HudModule) {
            String label = "Customize look & position";
            int bw = Math.min(w - 8, d.width(label) + 26);
            boolean ch = inView && in(mx, my, x, y, bw, 18);
            d.roundRect(x, y, bw, 18, Theme.withAlpha(a, ch ? 0xFF : 0x2A));
            d.roundOutline(x, y, bw, 18, Theme.withAlpha(a, ch ? 0xFF : 0xA0));
            pencil(d, x + 7, y + 5, ch ? 0xFF15171C : Theme.withAlpha(a, 0xFF));
            d.text(label, x + 19, y + 5, ch ? 0xFF15171C : Theme.withAlpha(a, 0xFF), false);
            hit("customize", x, y, bw, 18, null);
            d.text(d.trim("Size, colors and background. Or right-click it in Edit HUD.", w - 8),
                    x + 1, y + 23, Theme.FAINT, false);
            y += 40;
        }

        List<Setting> list = m.settings();
        for (int i = 0; i < list.size(); i++) {
            Setting st = list.get(i);
            boolean rh = inView && in(mx, my, x, y, w - 4, SET_H);
            if (rh) {
                d.roundRect(x, y, w - 4, SET_H, Theme.withAlpha(0xFFFFFF, 0x0E));
            }
            d.text(d.trim(st.label, w / 2), x + 6, y + 7, rh ? Theme.TEXT : Theme.MUTED, false);
            int right = x + w - 8;
            String id = "set:" + st.id;
            if (st instanceof BoolSetting) {
                BoolSetting b = (BoolSetting) st;
                float target = b.value ? 1f : 0f;
                Float cur = settingKnob.get(st);
                float v = cur == null ? target : cur;
                v = v < target ? Math.min(target, v + dt * 9f) : Math.max(target, v - dt * 9f);
                settingKnob.put(st, v);
                switchKnob(d, right - 24, y + 5, v, a, rh);
                hit(id, x, y, w - 4, SET_H, st);
            } else if (st instanceof SliderSetting) {
                SliderSetting sl = (SliderSetting) st;
                String val = sl.text();
                int vw = Math.max(d.width(val), 28);
                d.text(val, right - d.width(val), y + 7, Theme.TEXT, false);
                int tw = Math.max(40, Math.min(110, w - 16 - w / 2 - vw));
                int tx = right - vw - 8 - tw;
                boolean active = draggingSlider == sl;
                d.roundRect(tx, y + 10, tw, 3, 0xFF2F3542);
                int fill = Math.round(sl.fraction() * tw);
                d.roundRect(tx, y + 10, Math.max(2, fill), 3, a);
                int kx = tx + fill - 3;
                d.rect(kx + 1, y + 7, 4, 9, rh || active ? 0xFFFFFFFF : 0xFFE6E6E6);
                d.rect(kx, y + 8, 6, 7, rh || active ? 0xFFFFFFFF : 0xFFE6E6E6);
                hit(id, tx - 3, y + 2, tw + 6, SET_H - 4, st);
            } else if (st instanceof ChoiceSetting || st instanceof ActionSetting) {
                boolean action = st instanceof ActionSetting;
                String val = action ? ((ActionSetting) st).button() : ((ChoiceSetting) st).text();
                int pw2 = d.width(val) + (action ? 14 : 22);
                int bx = right - pw2;
                boolean ph2 = inView && in(mx, my, bx, y + 3, pw2, 16);
                d.roundRect(bx, y + 3, pw2, 16, action ? Theme.withAlpha(a, ph2 ? 0xFF : 0x2A)
                        : ph2 ? Theme.ROW_HOVER : Theme.FIELD);
                d.roundOutline(bx, y + 3, pw2, 16, action ? Theme.withAlpha(a, 0xA0)
                        : ph2 ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
                int tc = action ? (ph2 ? 0xFF15171C : Theme.withAlpha(a, 0xFF)) : Theme.TEXT;
                d.text(val, bx + 7, y + 7, tc, false);
                if (!action) {  // a small arrow: click to cycle
                    arrowRight(d, bx + pw2 - 10, y + 8, ph2 ? a : Theme.FAINT);
                }
                hit(id, bx, y + 3, pw2, 16, st);
            }
            y += SET_H;
            if (i < list.size() - 1) {
                d.rect(x + 6, y, w - 16, 1, 0x0CFFFFFF);
            }
            y += 1;
        }
        if (!list.isEmpty()) {
            String reset = "Reset options";
            int rw = d.width(reset) + 14;
            int ry = y + 6;
            boolean rh = inView && in(mx, my, x, ry, rw, 14);
            d.roundRect(x, ry, rw, 14, rh ? Theme.ROW_HOVER : 0x00000000);
            d.roundOutline(x, ry, rw, 14, rh ? Theme.MUTED : Theme.HAIRLINE);
            d.text(reset, x + 7, ry + 3, rh ? Theme.TEXT : Theme.MUTED, false);
            hit("reset", x, ry, rw, 14, null);
        }
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);
        // Only what's inside the view can be clicked.
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.setting != null || h.id.equals("customize") || h.id.equals("reset")
                    || h.id.equals("bind") || h.id.equals("unbind")) {
                if (h.y + h.h / 2 < top || h.y + h.h / 2 > top + viewH) {
                    hits.remove(i);
                }
            }
        }
        scrollbar(d, top, viewH, inView, a);

    }

    private void slideTo(SliderSetting sl, int mx) {
        for (Hit h : hits) {
            if (h.setting == sl) {
                float t = (mx - (h.x + 3)) / (float) Math.max(1, h.w - 6);
                sl.set(sl.min + Math.max(0f, Math.min(1f, t)) * (sl.max - sl.min));
                return;
            }
        }
    }

    private boolean pageClicked(int mx, int my) {
        for (Hit h : new ArrayList<Hit>(hits)) {
            if (!in(mx, my, h.x, h.y, h.w, h.h)) {
                continue;
            }
            if (h.id.equals("bind")) {
                binding = binding == page ? null : page;
                return true;
            } else if (h.id.equals("unbind")) {
                page.key = Keys.NONE;
                binding = null;
            } else if (h.id.equals("back")) {
                closePage();
            } else if (h.id.equals("toggle")) {
                page.setEnabled(!page.enabled());
            } else if (h.id.equals("customize")) {
                HudModule hm = (HudModule) page;
                if (!hm.enabled()) {
                    hm.setEnabled(true);
                }
                editingHud = true;
                editor.openFor(hm);
            } else if (h.id.equals("reset")) {
                for (Setting st : page.settings()) {
                    st.reset();
                }
            } else if (h.setting instanceof BoolSetting) {
                ((BoolSetting) h.setting).value = !((BoolSetting) h.setting).value;
            } else if (h.setting instanceof ChoiceSetting) {
                ((ChoiceSetting) h.setting).next();
            } else if (h.setting instanceof ActionSetting) {
                ((ActionSetting) h.setting).action.run();
            } else if (h.setting instanceof SliderSetting) {
                draggingSlider = (SliderSetting) h.setting;
                slideTo(draggingSlider, mx);
            }
            pc.markDirty();
            return true;
        }
        return false;
    }

    private void closePage() {
        binding = null;
        page = null;
        draggingSlider = null;
        hits.clear();
        scroll = 0;
        smoothScroll = 0;
    }

    /** A gear: a ring with eight teeth, drawn pixel by pixel (10x10). */
    private static void gear(Draw d, int x, int y, int c) {
        d.rect(x + 3, y + 1, 4, 1, c);
        d.rect(x + 3, y + 8, 4, 1, c);
        d.rect(x + 1, y + 3, 1, 4, c);
        d.rect(x + 8, y + 3, 1, 4, c);
        d.rect(x + 2, y + 2, 1, 1, c);
        d.rect(x + 7, y + 2, 1, 1, c);
        d.rect(x + 2, y + 7, 1, 1, c);
        d.rect(x + 7, y + 7, 1, 1, c);
        // teeth
        d.rect(x + 4, y, 2, 1, c);
        d.rect(x + 4, y + 9, 2, 1, c);
        d.rect(x, y + 4, 1, 2, c);
        d.rect(x + 9, y + 4, 1, 2, c);
        d.rect(x + 1, y + 1, 1, 1, c);
        d.rect(x + 8, y + 1, 1, 1, c);
        d.rect(x + 1, y + 8, 1, 1, c);
        d.rect(x + 8, y + 8, 1, 1, c);
        // hub
        d.rect(x + 4, y + 4, 2, 2, c);
    }

    private static void arrowLeft(Draw d, int x, int y, int c) {
        for (int i = 0; i < 4; i++) {
            d.rect(x + i, y + 3 - i, 1, 1, c);
            d.rect(x + i, y + 3 + i, 1, 1, c);
        }
        d.rect(x, y + 3, 1, 1, c);
    }

    private static void arrowRight(Draw d, int x, int y, int c) {
        for (int i = 0; i < 3; i++) {
            d.rect(x + i, y + i, 1, 1, c);
            d.rect(x + i, y + 4 - i, 1, 1, c);
        }
    }

    /** A small pencil, for "customize". */
    private static void pencil(Draw d, int x, int y, int c) {
        for (int i = 0; i < 6; i++) {
            d.rect(x + 1 + i, y + 6 - i, 2, 1, c);
        }
        d.rect(x, y + 7, 1, 1, c);
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
        int mx = Math.round(screenMx / k);
        int my = Math.round(screenMy / k);
        layout(p);
        if (binding != null) {
            boolean onBind = false;
            for (Hit h : hits) {
                if ((h.id.equals("bind") || h.id.startsWith("bindrow:")) && in(mx, my, h.x, h.y, h.w, h.h)) {
                    onBind = true;
                }
            }
            if (!onBind) {
                binding = null;  // clicking anywhere else cancels
                return true;
            }
        }
        if (button == 1 && page == null && view == 0 && in(mx, my, listX, listY, listW, listH)) {
            // Right-click a tile to open its options, same as clicking the tile.
            List<Module> mods = visible();
            for (int i = 0; i < mods.size(); i++) {
                int[] c = card(i);
                if (in(mx, my, c[0], c[1], c[2], tileH)) {
                    openPage(mods.get(i));
                    return true;
                }
            }
        }
        if (button != 0) {
            return false;
        }
        if (!in(mx, my, px, py, pw, ph) || in(mx, my, closeX, closeY, 16, 16)) {
            // A click outside the window (or on ×) closes it, like any pop-up.
            p.openScreen(p.inWorld() ? Platform.Screen.NONE : Platform.Screen.TITLE);
            return true;
        }
        for (int i = 0; i < VIEWS.length; i++) {
            if (in(mx, my, tabX0 + i * (tabW + 4), tabsY, tabW, TABBAR_H)) {
                if (page != null) {
                    closePage();
                }
                if (view != i) {
                    listShownAt = System.currentTimeMillis();
                }
                view = i;
                scroll = 0;
                smoothScroll = 0;
                hits.clear();
                return true;
            }
        }
        if (in(mx, my, menuBtnX, tabsY, TABBAR_H, TABBAR_H)) {
            onlyOn = !onlyOn;
            listShownAt = System.currentTimeMillis();
            scroll = 0;
            return true;
        }
        for (int i = 0; i < TABS.length; i++) {
            if (in(mx, my, px + 10, tabY(i), SIDE_W - 8, TAB_H)) {
                if (page != null) {
                    closePage();
                }
                if (tab != TABS[i] || view != 0) {
                    listShownAt = System.currentTimeMillis();
                }
                tab = TABS[i];
                view = 0;
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
        if (page != null) {
            pageClicked(mx, my);
            return true;
        }
        if (view != 0) {
            viewClicked(mx, my);
            return true;
        }
        if (in(mx, my, listX, listY, listW, listH)) {
            List<Module> mods = visible();
            for (int i = 0; i < mods.size(); i++) {
                int[] c = card(i);
                if (in(mx, my, c[0], c[1], c[2], tileH)) {
                    Module m = mods.get(i);
                    int[] tg = toggleAt(c);
                    if (in(mx, my, tg[0] - 3, tg[1] - 3, 30, 17)) {  // the switch
                        m.setEnabled(!m.enabled());
                        pc.markDirty();
                    } else {  // anywhere else on the tile: its options
                        openPage(m);
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private void viewClicked(int mx, int my) {
        for (Hit h : new ArrayList<Hit>(hits)) {
            if (!in(mx, my, h.x, h.y, h.w, h.h)) {
                continue;
            }
            if (h.id.startsWith("accent:")) {
                pc.setAccent(ACCENTS[Integer.parseInt(h.id.substring(7))]);
            } else if (h.id.startsWith("module:")) {
                Module m = pc.module(h.id.substring(7));
                m.setEnabled(!m.enabled());
            } else if (h.id.equals("reset-hud")) {
                for (Module m : pc.modules()) {
                    if (m instanceof HudModule) {
                        ((HudModule) m).resetStyle();
                    }
                }
            } else if (h.id.equals("clear-keys")) {
                for (Module m : pc.modules()) {
                    m.key = Keys.NONE;
                }
            } else if (h.id.startsWith("bindrow:")) {
                Module m = pc.module(h.id.substring(8));
                binding = binding == m ? null : m;
                return;
            }
            pc.markDirty();
            return;
        }
    }

    public boolean mouseDragged(int screenMx, int screenMy) {
        if (draggingSlider != null && !editingHud) {
            slideTo(draggingSlider, Math.round(screenMx / k));
            return true;
        }
        return editingHud && editor.mouseDragged(screenMx, screenMy);
    }

    public boolean mouseReleased() {
        if (draggingSlider != null) {
            draggingSlider = null;
            pc.markDirty();
            return true;
        }
        return editingHud && editor.mouseReleased();
    }

    /** amount: positive = scroll up. */
    public boolean mouseScrolled(double amount) {
        if (editingHud) {
            return editor.mouseScrolled(amount, lastMx, lastMy);
        }
        scroll -= (int) Math.round(amount * (CARD_H + GAP) * 0.6);
        scroll = clamp(scroll, 0, maxScroll);
        return true;
    }

    /** Returns true if the key was used. Esc is used by the HUD editor and settings pages (to go back). */
    public boolean keyPressed(int key) {
        if (binding != null) {
            if (key == KEY_BACKSPACE || key == Keys.KEY_DELETE) {
                binding.key = Keys.NONE;
            } else if (key != KEY_ESCAPE) {
                binding.key = key;
                boundAt = System.currentTimeMillis();
            }
            binding = null;
            pc.markDirty();
            return true;
        }
        if (editingHud) {
            if (key == KEY_ESCAPE || key == KEY_ENTER) {
                editingHud = false;
                return true;
            }
            return false;
        }
        if (page != null) {
            if (key == KEY_ESCAPE || (key == KEY_BACKSPACE && search.isEmpty())) {
                closePage();
                return true;
            }
            return false;
        }
        if (key == KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            scroll = 0;
            smoothScroll = 0;
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
        if (System.currentTimeMillis() - boundAt < 250) {
            boundAt = 0;
            return true;  // the character of the key that was just bound
        }
        if (binding != null) {
            return true;
        }
        if (editingHud || Character.isISOControl(c) || search.length() >= 24) {
            return false;
        }
        if (search.isEmpty() && c == ' ') {
            return true;
        }
        if (page != null) {  // typing goes back to the list to search
            closePage();
        }
        if (view != 0) {
            view = 0;
            listShownAt = System.currentTimeMillis();
        }
        search += c;
        scroll = 0;
        smoothScroll = 0;  // results start at the top straight away
        return true;
    }
}
