package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A page of the Right Shift menu drawn in the space right of the sidebar (Mods, Shaders,
 * Resource packs, Locker, Friends). Works in menu pixels; the menu clips and scales.
 */
abstract class MenuPanel {

    /** Something clickable from the last frame. */
    static final class Spot {
        final String id;
        final int x, y, w, h;

        Spot(String id, int x, int y, int w, int h) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean has(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    protected final Peregrine pc;
    protected final Menu menu;
    protected final List<Spot> spots = new ArrayList<Spot>();
    protected final Map<String, Float> glow = new HashMap<String, Float>();
    protected int scroll, maxScroll;
    protected float smooth;
    protected long shownAt;
    protected int x, y, w, h;   // this frame's area

    MenuPanel(Peregrine pc, Menu menu) {
        this.pc = pc;
        this.menu = menu;
    }

    /** Called when the page is opened (again). */
    void shown() {
        shownAt = System.currentTimeMillis();
        scroll = 0;
        smooth = 0;
    }

    abstract void draw(Draw d, int mx, int my, float dt, long now, int accent);

    /** True if the click did something. */
    abstract boolean clicked(String id, int mx, int my, int button);

    final void render(Draw d, int mx, int my, int x, int y, int w, int h, float dt, long now, int accent) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        spots.clear();
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        smooth += (scroll - smooth) * Math.min(1f, dt * 18f);
        if (Math.abs(scroll - smooth) < 0.5f) {
            smooth = scroll;
        }
        draw(d, mx, my, dt, now, accent);
    }

    final boolean click(int mx, int my, int button) {
        for (int i = spots.size() - 1; i >= 0; i--) {  // what was drawn last is on top
            Spot s = spots.get(i);
            if (s.has(mx, my) && clicked(s.id, mx, my, button)) {
                return true;
            }
        }
        return false;
    }

    void scrolled(double amount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(amount * 24)));
    }

    /** A typed character, for a page with its own text box. True if it was used. */
    boolean typed(char c) {
        return false;
    }

    /** A key (Backspace, Enter...), for a page with its own text box. True if it was used. */
    boolean key(int key) {
        return false;
    }

    /** The menu's search text changed. */
    void searchChanged(String text) {
    }

    String placeholder() {
        return "Search...";
    }

    // ---- helpers

    protected Spot spot(String id, int x, int y, int w, int h) {
        Spot s = new Spot(id, x, y, w, h);
        spots.add(s);
        return s;
    }

    protected float glow(String id, boolean on, float dt) {
        Float g = glow.get(id);
        float v = Glass.ease(g == null ? 0f : g, on, dt);
        glow.put(id, v);
        return v;
    }

    /** Rows fade and rise in one after another when the page opens. */
    protected float appear(int i) {
        return FadeDraw.progress(shownAt + Math.min(i, 14) * 22L, 200);
    }

    protected static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static final Map<String, String> TEXTURES = new HashMap<String, String>();

    /** A picture file as a texture (loaded once). */
    protected String texture(String file) {
        if (file == null) {
            return null;
        }
        String id = TEXTURES.get(file);
        if (id == null && !TEXTURES.containsKey(file)) {
            id = pc.platform().loadImage(java.nio.file.Paths.get(file));
            TEXTURES.put(file, id);
        }
        return id;
    }

    /** An icon, or the first letter on an accent tile when there's no picture. */
    protected void icon(Draw d, String file, String name, int x, int y, int s, int accent) {
        String tex = texture(file);
        if (tex != null) {
            d.texture(tex, x, y, s, s, 0f, 0f, 1f, 1f, 0xFFFFFFFF);
            return;
        }
        d.roundRect(x, y, s, s, 4, Theme.withAlpha(accent, 0x46));
        d.roundOutline(x, y, s, s, 4, Theme.withAlpha(accent, 0x70));
        String letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        d.text(letter, x + (s - d.width(letter)) / 2 + 1, y + (s - 8) / 2 + 1, Theme.TEXT, false);
    }

    /** A small trash can (9 x 10). */
    protected static void trash(Draw d, int x, int y, int c) {
        if (d.hasTextures()) {
            MenuIcons.draw(d, "trash", x - 1, y - 1, 11, c);
            return;
        }
        d.rect(x + 3, y, 3, 1, c);
        d.rect(x, y + 1, 9, 1, c);
        d.rect(x + 1, y + 3, 1, 7, c);
        d.rect(x + 7, y + 3, 1, 7, c);
        d.rect(x + 1, y + 9, 7, 1, c);
        d.rect(x + 3, y + 4, 1, 4, c);
        d.rect(x + 5, y + 4, 1, 4, c);
    }

    /** Three dots taking turns, for "working on it". */
    protected static void dots(Draw d, int x, int y, long now, int c) {
        for (int i = 0; i < 3; i++) {
            float k = 0.35f + 0.65f * (float) Math.max(0, Math.sin((now / 160.0) - i * 0.9));
            int col = Theme.withAlpha(c, Math.round(0xFF * k));
            if (d.hasTextures()) {
                d.texture(Art.round(2), x + i * 4, y, 3, 3, 0f, 0f, 1f, 1f, col);
            } else {
                d.rect(x + i * 4, y, 2, 2, col);
            }
        }
    }

    /** A notice across the top of a page. Returns its height. */
    protected int banner(Draw d, int x, int y, int w, String text, int accent) {
        String t = d.trim(text, w - 20);
        d.roundRect(x, y, w, 16, 4, Theme.withAlpha(accent, 0x30));
        d.roundOutline(x, y, w, 16, 4, Theme.withAlpha(accent, 0x90));
        d.rect(x + 6, y + 6, 3, 3, accent);
        d.text(t, x + 13, y + 4, Theme.TEXT, false);
        return 20;
    }
}
