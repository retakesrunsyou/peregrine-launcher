package net.peregrine.client.core;

import java.util.List;

/**
 * The HUD editor (Edit HUD in the Right Shift menu), like Lunar's:
 *
 *  - drag any HUD item anywhere; it snaps to the screen's centre lines and edges
 *  - drag the corner handle, or scroll over an item, to make it bigger or smaller
 *  - right-click an item for its style: size, text and label colours, RGB text,
 *    background on/off and how see-through it is, text shadow, reset or hide
 *  - right-click empty space for Reset all and Done; Esc or Enter saves and leaves
 *
 * Nothing sits across the top of the screen, so every spot can be used.
 *
 * HUD items live in screen (GUI) pixels. The editor's own buttons and panel are
 * drawn at the menu's finer scale (k), so they stay small and tidy.
 */
final class HudEditor {

    private enum Drag { NONE, MOVE, RESIZE, SIZE_SLIDER, ALPHA_SLIDER, SPEED_SLIDER }

    /** Colour choices; 0 means "default" (white text, accent labels). */
    static final int[] COLORS = {
        0, 0xFFFFFFFF, 0xFFAAB0BD, 0xFFFF5C5C, 0xFFFFA54F, 0xFFFFE066,
        0xFF6BE38B, 0xFF5CE1E6, 0xFF6FA8FF, 0xFFFF7AD9,
    };

    private static final int SNAP = 4;
    private static final int HANDLE = 6;
    private static final int PANEL_W = 136;
    private static final int SW = 10;      // colour swatch size
    private static final int SW_GAP = 2;

    private final Peregrine pc;
    private HudModule selected;
    private boolean styleOpen;
    private Drag drag = Drag.NONE;
    private int dragDX, dragDY;
    private float baseW, baseH;   // the selected item's size at 100%, while resizing
    private int anchorX, anchorY; // its top-left, kept still while resizing
    private int guideX = -1, guideY = -1;
    private boolean exit;
    private float k = 1f;

    // Layout from the last frame (UI units = screen / k), for clicks.
    private int doneX, doneY, doneW, doneH;   // the Back button when not in a world
    private boolean menuOpen;                 // right-click on empty space
    private int menuX, menuY;
    private static final int MENU_W = 92, MENU_ROW = 15;
    private static final String[] MENU_ITEMS = {"Done", "Reset all items"};
    private int panelX, panelY, panelH;
    private int panelY0, panelSw;
    private float panelScale;
    private HudModule panelFor;
    private int closeX, closeY;
    private int sizeY, textY, labelY, chromaY, speedY, bgY, alphaY, shadowY, buttonsY;
    private int sliderX, sliderW;

    HudEditor(Peregrine pc) {
        this.pc = pc;
    }

    void open() {
        panelFor = null;
        selected = null;
        styleOpen = false;
        menuOpen = false;
        drag = Drag.NONE;
        exit = false;
        guideX = guideY = -1;
    }

    /** Opens with one item picked and its style panel showing (from its settings page). */
    void openFor(HudModule h) {
        open();
        if (h.enabled()) {
            selected = h;
            styleOpen = true;
        }
    }

    boolean wantsExit() {
        boolean e = exit;
        exit = false;
        return e;
    }

    boolean styleOpen() {
        return styleOpen && selected != null;
    }

