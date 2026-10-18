package net.peregrine.client.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Installed or Browse, for mods, shader packs or resource packs. */
final class LibraryPanel extends MenuPanel {

    static final int ROW = 30, BROWSE_ROW = 36, GAP = 4;

    final Library.Kind kind;
    final boolean browse;
    private String filter = "";
    // installed
    private String confirm;        // the item waiting for a second click to remove
    private long confirmAt;
    private List<String> packsOn;  // resource packs switched on (ids), refreshed now and then
    private long packsAt;
    private String shaderNote;
    // browse
    private final List<Library.Result> results = new ArrayList<Library.Result>();
    private volatile boolean loading, more = true;
    private volatile String error;
    private String query = "", asked = null;
    private long typedAt;
    private int requestId;

    LibraryPanel(Peregrine pc, Menu menu, Library.Kind kind, boolean browse) {
        super(pc, menu);
        this.kind = kind;
        this.browse = browse;
    }

    int resultCount() {
        synchronized (results) {
            return results.size();
        }
    }

    @Override
    void shown() {
        super.shown();
        Library.rescan(kind);
        Library.identify(kind);
        packsAt = 0;
    }

    @Override
    String placeholder() {
        return browse ? "Search Modrinth..." : "Filter " + kind.label.toLowerCase(Locale.ROOT) + "...";
    }

    @Override
    void searchChanged(String text) {
        if (browse) {
            query = text;
            typedAt = System.currentTimeMillis();
        } else {
            filter = text.toLowerCase(Locale.ROOT);
            scroll = 0;
        }
    }

    @Override
    void draw(Draw d, int mx, int my, float dt, long now, int a) {
        if (browse) {
            drawBrowse(d, mx, my, dt, now, a);
        } else {
            drawInstalled(d, mx, my, dt, now, a);
        }
    }

    // ------------------------------------------------------------------ installed

    private boolean packOn(Library.Item it) {
        if (kind != Library.Kind.PACKS) {
            return it.enabled;
        }
        if (packsOn == null || System.currentTimeMillis() - packsAt > 1500) {
            packsOn = new ArrayList<String>(pc.platform().selectedPackIds());
            packsAt = System.currentTimeMillis();
        }
        return packsOn != null && packsOn.contains("file/" + it.file);
    }

