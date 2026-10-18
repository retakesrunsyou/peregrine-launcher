package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.peregrine.client.core.settings.ActionSetting;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.KeySetting;
import net.peregrine.client.core.settings.Setting;
import net.peregrine.client.core.settings.SliderSetting;
import net.peregrine.client.core.settings.TextSetting;

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

    private static final int TITLE_H = 30;
    private static final int TABBAR_H = 16;
    private static final int SIDE_W = 124;
    private static final int TILE_GAP = 6;
    private static final int SIDE_ROW = 15;

    /** The old tab names, still understood by the self-test ("view:Settings"...). */
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
    private TextSetting typing;        // the text box being typed into
    private KeySetting bindingKey;     // a key setting waiting for a key
    private final Map<Setting, Float> settingKnob = new IdentityHashMap<Setting, Float>();
    private Module binding;       // waiting for a key for this feature's keybind
    private long boundAt;         // when a key was just bound (its typed character is swallowed)
    private int cols = 2;
    private int tileW = 80, tileH = 76;
    private long listShownAt;
    private int view;             // 0 modules, 1 settings, 2 keybinds, 3 a panel (mods, shaders, packs, locker, friends)
    private String section = "cat:all";  // what's chosen in the sidebar
    private String expanded = "modules"; // the sidebar group that's open (one at a time)
    private final Map<String, Float> expand = new java.util.HashMap<String, Float>();
    private final Map<String, Float> sideGlow = new java.util.HashMap<String, Float>();
    private final Map<String, MenuPanel> panels = new java.util.HashMap<String, MenuPanel>();
    private final List<Hit> sideHits = new ArrayList<Hit>();
    private float sideSlide = -1;
    private int sideX, sideY, sideW, sideH;
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
        sideSlide = -1;
        MenuPanel pn = panel();
        if (pn != null) {
            pn.shown();
            pn.searchChanged("");
        }
    }

    // ------------------------------------------------------------ the sidebar

    /** One line of the sidebar: a group (with children that drop down) or a page. */
    private static final class Node {
        final String id, label, icon;
        final Node[] kids;

        Node(String id, String label, String icon, Node... kids) {
            this.id = id;
            this.label = label;
            this.icon = icon;
            this.kids = kids;
        }
    }

    private static final Node[] SIDEBAR = {
        new Node("modules", "Modules", "grid",
                new Node("cat:HUD", "HUD", "hud"), new Node("cat:UTILITY", "Utility", "wrench"),
                new Node("cat:VISUALS", "Visuals", "eye"), new Node("cat:PERFORMANCE", "Performance", "bolt")),
        new Node("mods", "Mods", "puzzle", new Node("mods:installed", "Installed", null), new Node("mods:browse", "Browse", null)),
        new Node("shaders", "Shaders", "sun", new Node("shaders:installed", "Installed", null), new Node("shaders:browse", "Browse", null)),
        new Node("packs", "Resource packs", "layers", new Node("packs:installed", "Installed", null), new Node("packs:browse", "Browse", null)),
        new Node("schematics", "Schematics", "blueprint"),
        new Node("locker", "Locker", "shirt"),
        new Node("friends", "Friends", "people"),
        new Node("voice", "Voice chat", "mic"),
        null,  // a divider
        new Node("keybinds", "Keybinds", "keyboard"),
        new Node("settings", "Settings", "gear"),
    };

    /** The page shown for a sidebar choice (null for modules, settings and keybinds). */
    private MenuPanel panel() {
        if (view != 3) {
            return null;
        }
        MenuPanel pn = panels.get(section);
        if (pn == null) {
            if (section.equals("locker")) {
                pn = new LockerPanel(pc, this);
            } else if (section.equals("friends")) {
                pn = new FriendsPanel(pc, this);
            } else if (section.equals("schematics")) {
                pn = new SchematicsPanel(pc, this);
            } else if (section.equals("voice")) {
                pn = new VoicePanel(pc, this);
            } else {
                String[] parts = section.split(":");
                Library.Kind kind = parts[0].equals("mods") ? Library.Kind.MODS
                        : parts[0].equals("shaders") ? Library.Kind.SHADERS : Library.Kind.PACKS;
                pn = new LibraryPanel(pc, this, kind, parts.length > 1 && parts[1].equals("browse"));
            }
            panels.put(section, pn);
            pn.shown();
        }
        return pn;
    }

    /** Chooses something in the sidebar. */
    void choose(String id) {
        if (page != null) {
            closePage();
        }
        boolean changed = !id.equals(section);
        section = id;
        if (id.startsWith("cat:")) {
            String c = id.substring(4);
            tab = c.equals("all") ? null : Module.Category.valueOf(c);
            view = 0;
            expanded = "modules";
        } else if (id.equals("settings")) {
            view = 1;
        } else if (id.equals("keybinds")) {
            view = 2;
        } else {
            view = 3;
            int colon = id.indexOf(':');
            if (colon > 0) {
                expanded = id.substring(0, colon);
            }
        }
        scroll = 0;
        smoothScroll = 0;
        hits.clear();
        if (changed) {
            listShownAt = System.currentTimeMillis();
            search = "";
            MenuPanel pn = panel();
            if (pn != null) {
                pn.shown();
                pn.searchChanged("");
            }
        }
    }

    /** A group's header was clicked: open it (choosing its first page), or fold it away again. */
    private void toggleGroup(Node n) {
        if (n.id.equals("modules") && section.startsWith("cat:") && !section.equals("cat:all")) {
            choose("cat:all");  // from HUD (or another category) to all of them
            expanded = n.id;
            return;
        }
        boolean inside = n.id.equals("modules") && section.equals("cat:all");
        for (Node k : n.kids) {
            inside |= k.id.equals(section);
        }
        if (inside && n.id.equals(expanded)) {  // clicked again: fold it away
            expanded = "";
            return;
        }
        if (!inside) {
            choose(n.id.equals("modules") ? "cat:all" : n.kids[0].id);
        }
        expanded = n.id;
    }

    // ---- for the menu's pages (MenuPanel)

    void clipArea(Draw d, int x, int y, int w, int h) {
        d.popScale();
        d.clip(s(x), s(y), s(w), s(h));
        d.pushScale(0, 0, k);
    }

    void unclipArea(Draw d) {
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);
    }

    void scrollbar(Draw d, int top, int viewH, int scrollNow, int max, float smooth, int a) {
        if (max <= 0) {
            return;
        }
        int track = viewH - 4;
        int barH = Math.max(14, track * viewH / (viewH + max));
        int barY = top + 2 + Math.round((track - barH) * (smooth / max));
        d.roundRect(px + pw - 7, top + 2, 3, track, 1, 0x14FFFFFF);
        d.roundRect(px + pw - 7, barY, 3, barH, 1, Theme.withAlpha(a, 0xB0));
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

    String section() {
        return section;
    }

    /** Modrinth results showing on a Browse page (for the self-test). */
    int browseResults() {
        MenuPanel pn = panel();
        return pn instanceof LibraryPanel ? ((LibraryPanel) pn).resultCount() : -1;
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
        if (what.startsWith("view:") || what.startsWith("side:")) {
            String id = what.startsWith("side:") ? what.substring(5)
                    : what.equals("view:Settings") ? "settings" : what.equals("view:Keybinds") ? "keybinds" : "modules";
            for (Hit h : sideHits) {
                if (h.id.equals(id)) {
                    return new int[] {s(h.x + h.w / 2), s(h.y + h.h / 2)};
                }
            }
            return null;
        }
        if (what.startsWith("panel:") && panel() != null) {
            for (MenuPanel.Spot sp : panel().spots) {
                if (sp.id.equals(what.substring(6))) {
                    return new int[] {s(sp.x + sp.w / 2), s(sp.y + sp.h / 2)};
                }
            }
            return null;
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
                return y > listY && y < listY + listH ? new int[] {s(tg[0] + 9), s(y)} : null;
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
        pw = Math.min(sw - 16, clamp(Math.round(sw * 0.74f), 340, 560));
        ph = Math.min(sh - 16, clamp(Math.round(sh * 0.82f), 220, 340));
        px = (sw - pw) / 2;
        py = (sh - ph) / 2;
        closeX = px + pw - 23;
        closeY = py + 7;
        searchW = clamp(Math.round(pw * 0.27f), 90, 150);
        searchX = closeX - 6 - searchW;
        searchY = py + 7;
        tabsY = searchY;
        menuBtnX = searchX - 4 - TABBAR_H;
        int bodyY = py + TITLE_H + 6;
        editW = SIDE_W;
        editH = 17;
        editX = px + 8;
        editY = py + ph - 8 - editH;
        sideX = px + 8;
        sideY = bodyY;
        sideW = SIDE_W;
        sideH = editY - 6 - bodyY;
        listX = px + 8 + SIDE_W + 12;
        listY = bodyY;
        listW = px + pw - 12 - listX;
        listH = py + ph - 8 - bodyY;
        cols = Math.max(2, (listW + TILE_GAP) / (104 + TILE_GAP));
        tileW = (listW - TILE_GAP * (cols - 1)) / cols;
        tileH = 60;
    }

    /** Tile i of the grid: x, y, width (menu pixels, scrolled). */
    private int[] card(int i) {
        int col = i % cols, row = i / cols;
        return new int[] {listX + col * (tileW + TILE_GAP), listY + row * (tileH + TILE_GAP) - Math.round(smoothScroll), tileW};
    }

    /** A tile's switch: x, y (18 x 10), in its top right corner. */
    private int[] toggleAt(int[] c) {
        return new int[] {c[0] + c[2] - 25, c[1] + 8};
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
            // Rise into place as it fades in.
            float e = 1f - (1f - t) * (1f - t);
            int lift = Math.round((1f - e) * 12);
            py += lift;
            listY += lift;
            searchY += lift;
            closeY += lift;
            editY += lift;
            tabsY += lift;
            sideY += lift;
        }
        int a = pc.accent();

        // The window: frosted glass with the accent glowing along its top edge, the falcon faint in the corner
        Glass.window(d, px, py, pw, ph, pc.menuBg(Glass.GLASS), a);
        Glass.falcon(d, px + pw - 92, py + ph - 96, 9f, 0x0CFFFFFF);
        d.rect(px + 10, py + TITLE_H, pw - 20, 1, 0x18FFFFFF);

        // Title: the falcon, "Peregrine", and where you are
        int ty = py + (TITLE_H - 8) / 2;
        d.textScaled(Hooks.LOGO, px + 9, ty - 4, 0xFFFFFFFF, 1.6f, false);
        d.textScaled("Peregrine", px + 27, ty - 1, Theme.TEXT, 1.25f, true);
        String crumb = crumb();
        int cx0 = px + 27 + Math.round(d.width("Peregrine") * 1.25f) + 6;
        int room = (view == 0 ? menuBtnX : searchX) - 8 - cx0;
        if (room > 20) {
            d.text("/", cx0, ty + 1, Theme.FAINT, false);
            d.text(d.trim(crumb, room - 10), cx0 + 9, ty + 1, a, false);
        }
        boolean closeHover = in(mx, my, closeX, closeY, 16, 16);
        float cg = sideGlowOf("close", closeHover, dt);
        d.roundRect(closeX, closeY, 16, 16, 8, Theme.mix(0x30FFFFFF, Theme.withAlpha(Theme.BAD, 0xD0), cg));
        int xc = Theme.mix(Theme.MUTED, 0xFFFFFFFF, cg);
        for (int i = 0; i < 6; i++) {  // a small drawn ×, crisp at any GUI scale
            d.rect(closeX + 5 + i, closeY + 5 + i, 1, 1, xc);
            d.rect(closeX + 10 - i, closeY + 5 + i, 1, 1, xc);
        }

        // Search (it searches whatever page you're on), and ≡ (only what's on) for the modules
        MenuPanel pn = page == null ? panel() : null;
        boolean searchable = pn == null || !pn.placeholder().isEmpty();
        if (view == 0) {
            boolean mh = in(mx, my, menuBtnX, tabsY, TABBAR_H, TABBAR_H);
            float mg = sideGlowOf("onlyon", mh || onlyOn, dt);
            d.roundRect(menuBtnX, tabsY, TABBAR_H, TABBAR_H, 8, onlyOn ? Theme.withAlpha(a, 0x60) : Theme.mix(0x20FFFFFF, 0x38FFFFFF, mg));
            for (int i = 0; i < 3; i++) {
                d.rect(menuBtnX + 5, tabsY + 4 + i * 3, 6, 1, onlyOn ? a : Theme.mix(Theme.MUTED, Theme.TEXT, mg));
            }
        }
        if (searchable) {
            boolean searching = !search.isEmpty();
            d.roundRect(searchX, searchY, searchW, TABBAR_H, 8, 0x50000000);
            d.roundOutline(searchX, searchY, searchW, TABBAR_H, 8, searching ? Theme.withAlpha(a, 0xC0) : 0x22FFFFFF);
            boolean caret = (now / 500) % 2 == 0;
            String shown = searching ? search + (caret ? "_" : "") : pn != null ? pn.placeholder() : "Search modules...";
            MenuIcons.draw(d, "search", searchX + 5, searchY + 4, searching ? a : Theme.FAINT);
            d.text(d.trim(shown, searchW - 22), searchX + 17, searchY + 4, searching ? Theme.TEXT : Theme.FAINT, false);
        }

        drawSidebar(d, mx, my, dt, a);
        boolean editHover = in(mx, my, editX, editY, editW, editH);
        float eg = sideGlowOf("edit", editHover, dt);
        Glass.pill(d, editX, editY, editW, editH, true, eg, a);
        MenuIcons.draw(d, "hud", editX + 8, editY + 4, Theme.onColor(a));
        String edit = "Edit HUD";
        d.text(edit, editX + (editW - d.width(edit)) / 2 + 5, editY + 5, Theme.onColor(a), false);

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
        if (pn != null) {
            // pages slide in a little from the right when chosen
            float st = FadeDraw.progress(listShownAt, 220);
            float se = 1f - (1f - st) * (1f - st);
            int off = Math.round((1f - se) * 10);
            Draw pd = st < 1f ? new FadeDraw(d, 0.2f + 0.8f * st) : d;
            pn.render(pd, mx, my, listX + off, listY, listW, listH, dt, now, a);
            return;
        }

        // Module cards
        List<Module> mods = visible();
        int rows = (mods.size() + cols - 1) / cols;
        maxScroll = Math.max(0, rows * (tileH + TILE_GAP) - TILE_GAP - listH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }

        // Clipping works in screen pixels on every version, so step out of the scale for it.
        clipArea(d, listX - 2, listY, listW + 4, listH);
        boolean mouseInList = in(mx, my, listX, listY, listW, listH);
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            int[] c = card(i);
            int cx = c[0], cy = c[1], cw = c[2];
            if (cy + tileH < listY || cy > listY + listH) {
                continue;
            }
            // Cards pop in one after another when the menu (or a new category) opens.
            float raw = FadeDraw.progress(listShownAt + Math.min(i, 18) * 14L, 240);
            float appear = Math.max(0f, Math.min(1f, raw * 1.4f));
            Draw cd = appear < 1f ? new FadeDraw(d, 0.05f + 0.95f * appear) : d;
            if (raw < 1f) {
                cy += Math.round((1f - Glass.backOut(raw)) * 8);
                c = new int[] {cx, cy, cw};
            }
            boolean hover = mouseInList && in(mx, my, cx, cy, cw, tileH);
            float g = ease(rowGlow, m, hover ? 1f : 0f, dt * 10f);
            float on = animate(m, dt);
            cy -= Math.round(g * 1.5f);
            c = new int[] {cx, cy, cw};
            Glass.pane(cd, cx, cy, cw, tileH, 5, Theme.mix(Glass.PANE, Glass.PANE_HOVER, g));
            if (on > 0.01f) {  // switched on: a warm wash of the accent
                cd.roundRect(cx + 1, cy + 1, cw - 2, tileH - 2, 4, Theme.withAlpha(a, Math.round(0x16 * on)));
            }
            if (g > 0.01f) {
                cd.roundOutline(cx, cy, cw, tileH, 5, Theme.withAlpha(a, Math.round(0xA0 * g)));
            }
            // the icon in a soft round light
            int ix = cx + 7, iy = cy + 7;
            cd.roundRect(ix, iy, 24, 24, 12, Theme.mix(0x24FFFFFF, Theme.withAlpha(a, 0x50), on));
            Object stack = p.icon(Icons.of(m));
            if (stack != null) {
                cd.item(stack, ix + 4, iy + 4);
            } else {
                categoryIcon(cd, m.category, ix + 8, iy + 8, Theme.TEXT);
            }
            int[] tg = toggleAt(c);
            Glass.toggle(cd, tg[0], tg[1], on, a, hover && in(mx, my, tg[0] - 3, tg[1] - 3, 24, 16));
            boolean warn = hover && m.warning() != null;
            String title = cd.trim(m.name, cw - 14);
            cd.text(title, cx + 8, cy + tileH - 23, warn ? Theme.OK : Theme.TEXT, false);
            String sub = warn ? cd.trim(m.warning(), cw - 14) : m.category.label;
            if (m.key >= 0 && !warn) {
                sub = Keys.name(m.key);
                int kw = cd.width(sub) + 6;
                cd.roundRect(cx + 7, cy + tileH - 13, kw, 10, 3, 0x40000000);
                cd.text(sub, cx + 10, cy + tileH - 12, Theme.MUTED, false);
            } else {
                cd.text(sub, cx + 8, cy + tileH - 12, warn ? Theme.withAlpha(Theme.OK, 0xC0) : Theme.FAINT, false);
            }
            // a line of light along the bottom that fills in when it's on
            int lw = Math.round((cw - 12) * on);
            if (lw > 0) {
                cd.rect(cx + 6, cy + tileH - 2, lw, 1, Theme.withAlpha(a, 0xE0));
            }
        }
        unclipArea(d);

        if (mods.isEmpty()) {
            String none = onlyOn && search.isEmpty() ? "Nothing switched on here" : "Nothing matches \"" + d.trim(search, listW - 80) + "\"";
            d.text(none, listX + (listW - d.width(none)) / 2, listY + listH / 2 - 8, Theme.MUTED, false);
            String hint = onlyOn ? "The ≡ button shows everything again" : "Backspace to clear the search";
            d.text(hint, listX + (listW - d.width(hint)) / 2, listY + listH / 2 + 4, Theme.FAINT, false);
        }
        scrollbar(d, listY, listH, in(mx, my, listX, listY, listW + 8, listH), a);
    }

    /** Where you are, for the title: "Modules / HUD", "Mods / Browse"... */
    private String crumb() {
        if (page != null) {
            return page.name;
        }
        for (Node n : SIDEBAR) {
            if (n == null) {
                continue;
            }
            if (n.id.equals(section) || (n.id.equals("modules") && section.equals("cat:all"))) {
                return n.label;
            }
            for (Node c : n.kids) {
                if (c.id.equals(section)) {
                    return n.label + " / " + c.label;
                }
            }
        }
        return "";
    }

    private float sideGlowOf(String id, boolean on, float dt) {
        Float g = sideGlow.get(id);
        float v = Glass.ease(g == null ? 0f : g, on, dt);
        sideGlow.put(id, v);
        return v;
    }

    /** One drawn line of the sidebar, kept until the selection pill is under it. */
    private static final class Row {
        final Node n;
        final int y;
        final float alpha, open;
        final boolean kid;

        Row(Node n, int y, float alpha, float open, boolean kid) {
            this.n = n;
            this.y = y;
            this.alpha = alpha;
            this.open = open;
            this.kid = kid;
        }
    }

    private final List<Row> rows = new ArrayList<Row>();

    /** The sidebar: groups that drop down (Modules, Mods, Shaders, Resource packs) and single pages. */
    private void drawSidebar(Draw d, int mx, int my, float dt, int a) {
        sideHits.clear();
        rows.clear();
        Glass.pane(d, sideX, sideY, sideW, sideH, 5, pc.menuBg(0x50080A0E));
        int lines = 0, dividers = 0;
        for (Node n : SIDEBAR) {
            if (n == null) {
                dividers++;
            } else {
                lines += 1 + (n.id.equals(expanded) ? n.kids.length : 0);
            }
        }
        int rowH = Math.max(11, Math.min(SIDE_ROW, (sideH - 8 - dividers * 7) / Math.max(1, lines)));
        int ty = (rowH - 8) / 2 + 1;
        int y = sideY + 4;
        float selY = -1;
        boolean selKid = false;
        for (Node n : SIDEBAR) {
            if (n == null) {
                d.rect(sideX + 8, y + 3, sideW - 16, 1, 0x18FFFFFF);
                y += 7;
                continue;
            }
            float ex = 0f;
            if (n.kids.length > 0) {
                Float e = expand.get(n.id);
                float target = n.id.equals(expanded) ? 1f : 0f;
                ex = e == null ? target : e + (target - e) * Math.min(1f, dt * 14f);
                if (Math.abs(target - ex) < 0.01f) {
                    ex = target;
                }
                expand.put(n.id, ex);
            }
            if (n.id.equals(section) || (n.id.equals("modules") && section.equals("cat:all"))) {
                selY = y;
            }
            sideHits.add(new Hit(n.id, sideX + 3, y, sideW - 6, rowH, null));
            rows.add(new Row(n, y, 1f, ex, false));
            y += rowH;
            if (ex > 0.01f) {
                int kidsTop = y;
                for (Node c : n.kids) {
                    if (c.id.equals(section)) {
                        selY = y;
                        selKid = true;
                    }
                    if (ex > 0.9f) {
                        sideHits.add(new Hit(c.id, sideX + 14, y, sideW - 17, rowH - 2, null));
                    }
                    rows.add(new Row(c, y, Math.max(0f, Math.min(1f, ex * 1.3f - 0.3f)), 0f, true));
                    y += Math.round((rowH - 1) * ex);
                }
                // a thin line of the accent joining the group's pages
                d.rect(sideX + 10, kidsTop, 1, Math.max(0, y - kidsTop - 3), Theme.withAlpha(a, Math.round(0x60 * ex)));
            }
        }
        // hover washes, under everything else
        for (Row r : rows) {
            boolean selected = isSelected(r.n);
            int rx = r.kid ? sideX + 14 : sideX + 3, rw = r.kid ? sideW - 17 : sideW - 6, rh = r.kid ? rowH - 2 : rowH;
            float g = sideGlowOf("n:" + r.n.id, r.alpha > 0.9f && in(mx, my, rx, r.y, rw, rh), dt);
            if (!selected && g > 0.01f) {
                d.roundRect(rx, r.y, rw, rh, 4, Theme.withAlpha(0xFFFFFF, Math.round(0x16 * g * r.alpha)));
            }
        }
        // the selection: an accent pill gliding from one choice to the next
        if (selY >= 0) {
            sideSlide = sideSlide < 0 ? selY : sideSlide + (selY - sideSlide) * Math.min(1f, dt * 16f);
            int sx = selKid ? sideX + 14 : sideX + 3, sw = selKid ? sideW - 17 : sideW - 6, sh = selKid ? rowH - 2 : rowH;
            d.roundRect(sx, Math.round(sideSlide), sw, sh, 4, a);
            d.rect(sx + 4, Math.round(sideSlide), sw - 8, 1, 0x50FFFFFF);
        }
        // icons and names on top
        for (Row r : rows) {
            if (r.alpha <= 0.01f) {
                continue;
            }
            Draw rd = r.alpha < 1f ? new FadeDraw(d, r.alpha) : d;
            boolean selected = isSelected(r.n);
            boolean inside = false;
            for (Node c : r.n.kids) {
                inside |= c.id.equals(section);
            }
            Float gl = sideGlow.get("n:" + r.n.id);
            float g = gl == null ? 0f : gl;
            if (r.kid) {
                int kc = selected ? Theme.onColor(a) : Theme.mix(Theme.FAINT, Theme.TEXT, g);
                if (r.n.icon != null) {
                    MenuIcons.draw(rd, r.n.icon, sideX + 18, r.y + ty - 2, kc);
                } else {
                    rd.rect(sideX + 20, r.y + ty + 2, 2, 2, kc);
                }
                rd.text(rd.trim(r.n.label, sideW - 50), sideX + 31, r.y + ty - 1, kc, false);
                if (r.n.id.startsWith("cat:")) {  // how many are on
                    String cnt = String.valueOf(count(Module.Category.valueOf(r.n.id.substring(4)), true));
                    rd.text(cnt, sideX + sideW - 8 - rd.width(cnt), r.y + ty - 1, selected ? Theme.onColor(a) : Theme.FAINT, false);
                }
            } else {
                int col = selected ? Theme.onColor(a) : inside ? Theme.TEXT : Theme.mix(Theme.MUTED, Theme.TEXT, g);
                MenuIcons.draw(rd, r.n.icon, sideX + 8, r.y + ty - 1, col);
                rd.text(rd.trim(r.n.label, sideW - 36), sideX + 21, r.y + ty, col, false);
                if (r.n.kids.length > 0) {
                    Glass.chevron(rd, sideX + sideW - 12, r.y + ty + 1, r.open, selected ? col : Theme.withAlpha(col, 0xB0));
                }
            }
        }
    }

    private boolean isSelected(Node n) {
        return n.id.equals(section) || (n.id.equals("modules") && section.equals("cat:all"));
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
        int track = Theme.mix(hover ? 0x60FFFFFF : 0x38FFFFFF, accent, on);
        d.roundRect(x, y, 24, 11, 5, track);
        int kx = x + 1 + Math.round(on * 13);
        d.roundRect(kx, y + 1, 9, 9, 4, on > 0.5f ? 0xFFFFFFFF : 0xFFC9CDD6);
        if (on > 0.05f) {
            d.roundOutline(x - 1, y - 1, 26, 13, 6, Theme.withAlpha(accent, Math.round(0x50 * on)));
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

    private int settingsH;

    private void renderSettingsView(Draw d, int mx, int my, float dt, int a) {
        hits.clear();
        maxScroll = Math.max(0, settingsH - listH);
        scroll = clamp(scroll, 0, maxScroll);
        smoothScroll += (scroll - smoothScroll) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smoothScroll) < 0.5f) {
            smoothScroll = scroll;
        }
        d.popScale();
        d.clip(s(listX), s(listY), s(listW), s(listH));
        d.pushScale(0, 0, k);
        int x = listX, w = listW, y = listY - Math.round(smoothScroll);
        int top = y;
        y = settingsRows(d, mx, my, dt, a, x, y, w);
        settingsH = y - top;
        d.popScale();
        d.unclip();
        d.pushScale(0, 0, k);
        // only what's in view can be clicked
        hits.removeIf(h -> h.y + h.h / 2 < listY || h.y + h.h / 2 > listY + listH);
        if (maxScroll > 0) {
            scrollbar(d, listY, listH, in(mx, my, listX, listY, listW + 8, listH), a);
        }
    }

    private int settingsRows(Draw d, int mx, int my, float dt, int a, int x, int y, int w) {
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
        // menu background: solid, see-through or none
        d.text("Menu background", x + 6, y + 7, Theme.MUTED, false);
        int bx = x + w - 6;
        for (int i = Peregrine.MENU_BACKGROUNDS.length - 1; i >= 0; i--) {
            String o = Peregrine.MENU_BACKGROUNDS[i];
            int ow = d.width(o) + 12;
            bx -= ow;
            boolean on = pc.menuBackground() == i;
            boolean hv = in(mx, my, bx, y + 3, ow, 16);
            d.roundRect(bx, y + 3, ow, 16, on ? a : hv ? Theme.CARD_HOVER : Theme.CARD);
            if (!on) {
                d.roundOutline(bx, y + 3, ow, 16, Theme.HAIRLINE);
            }
            d.text(o, bx + 6, y + 7, on ? Theme.onColor(a) : hv ? Theme.TEXT : Theme.MUTED, false);
            hit("menubg:" + i, bx, y + 3, ow, 16, null);
            bx -= 3;
        }
        y += SET_H + 2;
        y = moduleRow(d, mx, my, dt, a, "main_menu", "Peregrine main menu", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "pause_menu", "Peregrine pause menu", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "clean_menus", "Keep resource packs out of menus", x, y, w);
        y += 8;
        heading(d, "Game", x, y);
        y += 16;
        y = moduleRow(d, mx, my, dt, a, "stretched", "Stretched resolution", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "place_animation", "Blocks grow when placed", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "block_outline", "Rainbow block outline", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "cosmetics", "Capes and badges", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "smooth_inventory", "Smooth inventory", x, y, w);
        y = moduleRow(d, mx, my, dt, a, "motion_blur", "Motion blur", x, y, w);
        y += 8;
        heading(d, "Reset", x, y);
        y += 16;
        y = actionRow(d, mx, my, a, "reset-hud", "HUD layout", "Reset positions & looks", x, y, w);
        y = actionRow(d, mx, my, a, "clear-keys", "Keybinds", "Clear them all", x, y, w);
        return y;
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
            } else if (st instanceof TextSetting) {
                TextSetting ts = (TextSetting) st;
                boolean focused = typing == ts;
                int fw = Math.max(70, Math.min(150, w / 2 - 8));
                int bx = right - fw;
                boolean fh = inView && in(mx, my, bx, y + 3, fw, 16);
                d.roundRect(bx, y + 3, fw, 16, Theme.FIELD);
                d.roundOutline(bx, y + 3, fw, 16, focused ? Theme.withAlpha(a, 0xE0) : fh ? Theme.withAlpha(a, 0x90) : Theme.HAIRLINE);
                boolean caret = focused && (now / 500) % 2 == 0;
                String shown = ts.value.isEmpty() && !focused ? ts.placeholder : ts.value;
                // Long text shows its end while typing, its start otherwise.
                String fit = focused ? tail(d, shown, fw - 14) : d.trim(shown, fw - 10);
                d.text(fit + (caret ? "_" : ""), bx + 5, y + 7,
                        ts.value.isEmpty() && !focused ? Theme.FAINT : Theme.TEXT, false);
                hit(id, bx, y + 3, fw, 16, st);
            } else if (st instanceof KeySetting) {
                KeySetting ks = (KeySetting) st;
                boolean waiting = bindingKey == ks;
                String val = waiting ? "Press a key\u2026" : ks.text();
                int bw2 = Math.max(44, d.width(val) + 16);
                int bx = right - bw2;
                boolean bhv = inView && in(mx, my, bx, y + 3, bw2, 16);
                float pulse = waiting ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
                d.roundRect(bx, y + 3, bw2, 16, waiting ? Theme.withAlpha(a, 0x30 + Math.round(0x30 * pulse))
                        : bhv ? Theme.ROW_HOVER : Theme.FIELD);
                d.roundOutline(bx, y + 3, bw2, 16, waiting || bhv ? Theme.withAlpha(a, 0xC0) : Theme.HAIRLINE);
                d.text(val, bx + (bw2 - d.width(val)) / 2, y + 7, waiting ? a : ks.key >= 0 ? Theme.TEXT : Theme.FAINT, false);
                hit(id, bx, y + 3, bw2, 16, st);
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

    /** The end of a text that fits in maxW (what you're typing stays in view). */
    private static String tail(Draw d, String s, int maxW) {
        if (d.width(s) <= maxW) {
            return s;
        }
        int i = 0;
        while (i < s.length() && d.width("\u2026" + s.substring(i)) > maxW) {
            i++;
        }
        return "\u2026" + s.substring(i);
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
        if (typing != null || bindingKey != null) {
            boolean onIt = false;
            for (Hit h : hits) {
                if ((h.setting == typing || h.setting == bindingKey) && in(mx, my, h.x, h.y, h.w, h.h)) {
                    onIt = true;
                }
            }
            if (!onIt) {
                if (typing != null) {
                    pc.markDirty();
                }
                typing = null;
                bindingKey = null;
            }
        }
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
            } else if (h.setting instanceof TextSetting) {
                typing = typing == h.setting ? null : (TextSetting) h.setting;
                bindingKey = null;
                return true;
            } else if (h.setting instanceof KeySetting) {
                bindingKey = bindingKey == h.setting ? null : (KeySetting) h.setting;
                typing = null;
                return true;
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
        if (typing != null) {
            pc.markDirty();
        }
        typing = null;
        bindingKey = null;
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
        for (Hit h : new ArrayList<Hit>(sideHits)) {
            if (in(mx, my, h.x, h.y, h.w, h.h)) {
                Node n = node(h.id);
                if (n != null && n.kids.length > 0) {
                    toggleGroup(n);
                } else {
                    choose(h.id);
                }
                return true;
            }
        }
        if (view == 0 && in(mx, my, menuBtnX, tabsY, TABBAR_H, TABBAR_H)) {
            onlyOn = !onlyOn;
            listShownAt = System.currentTimeMillis();
            scroll = 0;
            return true;
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
        if (view == 3) {
            MenuPanel pn = panel();
            if (pn != null) {
                pn.click(mx, my, button);
            }
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
            } else if (h.id.startsWith("menubg:")) {
                pc.setMenuBackground(Integer.parseInt(h.id.substring(7)));
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

    private static Node node(String id) {
        for (Node n : SIDEBAR) {
            if (n != null && n.id.equals(id)) {
                return n;
            }
        }
        return null;
    }

    public boolean mouseDragged(int screenMx, int screenMy) {
        MenuPanel pn = editingHud ? null : panel();
        if (pn instanceof LockerPanel && ((LockerPanel) pn).dragged(Math.round(screenMx / k))) {
            return true;
        }
        if (draggingSlider != null && !editingHud) {
            slideTo(draggingSlider, Math.round(screenMx / k));
            return true;
        }
        return editingHud && editor.mouseDragged(screenMx, screenMy);
    }

    public boolean mouseReleased() {
        MenuPanel pn = editingHud ? null : panel();
        if (pn instanceof LockerPanel && ((LockerPanel) pn).released(Math.round(lastMx / k))) {
            return true;
        }
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
        MenuPanel pn = page == null ? panel() : null;
        if (pn != null) {
            pn.scrolled(amount);
            return true;
        }
        scroll -= (int) Math.round(amount * (CARD_H + GAP) * 0.6);
        scroll = clamp(scroll, 0, maxScroll);
        return true;
    }

    /** Returns true if the key was used. Esc is used by the HUD editor and settings pages (to go back). */
    public boolean keyPressed(int key) {
        if (bindingKey != null) {
            if (key == KEY_BACKSPACE || key == Keys.KEY_DELETE) {
                bindingKey.key = Keys.NONE;
            } else if (key != KEY_ESCAPE) {
                bindingKey.key = key;
                boundAt = System.currentTimeMillis();
            }
            bindingKey = null;
            pc.markDirty();
            return true;
        }
        if (typing != null) {
            if (key == KEY_BACKSPACE) {
                typing.backspace();
            } else if (key == KEY_ESCAPE || key == KEY_ENTER) {
                typing = null;
                pc.markDirty();
            }
            return true;  // every other key just types (via charTyped)
        }
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
        MenuPanel own = panel();
        if (own != null && own.key(key)) {
            return true;
        }
        if (key == KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            scroll = 0;
            smoothScroll = 0;
            MenuPanel pn = panel();
            if (pn != null) {
                pn.searchChanged(search);
            }
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
        if (binding != null || bindingKey != null) {
            return true;
        }
        if (typing != null) {
            typing.type(c);
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
        MenuPanel pn = panel();
        if (pn != null && pn.typed(c)) {
            return true;
        }
        if (pn != null) {  // a page with its own search (Browse searches Modrinth)
            if (pn.placeholder().isEmpty()) {
                return false;
            }
            search += c;
            pn.searchChanged(search);
            return true;
        }
        if (view != 0) {
            choose("cat:all");
        }
        search += c;
        scroll = 0;
        smoothScroll = 0;  // results start at the top straight away
        return true;
    }
}