    HudModule selected() {
        return selected;
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int ui(int screen) {
        return Math.round(screen / k);
    }

    private int screen(int ui) {
        return Math.round(ui * k);
    }

    // ------------------------------------------------------------ drawing

    void render(Draw d, int mx, int my, float k) {
        this.k = k;
        Platform p = pc.platform();
        int a = pc.accent();
        int sw = p.screenWidth();
        int sh = p.screenHeight();
        if (!p.inWorld()) {
            renderNoWorld(d, mx, my, a);
            return;
        }
        if (selected != null && !selected.enabled()) {
            selected = null;
            styleOpen = false;
        }

        // Faint centre lines help line things up; brighter while something snaps to them.
        d.rect(sw / 2, 0, 1, sh, guideX == sw / 2 ? Theme.withAlpha(a, 0xC0) : 0x14FFFFFF);
        d.rect(0, sh / 2, sw, 1, guideY == sh / 2 ? Theme.withAlpha(a, 0xC0) : 0x14FFFFFF);
        if (guideX >= 0 && guideX != sw / 2) {
            d.rect(guideX, 0, 1, sh, Theme.withAlpha(a, 0xC0));
        }
        if (guideY >= 0 && guideY != sh / 2) {
            d.rect(0, guideY, sw, 1, Theme.withAlpha(a, 0xC0));
        }

        HudModule hover = itemAt(mx, my);
        for (Module m : pc.modules()) {
            if (!(m instanceof HudModule) || !m.enabled()) {
                continue;
            }
            HudModule h = (HudModule) m;
            if (h.lastX < 0) {
                continue;
            }
            boolean sel = h == selected;
            boolean hot = h == hover && drag == Drag.NONE;
            if (!h.hasContent(p)) {
                // Nothing to show yet (like ping in singleplayer): label the box so it isn't blank.
                d.roundRect(h.lastX, h.lastY, h.lastW, h.lastH, Theme.withAlpha(Theme.PANEL, 0x90));
                String label = d.trim(h.name, Math.max(0, h.lastW - 4));
                d.text(label, h.lastX + (h.lastW - d.width(label)) / 2,
                        h.lastY + (h.lastH - d.lineHeight()) / 2 + 1, Theme.MUTED, false);
            }
            if (sel || hot) {
                d.roundRect(h.lastX, h.lastY, h.lastW, h.lastH, Theme.withAlpha(a, sel ? 0x22 : 0x16));
            }
            d.roundOutline(h.lastX, h.lastY, h.lastW, h.lastH,
                    sel ? a : hot ? Theme.withAlpha(a, 0xB0) : Theme.withAlpha(a, 0x48));
            if (sel) {
                int hx = h.lastX + h.lastW - HANDLE / 2 - 1;
                int hy = h.lastY + h.lastH - HANDLE / 2 - 1;
                d.rect(hx - 1, hy - 1, HANDLE + 2, HANDLE + 2, 0xFFFFFFFF);
                d.rect(hx, hy, HANDLE, HANDLE, a);
            }
        }

        // Names and sizes are UI chrome: drawn at the finer menu scale.
        d.pushScale(0, 0, k);
        HudModule tag = selected != null ? selected : hover;
        if (tag != null && tag.lastX >= 0) {
            String text = tag.name + (tag.scale != 1f ? "  " + Math.round(tag.scale * 100) + "%" : "");
            int tw = d.width(text) + 8;
            int tx = ui(tag.lastX);
            int ty = ui(tag.lastY) - 13;
            if (ty < 2) {
                ty = ui(tag.lastY + tag.lastH) + 2;
            }
            d.roundRect(tx, ty, tw, 11, Theme.withAlpha(a, 0xEE));
            d.text(text, tx + 4, ty + 2, 0xFF15171C, false);
        }
        if (styleOpen()) {
            renderPanel(d, mx, my, a, sw, sh);
        }
        if (menuOpen) {
            renderMenu(d, mx, my, a);
        }
        d.popScale();
    }

    private void renderNoWorld(Draw d, int mx, int my, int a) {
        Platform p = pc.platform();
        d.pushScale(0, 0, k);
        int sw = ui(p.screenWidth());
        int sh = ui(p.screenHeight());
        String line1 = "Join a world to arrange your HUD";
        String line2 = "Your HUD items appear here once you're playing.";
        int w = Math.max(d.width(line1), d.width(line2)) + 36;
        int h = 72;
        int x = (sw - w) / 2;
        int y = (sh - h) / 2;
        d.shadow(x, y, w, h);
        d.roundRect(x, y, w, h, Theme.PANEL);
        d.roundOutline(x, y, w, h, Theme.HAIRLINE);
        d.rect(x + 2, y, w - 4, 1, a);
        d.text(line1, x + (w - d.width(line1)) / 2, y + 14, Theme.TEXT, false);
        d.text(line2, x + (w - d.width(line2)) / 2, y + 27, Theme.MUTED, false);
        String back = "Back";
        doneW = d.width(back) + 22;
        doneH = 15;
        doneX = x + (w - doneW) / 2;
        doneY = y + h - doneH - 10;
        boolean hover = in(ui(mx), ui(my), doneX, doneY, doneW, doneH);
        d.roundRect(doneX, doneY, doneW, doneH, Theme.withAlpha(a, hover ? 0xFF : 0xDD));
        d.text(back, doneX + 11, doneY + 4, 0xFF15171C, false);
        d.popScale();
    }

    /** The small menu from right-clicking empty space. */
    private void renderMenu(Draw d, int mx, int my, int a) {
        int umx = ui(mx), umy = ui(my);
        int h = MENU_ITEMS.length * MENU_ROW + 4;
        menuX = Math.min(menuX, ui(pc.platform().screenWidth()) - MENU_W - 2);
        menuY = Math.min(menuY, ui(pc.platform().screenHeight()) - h - 2);
        d.shadow(menuX, menuY, MENU_W, h);
        d.roundRect(menuX, menuY, MENU_W, h, Theme.PANEL);
        d.roundOutline(menuX, menuY, MENU_W, h, Theme.HAIRLINE);
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            int y = menuY + 2 + i * MENU_ROW;
            boolean hv = in(umx, umy, menuX + 2, y, MENU_W - 4, MENU_ROW);
            if (hv) {
                d.roundRect(menuX + 2, y, MENU_W - 4, MENU_ROW, Theme.withAlpha(a, 0x30));
            }
            d.text(MENU_ITEMS[i], menuX + 8, y + 4, hv ? Theme.TEXT : Theme.MUTED, false);
        }
        String esc = "Esc";
        d.text(esc, menuX + MENU_W - 8 - d.width(esc), menuY + 6, Theme.FAINT, false);
    }