    private void drawInstalled(Draw d, int mx, int my, float dt, long now, int a) {
        List<Library.Item> all = Library.installed(kind);
        List<Library.Item> items = new ArrayList<Library.Item>();
        for (Library.Item it : all) {
            if (filter.isEmpty() || it.name.toLowerCase(Locale.ROOT).contains(filter)
                    || it.file.toLowerCase(Locale.ROOT).contains(filter)) {
                items.add(it);
            }
        }
        int top = y;
        String title = "Installed " + kind.label.toLowerCase(Locale.ROOT);
        d.textScaled(title, x, top + 1, Theme.TEXT, 1.25f, true);
        d.text(String.valueOf(all.size()), x + Math.round(d.width(title) * 1.25f) + 6, top + 3, a, false);
        // Open folder, on the right
        String of = "Open folder";
        int ow = d.width(of) + 22, ox = x + w - ow;
        float og = glow("open", in(mx, my, ox, top - 1, ow, 14), dt);
        Glass.pill(d, ox, top - 1, ow, 14, false, og, a);
        MenuIcons.draw(d, "folder", ox + 5, top + 2, Theme.mix(Theme.MUTED, Theme.TEXT, og));
        d.text(of, ox + 17, top + 3, Theme.mix(Theme.MUTED, Theme.TEXT, og), false);
        spot("open", ox, top - 1, ow, 14);
        if (kind == Library.Kind.SHADERS) {
            String inUse = Library.shaderInUse();
            if (inUse != null && !inUse.isEmpty()) {
                String off = "Shaders off";
                int fw = d.width(off) + 14, fx = ox - fw - 4;
                float fg = glow("off", in(mx, my, fx, top - 1, fw, 14), dt);
                Glass.pill(d, fx, top - 1, fw, 14, false, fg, a);
                d.text(off, fx + 7, top + 3, Theme.mix(Theme.MUTED, Theme.TEXT, fg), false);
                spot("shaders-off", fx, top - 1, fw, 14);
            }
        }
        top += 20;
        if (kind == Library.Kind.MODS && Library.modsChanged) {
            top += banner(d, x, top, w, "Restart the game to load your mod changes", a);
        }
        if (kind == Library.Kind.SHADERS && !Library.irisPresent()) {
            top += banner(d, x, top, w, "Shaders need Iris: get it in Mods > Browse, then restart", a);
        }
        if (shaderNote != null) {
            top += banner(d, x, top, w, shaderNote, Theme.BAD);
        }
        int listTop = top, listH = y + h - top;
        maxScroll = Math.max(0, items.size() * (ROW + GAP) - GAP - listH);
        if (items.isEmpty()) {
            String none = !Library.scanned(kind) ? "Looking..." : filter.isEmpty()
                    ? "No " + kind.label.toLowerCase(Locale.ROOT) + " yet" : "Nothing matches";
            d.text(none, x + (w - d.width(none)) / 2, listTop + listH / 2 - 10, Theme.MUTED, false);
            if (filter.isEmpty() && Library.scanned(kind)) {
                String hint = "Find some in Browse, or drop files in the folder";
                d.text(hint, x + (w - d.width(hint)) / 2, listTop + listH / 2 + 2, Theme.FAINT, false);
            }
            return;
        }
        String inUse = kind == Library.Kind.SHADERS ? Library.shaderInUse() : null;
        menu.clipArea(d, x, listTop, w + 6, listH);
        for (int i = 0; i < items.size(); i++) {
            Library.Item it = items.get(i);
            int ry = listTop + i * (ROW + GAP) - Math.round(smooth);
            if (ry + ROW < listTop || ry > listTop + listH) {
                continue;
            }
            float ap = appear(i);
            Draw rd = ap < 1f ? new FadeDraw(d, 0.1f + 0.9f * ap) : d;
            ry += Math.round((1f - ap) * 6);
            boolean hover = in(mx, my, x, ry, w, ROW) && in(mx, my, x, listTop, w, listH);
            float g = glow("row:" + it.file, hover, dt);
            boolean on = packOn(it);
            boolean using = inUse != null && inUse.equals(it.file);
            Glass.pane(rd, x, ry, w, ROW, 4, Theme.mix(Glass.PANE, Glass.PANE_HOVER, g));
            if (on && kind != Library.Kind.SHADERS || using) {
                rd.roundRect(x + 1, ry + 5, 2, ROW - 10, 1, a);
            }
            icon(rd, it.iconFile, it.name, x + 6, ry + 5, 20, a);
            int tx = x + 32;
            int right = x + w - 8;
            // controls on the right: remove, then the switch (or Use for shaders)
            if (!it.ours) {
                boolean th = in(mx, my, right - 12, ry + 9, 13, 13);
                boolean asking = it.file.equals(confirm) && now - confirmAt < 3000;
                if (asking) {
                    String sure = "Remove?";
                    int sw = rd.width(sure) + 10;
                    rd.roundRect(right - sw, ry + 8, sw, 14, 4, Theme.withAlpha(Theme.BAD, 0xC0));
                    rd.text(sure, right - sw + 5, ry + 11, 0xFFFFFFFF, false);
                    spot("remove:" + it.file, right - sw, ry + 8, sw, 14);
                    right -= sw + 8;
                } else {
                    trash(rd, right - 10, ry + 10, th ? Theme.BAD : Theme.FAINT);
                    spot("remove:" + it.file, right - 12, ry + 8, 14, 14);
                    right -= 22;
                }
            } else {
                String tag = "Peregrine";
                int tw = rd.width(tag) + 8;
                rd.roundRect(right - tw, ry + 9, tw, 12, 3, Theme.withAlpha(a, 0x38));
                rd.text(tag, right - tw + 4, ry + 11, a, false);
                right -= tw + 8;
            }
            if (kind == Library.Kind.SHADERS) {
                String u = using ? "In use" : "Use";
                int uw = rd.width(u) + 14;
                float ug = glow("use:" + it.file, in(mx, my, right - uw, ry + 8, uw, 14), dt);
                Glass.pill(rd, right - uw, ry + 8, uw, 14, using, ug, a);
                rd.text(u, right - uw + 7, ry + 11, using ? Theme.onColor(a) : Theme.TEXT, false);
                spot("use:" + it.file, right - uw, ry + 8, uw, 14);
                right -= uw + 8;
            } else if (!it.ours) {
                float k = glow("on:" + it.file, on, dt * 1.6f);
                boolean sh = in(mx, my, right - 20, ry + 9, 22, 12);
                Glass.toggle(rd, right - 18, ry + 10, k, a, sh);
                spot("toggle:" + it.file, right - 21, ry + 7, 24, 16);
                right -= 28;
            }
            String name = rd.trim(it.name, right - tx - 4);
            rd.text(name, tx, ry + 6, on || kind == Library.Kind.SHADERS ? Theme.TEXT : Theme.MUTED, false);
            if (!it.version.isEmpty() && rd.width(name) + rd.width(it.version) + 8 < right - tx) {
                rd.text(it.version, tx + rd.width(name) + 5, ry + 6, Theme.FAINT, false);
            }
            String sub = it.description.isEmpty() ? it.file : it.description;
            rd.text(rd.trim(sub, right - tx - 4), tx, ry + 17, Theme.FAINT, false);
        }
        menu.unclipArea(d);
        menu.scrollbar(d, listTop, listH, scroll, maxScroll, smooth, a);
    }