    private void layoutPanel(int screenW, int screenH) {
        HudModule s = selected;
        panelH = 166 + (s.chroma ? 18 : 0) + (s.background ? 18 : 0);
        int sw = ui(screenW), sh = ui(screenH);
        // The panel stays put while the item's text changes width (like FPS going
        // from 99 to 100); it only follows when the item is moved or resized.
        if (s != panelFor || drag == Drag.MOVE || drag == Drag.RESIZE || (s.scale != panelScale && drag != Drag.SIZE_SLIDER) || panelSw != sw) {
            int right = ui(s.lastX + s.lastW) + 8;
            int left = ui(s.lastX) - 8 - PANEL_W;
            panelX = right + PANEL_W <= sw - 4 ? right : Math.max(4, left);
            panelY0 = ui(s.lastY);
            panelFor = s;
            panelScale = s.scale;
            panelSw = sw;
        }
        panelY = Math.max(32, Math.min(panelY0, sh - panelH - 4));
        int y = panelY + 24;
        sizeY = y;
        y += 24;
        textY = y;
        y += 24;
        labelY = y;
        y += 24;
        chromaY = y;
        y += 16;
        if (s.chroma) {
            speedY = y;
            y += 18;
        } else {
            speedY = -1000;
        }
        bgY = y;
        y += 16;
        if (s.background) {
            alphaY = y;
            y += 18;
        } else {
            alphaY = -1000;
        }
        shadowY = y;
        y += 18;
        buttonsY = y;
        sliderX = panelX + 8;
        sliderW = PANEL_W - 16;
        closeX = panelX + PANEL_W - 14;
        closeY = panelY + 8;
    }