    // ------------------------------------------------------------------ browse

    private void searchNow(final boolean next) {
        final String q = query;
        final String mc = pc.platform().minecraftVersion();
        final int offset = next ? results.size() : 0;
        final int id = ++requestId;
        loading = true;
        error = null;
        if (!next) {
            asked = q;
        }
        Thread t = new Thread(() -> {
            try {
                List<Library.Result> got = Library.search(kind, q, mc, offset);
                synchronized (results) {
                    if (id != requestId) {
                        return;
                    }
                    if (!next) {
                        results.clear();
                    }
                    results.addAll(got);
                    more = got.size() >= 20;
                }
            } catch (Exception e) {
                if (id == requestId) {
                    error = "Couldn't reach Modrinth. Check your internet and try again.";
                    System.out.println("[Peregrine] Modrinth search failed: " + e);
                }
            } finally {
                if (id == requestId) {
                    loading = false;
                }
            }
        }, "Peregrine search");
        t.setDaemon(true);
        t.start();
        if (!next) {
            scroll = 0;
            smooth = 0;
            shownAt = System.currentTimeMillis();
        }
    }

    private void drawBrowse(Draw d, int mx, int my, float dt, long now, int a) {
        if (asked == null || (!query.equals(asked) && now - typedAt > 350)) {
            searchNow(false);
        }
        int top = y;
        String title = query.trim().isEmpty() ? "Popular " + kind.label.toLowerCase(Locale.ROOT) : "Results";
        d.textScaled(title, x, top + 1, Theme.TEXT, 1.25f, true);
        String sub = kind == Library.Kind.MODS ? "Fabric · " + pc.platform().minecraftVersion()
                : kind == Library.Kind.PACKS ? pc.platform().minecraftVersion() : "for Iris";
        d.text(sub, x + Math.round(d.width(title) * 1.25f) + 8, top + 3, Theme.FAINT, false);
        String src = "Modrinth";
        d.text(src, x + w - d.width(src), top + 3, Theme.withAlpha(0xFF1BD96A, 0xE0), false);
        top += 20;
        if (kind == Library.Kind.MODS) {
            if (Library.modsChanged) {
                top += banner(d, x, top, w, "Restart the game to load new mods", a);
            }
        } else if (kind == Library.Kind.SHADERS && !Library.irisPresent()) {
            top += banner(d, x, top, w, "Shaders need Iris: get it in Mods > Browse, then restart", a);
        }
        int listTop = top, listH = y + h - top;
        List<Library.Result> rs;
        synchronized (results) {
            rs = new ArrayList<Library.Result>(results);
        }
        int rowsH = rs.size() * (BROWSE_ROW + GAP) + (more && !rs.isEmpty() ? 24 : 0);
        maxScroll = Math.max(0, rowsH - listH);
        if (rs.isEmpty()) {
            if (loading) {
                dots(d, x + w / 2 - 5, listTop + listH / 2 - 4, now, a);
            } else {
                String none = d.trim(error != null ? error : "Nothing found", w - 10);
                d.text(none, x + (w - d.width(none)) / 2, listTop + listH / 2 - 4, error != null ? Theme.BAD : Theme.MUTED, false);
            }
            return;
        }
        if (more && !loading && scroll >= maxScroll - 30 && maxScroll > 0) {
            searchNow(true);  // near the end: the next page
        }
        menu.clipArea(d, x, listTop, w + 6, listH);
        for (int i = 0; i < rs.size(); i++) {
            Library.Result r = rs.get(i);
            int ry = listTop + i * (BROWSE_ROW + GAP) - Math.round(smooth);
            if (ry + BROWSE_ROW < listTop || ry > listTop + listH) {
                continue;
            }
            float ap = appear(i);
            Draw rd = ap < 1f ? new FadeDraw(d, 0.1f + 0.9f * ap) : d;
            ry += Math.round((1f - ap) * 6);
            boolean hover = in(mx, my, x, ry, w, BROWSE_ROW) && in(mx, my, x, listTop, w, listH);
            float g = glow("r:" + r.project, hover, dt);
            Glass.pane(rd, x, ry, w, BROWSE_ROW, 4, Theme.mix(Glass.PANE, Glass.PANE_HOVER, g));
            icon(rd, Library.webIcon(r.iconUrl), r.title, x + 6, ry + 5, 26, a);
            int tx = x + 38, right = x + w - 8;
            // the install button
            boolean have = Library.has(r.project) || "done".equals(r.state);
            String st = r.state;
            String label = have ? "Installed" : "working".equals(st) ? "" : "Install";
            int bw = Math.max(52, rd.width("Installed") + 18);
            int bx = right - bw, by = ry + 11;
            float bg = glow("i:" + r.project, in(mx, my, bx, by, bw, 14), dt);
            if (have) {
                rd.roundRect(bx, by, bw, 14, 6, Theme.withAlpha(0xFF7BC47F, 0x30));
                rd.roundOutline(bx, by, bw, 14, 6, Theme.withAlpha(0xFF7BC47F, 0xA0));
                MenuIcons.draw(rd, "check", bx + 5, by + 3, 0xFF7BC47F);
                rd.text(label, bx + 16, by + 3, 0xFF7BC47F, false);
            } else {
                Glass.pill(rd, bx, by, bw, 14, true, bg, a);
                if ("working".equals(st)) {
                    dots(rd, bx + bw / 2 - 5, by + 6, now, Theme.onColor(a));
                } else {
                    rd.text(label, bx + (bw - rd.width(label)) / 2, by + 3, Theme.onColor(a), false);
                }
                spot("install:" + r.project, bx, by, bw, 14);
            }
            right = bx - 8;
            String dl = Library.count(r.downloads);
            int dlw = rd.width(dl) + 10;
            MenuIcons.draw(rd, "download", right - dlw - 2, ry + 7, Theme.FAINT);
            rd.text(dl, right - dlw + 9, ry + 8, Theme.FAINT, false);
            int titleRoom = right - dlw - 10 - tx;
            String t = rd.trim(r.title, titleRoom);
            rd.text(t, tx, ry + 7, Theme.TEXT, false);
            if (!r.author.isEmpty() && rd.width(t) + rd.width("by " + r.author) + 8 < titleRoom) {
                rd.text("by " + r.author, tx + rd.width(t) + 5, ry + 7, Theme.FAINT, false);
            }
            boolean failed = !st.isEmpty() && !"working".equals(st) && !"done".equals(st);
            String desc = failed ? st : r.description;
            rd.text(rd.trim(desc, x + w - 8 - tx - (bw + 8)), tx, ry + 20, failed ? Theme.BAD : Theme.MUTED, false);
        }
        if (more) {
            int ly = listTop + rs.size() * (BROWSE_ROW + GAP) - Math.round(smooth) + 6;
            if (loading) {
                dots(d, x + w / 2 - 5, ly + 4, now, a);
            }
        }
        menu.unclipArea(d);
        menu.scrollbar(d, listTop, listH, scroll, maxScroll, smooth, a);
    }