    private void renderPanel(Draw d, int mx, int my, int a, int screenW, int screenH) {
        layoutPanel(screenW, screenH);
        int umx = ui(mx), umy = ui(my);
        HudModule s = selected;
        int x = panelX, w = PANEL_W;
        d.shadow(x, panelY, w, panelH);
        d.roundRect(x, panelY, w, panelH, Theme.PANEL);
        d.roundOutline(x, panelY, w, panelH, Theme.HAIRLINE);
        d.rect(x + 2, panelY, w - 4, 1, a);
        d.text(d.trim(s.name, w - 30), x + 8, panelY + 8, Theme.TEXT, false);
        boolean ch = in(umx, umy, closeX - 3, closeY - 3, 12, 12);
        int xc = ch ? Theme.TEXT : Theme.MUTED;
        for (int i = 0; i < 6; i++) {
            d.rect(closeX + i, closeY + i, 1, 1, xc);
            d.rect(closeX + 5 - i, closeY + i, 1, 1, xc);
        }
        d.rect(x + 1, panelY + 20, w - 2, 1, Theme.HAIRLINE);

        // Size
        float t = (s.scale - HudModule.MIN_SCALE) / (HudModule.MAX_SCALE - HudModule.MIN_SCALE);
        String pct = Math.round(s.scale * 100) + "%";
        d.text("Size", x + 8, sizeY, Theme.MUTED, false);
        d.text(pct, x + w - 8 - d.width(pct), sizeY, Theme.TEXT, false);
        slider(d, sizeY + 11, t, a);

        swatchRow(d, "Text", textY, s.textColor, Theme.TEXT, umx, umy, a);
        swatchRow(d, "Labels", labelY, s.labelColor, a, umx, umy, a);

        toggleRow(d, "RGB text", chromaY, s.chroma, a);
        if (s.chroma) {
            d.text("Speed", x + 8, speedY, Theme.MUTED, false);
            String sp = String.format("%.1fx", s.chromaSpeed);
            d.text(sp, x + w - 8 - d.width(sp), speedY, Theme.TEXT, false);
            slider(d, speedY + 10, (s.chromaSpeed - HudModule.MIN_CHROMA_SPEED)
                    / (HudModule.MAX_CHROMA_SPEED - HudModule.MIN_CHROMA_SPEED), a);
        }
        toggleRow(d, "Background", bgY, s.background, a);
        if (s.background) {
            d.text("Opacity", x + 8, alphaY, Theme.MUTED, false);
            String op = Math.round(s.backgroundAlpha * 100 / 255f) + "%";
            d.text(op, x + w - 8 - d.width(op), alphaY, Theme.TEXT, false);
            slider(d, alphaY + 10, s.backgroundAlpha / 255f, a);
        }
        toggleRow(d, "Text shadow", shadowY, s.shadow, a);

        int bw = (w - 20) / 2;
        boolean rh = in(umx, umy, x + 8, buttonsY, bw, 14);
        d.roundRect(x + 8, buttonsY, bw, 14, rh ? Theme.ROW_HOVER : 0x00000000);
        d.roundOutline(x + 8, buttonsY, bw, 14, rh ? Theme.MUTED : Theme.HAIRLINE);
        d.text("Reset", x + 8 + (bw - d.width("Reset")) / 2, buttonsY + 3, Theme.TEXT, false);
        int hx = x + 12 + bw;
        boolean hh = in(umx, umy, hx, buttonsY, bw, 14);
        d.roundRect(hx, buttonsY, bw, 14, Theme.withAlpha(Theme.BAD, hh ? 0x40 : 0x18));
        d.roundOutline(hx, buttonsY, bw, 14, Theme.withAlpha(Theme.BAD, hh ? 0xFF : 0x90));
        d.text("Hide", hx + (bw - d.width("Hide")) / 2, buttonsY + 3, Theme.BAD, false);
    }

    private void slider(Draw d, int y, float t, int a) {
        t = Math.max(0f, Math.min(1f, t));
        d.roundRect(sliderX, y, sliderW, 3, 0xFF2F3542);
        int fill = Math.round(sliderW * t);
        if (fill > 0) {
            d.roundRect(sliderX, y, Math.max(3, fill), 3, a);
        }
        int kx = sliderX + fill - 3;
        d.rect(kx + 1, y - 2, 5, 7, 0xFFFFFFFF);
        d.rect(kx, y - 1, 7, 5, 0xFFFFFFFF);
    }