    // ------------------------------------------------------------------ clicks

    @Override
    boolean clicked(String id, int mx, int my, int button) {
        if (id.equals("open")) {
            Library.openFolder(Library.folder(kind));
            return true;
        }
        if (id.equals("shaders-off")) {
            shaderNote = Library.useShader(null);
            return true;
        }
        if (id.startsWith("install:")) {
            String project = id.substring(8);
            synchronized (results) {
                for (Library.Result r : results) {
                    if (r.project.equals(project)) {
                        Library.install(kind, r, pc.platform().minecraftVersion());
                    }
                }
            }
            return true;
        }
        Library.Item it = find(id.substring(id.indexOf(':') + 1));
        if (it == null) {
            return false;
        }
        if (id.startsWith("remove:")) {
            long now = System.currentTimeMillis();
            if (it.file.equals(confirm) && now - confirmAt < 3000) {
                if (kind == Library.Kind.PACKS && packOn(it)) {
                    setPack(it, false);
                }
                Library.remove(kind, it);
                confirm = null;
            } else {
                confirm = it.file;
                confirmAt = now;
            }
            return true;
        }
        if (id.startsWith("toggle:")) {
            if (kind == Library.Kind.PACKS) {
                setPack(it, !packOn(it));
            } else {
                Library.setEnabled(kind, it, !it.enabled);
            }
            return true;
        }
        if (id.startsWith("use:")) {
            String inUse = Library.shaderInUse();
            shaderNote = Library.useShader(it.file.equals(inUse) ? null : it.file);
            return true;
        }
        return false;
    }

    private void setPack(Library.Item it, boolean on) {
        List<String> now = new ArrayList<String>(pc.platform().selectedPackIds());
        String pid = "file/" + it.file;
        now.remove(pid);
        if (on) {
            now.add(pid);  // last = on top
        }
        pc.platform().setSelectedPacks(now);
        packsOn = now;
        packsAt = System.currentTimeMillis();
    }

    private Library.Item find(String file) {
        for (Library.Item it : Library.installed(kind)) {
            if (it.file.equals(file)) {
                return it;
            }
        }
        return null;
    }
}