    private void swatchRow(Draw d, String label, int y, int current, int def, int umx, int umy, int a) {
        d.text(label, panelX + 8, y, Theme.MUTED, false);
        int sy = y + 10;
        for (int i = 0; i < COLORS.length; i++) {
            int sx = sliderX + i * (SW + SW_GAP);
            int c = COLORS[i] == 0 ? def : COLORS[i];
            boolean chosen = COLORS[i] == current;
            boolean hover = in(umx, umy, sx, sy, SW, SW);
            if (chosen || hover) {
                d.roundOutline(sx - 1, sy - 1, SW + 2, SW + 2, chosen ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            d.rect(sx + 1, sy + 1, SW - 2, SW - 2, c);
            if (COLORS[i] == 0) {  // "default": marked with a small dot
                d.rect(sx + SW / 2 - 1, sy + SW / 2 - 1, 2, 2, 0xFF15171C);
            }
        }
    }

    private void toggleRow(Draw d, String label, int y, boolean on, int a) {
        d.text(label, panelX + 8, y, Theme.MUTED, false);
        int tx = panelX + PANEL_W - 8 - 18;
        d.roundRect(tx, y - 1, 18, 9, on ? a : 0xFF2F3542);
        int kx = tx + (on ? 10 : 2);
        d.rect(kx, y + 1, 6, 5, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------ input (screen coordinates)

    private HudModule itemAt(int mx, int my) {
        List<Module> mods = pc.modules();
        for (int i = mods.size() - 1; i >= 0; i--) {  // topmost first
            Module m = mods.get(i);
            if (m instanceof HudModule && m.enabled() && ((HudModule) m).contains(mx, my)) {
                return (HudModule) m;
            }
        }
        return null;
    }

    private boolean onHandle(int mx, int my) {
        if (selected == null || selected.lastX < 0) {
            return false;
        }
        int hx = selected.lastX + selected.lastW - HANDLE / 2 - 2;
        int hy = selected.lastY + selected.lastH - HANDLE / 2 - 2;
        return in(mx, my, hx, hy, HANDLE + 4, HANDLE + 4);
    }

    boolean mouseClicked(int mx, int my, int button) {
        Platform p = pc.platform();
        int umx = ui(mx), umy = ui(my);
        if (!p.inWorld()) {
            if (button == 0 && in(umx, umy, doneX, doneY, doneW, doneH)) {
                exit = true;
            }
            return true;
        }
        if (menuOpen) {
            menuOpen = false;
            int h = MENU_ITEMS.length * MENU_ROW + 4;
            if (button == 0 && in(umx, umy, menuX, menuY, MENU_W, h)) {
                int i = (umy - menuY - 2) / MENU_ROW;
                if (i == 0) {
                    exit = true;
                } else if (i == 1) {
                    for (Module m : pc.modules()) {
                        if (m instanceof HudModule) {
                            ((HudModule) m).resetStyle();
                        }
                    }
                    pc.markDirty();
                }
            }
            return true;
        }
        if (styleOpen() && in(umx, umy, panelX, panelY, PANEL_W, panelH)) {
            if (button == 0) {
                clickPanel(umx, umy);
            }
            return true;
        }
        if (button == 0 && onHandle(mx, my)) {
            drag = Drag.RESIZE;
            baseW = selected.lastW / selected.scale;
            baseH = selected.lastH / selected.scale;
            anchorX = selected.lastX;
            anchorY = selected.lastY;
            return true;
        }
        HudModule h = itemAt(mx, my);
        if (h != null) {
            if (h != selected) {
                styleOpen = false;
            }
            selected = h;
            if (button == 1) {
                styleOpen = true;
                panelFor = null;  // place it next to the item afresh
                return true;
            }
            if (button == 0) {
                drag = Drag.MOVE;
                dragDX = mx - h.lastX;
                dragDY = my - h.lastY;
            }
            return true;
        }
        if (button == 0) {  // empty space: let go of the selection
            selected = null;
            styleOpen = false;
        } else if (button == 1) {  // empty space, right-click: Done and Reset all
            menuOpen = true;
            menuX = umx;
            menuY = umy;
        }
        return true;
    }

    private void clickPanel(int umx, int umy) {
        HudModule s = selected;
        if (in(umx, umy, closeX - 3, closeY - 3, 12, 12)) {
            styleOpen = false;
            return;
        }
        if (in(umx, umy, sliderX - 3, sizeY + 6, sliderW + 6, 12)) {
            drag = Drag.SIZE_SLIDER;
            sliderTo(umx);
            return;
        }
        if (s.chroma && in(umx, umy, sliderX - 3, speedY + 5, sliderW + 6, 12)) {
            drag = Drag.SPEED_SLIDER;
            sliderTo(umx);
            return;
        }
        if (in(umx, umy, panelX, chromaY - 3, PANEL_W, 14)) {
            s.chroma = !s.chroma;
            pc.markDirty();
            return;
        }
        if (s.background && in(umx, umy, sliderX - 3, alphaY + 5, sliderW + 6, 12)) {
            drag = Drag.ALPHA_SLIDER;
            sliderTo(umx);
            return;
        }
        int swatch = swatchAt(umx, umy, textY);
        if (swatch >= 0) {
            s.textColor = COLORS[swatch];
            pc.markDirty();
            return;
        }
        swatch = swatchAt(umx, umy, labelY);
        if (swatch >= 0) {
            s.labelColor = COLORS[swatch];
            pc.markDirty();
            return;
        }
        if (in(umx, umy, panelX, bgY - 3, PANEL_W, 14)) {
            s.background = !s.background;
            pc.markDirty();
            return;
        }
        if (in(umx, umy, panelX, shadowY - 3, PANEL_W, 14)) {
            s.shadow = !s.shadow;
            pc.markDirty();
            return;
        }
        int bw = (PANEL_W - 20) / 2;
        if (in(umx, umy, panelX + 8, buttonsY, bw, 14)) {
            s.resetStyle();
            pc.markDirty();
            return;
        }
        if (in(umx, umy, panelX + 12 + bw, buttonsY, bw, 14)) {
            s.setEnabled(false);
            selected = null;
            styleOpen = false;
            pc.markDirty();
        }
    }

    private int swatchAt(int umx, int umy, int rowY) {
        int sy = rowY + 10;
        for (int i = 0; i < COLORS.length; i++) {
            if (in(umx, umy, sliderX + i * (SW + SW_GAP) - 1, sy - 1, SW + 2, SW + 2)) {
                return i;
            }
        }
        return -1;
    }

    private void sliderTo(int umx) {
        float t = Math.max(0f, Math.min(1f, (umx - sliderX) / (float) sliderW));
        if (drag == Drag.SIZE_SLIDER) {
            resize(selected, HudModule.MIN_SCALE + t * (HudModule.MAX_SCALE - HudModule.MIN_SCALE));
        } else if (drag == Drag.ALPHA_SLIDER) {
            selected.backgroundAlpha = Math.round(t * 255);
        } else if (drag == Drag.SPEED_SLIDER) {
            float v = HudModule.MIN_CHROMA_SPEED + t * (HudModule.MAX_CHROMA_SPEED - HudModule.MIN_CHROMA_SPEED);
            selected.chromaSpeed = Math.round(v * 10) / 10f;
        }
        pc.markDirty();
    }

    /**
     * Changes an item's size without it jumping: the side nearest a screen edge stays
     * where it is (an item in the bottom-right corner grows up and to the left), and it
     * can't grow past the screen, so nothing gets pushed back from the border.
     */
    private void resize(HudModule h, float scale) {
        Platform p = pc.platform();
        float w100 = h.lastW / h.scale, h100 = h.lastH / h.scale;
        boolean right = h.fx > 0.5f, bottom = h.fy > 0.5f;
        int edgeX = right ? h.lastX + h.lastW : h.lastX;
        int edgeY = bottom ? h.lastY + h.lastH : h.lastY;
        float fit = Math.min(p.screenWidth() / w100, p.screenHeight() / h100);
        h.setScaleKeepingCorner(Math.min(scale, fit));
        if (h.scale * w100 > p.screenWidth() || h.scale * h100 > p.screenHeight()) {
            h.scale = Math.max(HudModule.MIN_SCALE, (float) Math.floor(fit * 20) / 20f);
        }
        h.lastW = (int) Math.ceil(w100 * h.scale);
        h.lastH = (int) Math.ceil(h100 * h.scale);
        int x = right ? edgeX - h.lastW : edgeX;
        int y = bottom ? edgeY - h.lastH : edgeY;
        x = Math.max(0, Math.min(p.screenWidth() - h.lastW, x));
        y = Math.max(0, Math.min(p.screenHeight() - h.lastH, y));
        h.lastX = x;
        h.lastY = y;
        h.moveTo(x, y, p);
    }

    boolean mouseDragged(int mx, int my) {
        Platform p = pc.platform();
        if (drag == Drag.MOVE && selected != null) {
            int w = selected.lastW, h = selected.lastH;
            int sw = p.screenWidth(), sh = p.screenHeight();
            int x = mx - dragDX, y = my - dragDY;
            guideX = guideY = -1;
            // Snap to the centre lines and the screen edges.
            if (Math.abs(x + w / 2 - sw / 2) <= SNAP) {
                x = sw / 2 - w / 2;
                guideX = sw / 2;
            } else if (Math.abs(x) <= SNAP) {
                x = 0;
            } else if (Math.abs(x + w - sw) <= SNAP) {
                x = sw - w;
            }
            if (Math.abs(y + h / 2 - sh / 2) <= SNAP) {
                y = sh / 2 - h / 2;
                guideY = sh / 2;
            } else if (Math.abs(y) <= SNAP) {
                y = 0;
            } else if (Math.abs(y + h - sh) <= SNAP) {
                y = sh - h;
            }
            // Line up with the other HUD items: their edges and centres.
            if (guideX < 0) {
                int[] snapped = snapTo(x, w, true);
                if (snapped != null) {
                    x = snapped[0];
                    guideX = snapped[1];
                }
            }
            if (guideY < 0) {
                int[] snapped = snapTo(y, h, false);
                if (snapped != null) {
                    y = snapped[0];
                    guideY = snapped[1];
                }
            }
            selected.moveTo(x, y, p);
            pc.markDirty();
            return true;
        }
        if (drag == Drag.RESIZE && selected != null) {
            float wanted = Math.max(8, mx - anchorX);
            int x = anchorX, y = anchorY;
            // Never past the screen edge: it stops growing there instead of being pushed back.
            float fit = Math.min((p.screenWidth() - anchorX) / baseW, (p.screenHeight() - anchorY) / baseH);
            selected.setScaleKeepingCorner(Math.min(wanted / baseW, fit));
            if (selected.scale > fit) {
                selected.scale = Math.max(HudModule.MIN_SCALE, (float) Math.floor(fit * 20) / 20f);
            }
            selected.lastW = (int) Math.ceil(baseW * selected.scale);
            selected.lastH = (int) Math.ceil(baseH * selected.scale);
            selected.lastX = x;
            selected.lastY = y;
            selected.moveTo(x, y, p);
            pc.markDirty();
            return true;
        }
        if (drag == Drag.SIZE_SLIDER || drag == Drag.ALPHA_SLIDER || drag == Drag.SPEED_SLIDER) {
            sliderTo(ui(mx));
            return true;
        }
        return false;
    }

    /**
     * Snaps a position (x or y) so the dragged item's start, middle or end lines up
     * with another item's. Returns {new position, guide line} or null.
     */
    private int[] snapTo(int pos, int size, boolean horizontal) {
        int best = SNAP + 1;
        int[] out = null;
        for (Module m : pc.modules()) {
            if (!(m instanceof HudModule) || m == selected || !m.enabled()) {
                continue;
            }
            HudModule o = (HudModule) m;
            if (o.lastX < 0) {
                continue;
            }
            int start = horizontal ? o.lastX : o.lastY;
            int len = horizontal ? o.lastW : o.lastH;
            int[] lines = {start, start + len / 2, start + len};
            for (int line : lines) {
                int[] mine = {pos, pos + size / 2, pos + size};
                for (int k = 0; k < 3; k++) {
                    int dist = Math.abs(mine[k] - line);
                    if (dist < best) {
                        best = dist;
                        out = new int[] {pos + (line - mine[k]), line};
                    }
                }
            }
        }
        return out;
    }

    boolean mouseReleased() {
        boolean was = drag != Drag.NONE;
        drag = Drag.NONE;
        guideX = guideY = -1;
        return was;
    }

    /** Scrolling over an item (or with one selected) changes its size in 5% steps. */
    boolean mouseScrolled(double amount, int mx, int my) {
        HudModule h = itemAt(mx, my);
        if (h == null) {
            h = selected;
        }
        if (h == null) {
            return false;
        }
        resize(h, h.scale + (amount > 0 ? 0.05f : -0.05f));
        pc.markDirty();
        return true;
    }

    // ------------------------------------------------------------ for the self-test (screen pixels)

    int[] centerOf(String what) {
        if (what.equals("done")) {
            return doneW > 0 && !pc.platform().inWorld()
                    ? new int[] {screen(doneX + doneW / 2), screen(doneY + doneH / 2)} : null;
        }
        if (what.equals("handle") && selected != null) {
            return new int[] {selected.lastX + selected.lastW - 1, selected.lastY + selected.lastH - 1};
        }
        if (what.startsWith("item:")) {
            Module m = pc.module(what.substring(5));
            if (m instanceof HudModule && ((HudModule) m).lastX >= 0) {
                HudModule h = (HudModule) m;
                return new int[] {h.lastX + h.lastW / 2, h.lastY + h.lastH / 2};
            }
            return null;
        }
        if (what.startsWith("text-swatch-") && styleOpen()) {
            int i = Integer.parseInt(what.substring("text-swatch-".length()));
            return new int[] {screen(sliderX + i * (SW + SW_GAP) + SW / 2), screen(textY + 10 + SW / 2)};
        }
        return null;
    }
}
