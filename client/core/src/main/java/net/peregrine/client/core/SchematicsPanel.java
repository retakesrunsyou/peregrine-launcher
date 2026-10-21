package net.peregrine.client.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Schematics in the Right Shift menu: pick a file to place it at your feet, then move it, turn
 * it, mirror it, build it one layer at a time, and see the blocks you still need.
 */
final class SchematicsPanel extends MenuPanel {

    private List<Path> files;
    private long listedAt;
    private String filter = "";

    SchematicsPanel(Peregrine pc, Menu menu) {
        super(pc, menu);
    }

    @Override
    void shown() {
        super.shown();
        listedAt = 0;
    }

    @Override
    String placeholder() {
        return Schematics.current() == null ? "Find a schematic..." : "";
    }

    @Override
    void searchChanged(String text) {
        filter = text.toLowerCase(Locale.ROOT);
        scroll = 0;
    }

    @Override
    void draw(Draw d, int mx, int my, float dt, long now, int a) {
        if (Schematics.current() == null) {
            drawFiles(d, mx, my, dt, now, a);
        } else {
            drawPlaced(d, mx, my, dt, now, a);
        }
    }

    // ------------------------------------------------------------------ choosing a file

    private void drawFiles(Draw d, int mx, int my, float dt, long now, int a) {
        if (files == null || now - listedAt > 3000) {
            files = Schematics.files();
            listedAt = now;
        }
        d.textScaled("Schematics", x, y + 1, Theme.TEXT, 1.25f, true);
        d.text(String.valueOf(files.size()), x + Math.round(d.width("Schematics") * 1.25f) + 6, y + 3, a, false);
        String of = "Open folder";
        int ow = d.width(of) + 22, ox = x + w - ow;
        float og = glow("open", in(mx, my, ox, y - 1, ow, 14), dt);
        Glass.pill(d, ox, y - 1, ow, 14, false, og, a);
        MenuIcons.draw(d, "folder", ox + 5, y + 2, Theme.mix(Theme.MUTED, Theme.TEXT, og));
        d.text(of, ox + 17, y + 3, Theme.mix(Theme.MUTED, Theme.TEXT, og), false);
        spot("open", ox, y - 1, ow, 14);
        int top = y + 20;
        String st = Schematics.status();
        if (!st.isEmpty()) {
            top += banner(d, x, top, w, st, st.startsWith("Couldn't") ? Theme.BAD : a);
        }
        if (!pc.platform().inWorld()) {
            top += banner(d, x, top, w, "Join a world to place a schematic", a);
        }
        int listTop = top, listH = y + h - top, rowH = 26, gap = 4;
        List<Path> shown = new java.util.ArrayList<Path>();
        for (Path f : files) {
            if (filter.isEmpty() || f.getFileName().toString().toLowerCase(Locale.ROOT).contains(filter)) {
                shown.add(f);
            }
        }
        if (shown.isEmpty()) {
            String t1 = files.isEmpty() ? "No schematics yet" : "Nothing matches";
            d.text(t1, x + (w - d.width(t1)) / 2, listTop + listH / 2 - 10, Theme.MUTED, false);
            if (files.isEmpty()) {
                String t2 = "Put .litematic, .schem or .nbt files in the folder";
                d.text(d.trim(t2, w), x + (w - Math.min(w, d.width(t2))) / 2, listTop + listH / 2 + 2, Theme.FAINT, false);
            }
            return;
        }
        maxScroll = Math.max(0, shown.size() * (rowH + gap) - gap - listH);
        menu.clipArea(d, x, listTop, w + 6, listH);
        for (int i = 0; i < shown.size(); i++) {
            Path f = shown.get(i);
            int ry = listTop + i * (rowH + gap) - Math.round(smooth);
            if (ry + rowH < listTop || ry > listTop + listH) {
                continue;
            }
            float ap = appear(i);
            Draw rd = ap < 1f ? new FadeDraw(d, 0.1f + 0.9f * ap) : d;
            ry += Math.round((1f - ap) * 6);
            boolean hover = in(mx, my, x, ry, w, rowH) && in(mx, my, x, listTop, w, listH);
            float g = glow("f:" + f, hover, dt);
            Glass.pane(rd, x, ry, w, rowH, 4, Theme.mix(Glass.PANE, Glass.PANE_HOVER, g));
            String fn = f.getFileName().toString();
            String ext = fn.substring(fn.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
            int tw = rd.width(ext) + 8;
            rd.roundRect(x + 6, ry + 7, tw, 12, 3, Theme.withAlpha(a, 0x38));
            rd.text(ext, x + 10, ry + 9, a, false);
            String name = fn.substring(0, Math.max(0, fn.lastIndexOf('.')));
            String place = "Place here";
            int pw = rd.width(place) + 14;
            rd.text(rd.trim(name, w - tw - pw - 30), x + tw + 12, ry + 4, Theme.TEXT, false);
            String sub = size(f) + " · " + FriendsPanel.ago(now - modified(f));
            Path rel = Schematics.folder().relativize(f).getParent();
            if (rel != null) {
                sub = rel + " · " + sub;
            }
            rd.text(rd.trim(sub, w - tw - pw - 30), x + tw + 12, ry + 15, Theme.FAINT, false);
            if (pc.platform().inWorld()) {
                float bg = glow("p:" + f, in(mx, my, x + w - pw - 6, ry + 6, pw, 14), dt);
                Glass.pill(rd, x + w - pw - 6, ry + 6, pw, 14, true, Math.max(bg, g * 0.4f), a);
                rd.text(place, x + w - pw + 1, ry + 9, Theme.onColor(a), false);
                spot("load:" + i, x, ry, w, rowH);
            }
        }
        menu.unclipArea(d);
        menu.scrollbar(d, listTop, listH, scroll, maxScroll, smooth, a);
        lastShown = shown;
    }

    private List<Path> lastShown;

    private static String size(Path f) {
        try {
            long b = Files.size(f);
            return b >= 1 << 20 ? String.format(Locale.ROOT, "%.1f MB", b / 1048576.0) : (b / 1024 + 1) + " KB";
        } catch (Exception e) {
            return "";
        }
    }

    private static long modified(Path f) {
        try {
            return Files.getLastModifiedTime(f).toMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------ the placed schematic

    private void button(Draw d, String id, String label, String icon, int bx, int by, int bw, int mx, int my, float dt, int a,
                        boolean on) {
        float g = glow("b:" + id, in(mx, my, bx, by, bw, 15), dt);
        Glass.pill(d, bx, by, bw, 15, on, g, a);
        int fg = on ? Theme.onColor(a) : Theme.mix(Theme.MUTED, Theme.TEXT, g);
        int lw = d.width(label) + (icon != null ? 12 : 0);
        int lx = bx + (bw - lw) / 2;
        if (icon != null) {
            MenuIcons.draw(d, icon, lx, by + 3, fg);
            lx += 12;
        }
        d.text(label, lx, by + 4, fg, false);
        spot(id, bx, by, bw, 15);
    }

    private void drawPlaced(Draw d, int mx, int my, float dt, long now, int a) {
        Schematics.Placement pl = Schematics.current();
        Schematic s = pl.s;
        Platform p = pc.platform();
        // header: its name and size, show/hide and unload on the right
        d.textScaled(d.trim(s.name, Math.round((w - 140) / 1.25f)), x, y + 1, Theme.TEXT, 1.25f, true);
        String dims = pl.sizeX() + " × " + s.sy + " × " + pl.sizeZ() + "  ·  " + s.total + " blocks";
        d.text(d.trim(dims, w - 140), x, y + 15, Theme.FAINT, false);
        String un = "Unload";
        int uw = d.width(un) + 14, ux = x + w - uw;
        float ug = glow("unload", in(mx, my, ux, y, uw, 15), dt);
        d.roundRect(ux, y, uw, 15, 7, Theme.withAlpha(Theme.BAD, Math.round(0x18 + 0x50 * ug)));
        d.roundOutline(ux, y, uw, 15, 7, Theme.mix(Theme.withAlpha(Theme.BAD, 0x90), Theme.BAD, ug));
        d.text(un, ux + 7, y + 4, Theme.mix(0xFFE8B4B4, 0xFFFFFFFF, ug), false);
        spot("unload", ux, y, uw, 15);
        String sh = pl.visible ? "Showing" : "Hidden";
        int sw2 = d.width(sh) + 30, sx = ux - sw2 - 6;
        float shg = glow("vis", in(mx, my, sx, y, sw2, 15), dt);
        Glass.pill(d, sx, y, sw2, 15, false, shg, a);
        Glass.toggle(d, sx + 5, y + 3, glow("visk", pl.visible, dt * 1.6f), a, false);
        d.text(sh, sx + 26, y + 4, Theme.TEXT, false);
        spot("visible", sx, y, sw2, 15);
        // ghost blocks: see-through copies of the blocks still to place
        net.peregrine.client.core.modules.SchematicHud hud = hud();
        if (hud != null) {
            boolean gOn = hud.ghosts.value;
            String gh = "Ghost blocks";
            int gw = d.width(gh) + 30, gx = sx - gw - 6;
            if (gx > x + Math.min(w / 2, d.width(dims) + 8)) {
                float gg = glow("ghost", in(mx, my, gx, y, gw, 15), dt);
                Glass.pill(d, gx, y, gw, 15, false, gg, a);
                Glass.toggle(d, gx + 5, y + 3, glow("ghostk", gOn, dt * 1.6f), a, false);
                d.text(gh, gx + 26, y + 4, pl.visible ? Theme.TEXT : Theme.MUTED, false);
                spot("ghosts", gx, y, gw, 15);
            }
        }
        int top = y + 28;
        if (!Schematics.sameWorld(pl, p)) {
            top += banner(d, x, top, w, "Placed in another world: \"To me\" moves it here", a);
        }

        // progress
        int need = Schematics.needed(), done = Schematics.done(), bad = Schematics.wrong();
        Glass.pane(d, x, top, w, 26, 4, Glass.PANE);
        String prog = done < 0 ? "Too big to count · build with the outlines" : need == 0 ? "Checking..."
                : Math.round(done * 100f / need) + "% built  ·  " + done + " / " + need;
        d.text(prog, x + 7, top + 5, Theme.TEXT, false);
        if (bad > 0) {
            String wr = bad + " wrong";
            d.text(wr, x + w - 7 - d.width(wr), top + 5, Theme.BAD, false);
        }
        int bw = w - 14;
        d.roundRect(x + 7, top + 17, bw, 4, 2, 0x30FFFFFF);
        float frac = need > 0 && done > 0 ? done / (float) need : 0f;
        int fw = Math.round(bw * frac);
        if (fw > 0) {
            d.roundRect(x + 7, top + 17, Math.max(4, fw), 4, 2, a);
        }
        top += 32;

        // controls
        int half = (w - 6) / 2;
        int cx = x, cy = top;
        d.text("Move", cx, cy + 4, Theme.MUTED, false);
        int bx = cx + 42, bwid = (half - 42 - 10) / 3;
        String[][] moves = {{"x-", "X−"}, {"x+", "X+"}, {"y-", "Y−"}, {"y+", "Y+"}, {"z-", "Z−"}, {"z+", "Z+"}};
        for (int i = 0; i < 6; i++) {
            int col = i / 2, row = i % 2;
            button(d, "move:" + moves[i][0], moves[i][1], null, bx + col * (bwid + 5), cy + row * 18, bwid, mx, my, dt, a, false);
        }
        int rx = x + half + 6, rw = w - half - 6;
        d.text("Turn", rx, cy + 4, Theme.MUTED, false);
        int tbx = rx + 32, tbw = (rw - 32 - 5) / 2;
        button(d, "rotate", "Turn", "reset", tbx, cy, tbw, mx, my, dt, a, false);
        button(d, "mirror", "Mirror", "fit", tbx + tbw + 5, cy, tbw, mx, my, dt, a, pl.mirror);
        button(d, "tome", "To me", "person", tbx, cy + 18, rw - 32, mx, my, dt, a, false);
        cy += 40;
        d.text("Layers", cx, cy + 4, Theme.MUTED, false);
        String[] modes = {"All", "One", "Up to"};
        int lbx = cx + 42, lbw = (half - 42 - 10) / 3;
        for (int i = 0; i < 3; i++) {
            button(d, "mode:" + i, modes[i], null, lbx + i * (lbw + 5), cy, lbw, mx, my, dt, a, pl.layerMode == i);
        }
        if (pl.layerMode != 0) {
            button(d, "layer-", "−", null, rx, cy, 18, mx, my, dt, a, false);
            String lv = "Layer " + (pl.layer + 1) + " / " + s.sy;
            d.text(lv, rx + 24 + (rw - 48 - d.width(lv)) / 2, cy + 4, Theme.TEXT, false);
            button(d, "layer+", "+", null, rx + rw - 18, cy, 18, mx, my, dt, a, false);
        } else {
            d.text("Every layer", rx + 4, cy + 4, Theme.FAINT, false);
        }
        cy += 22;

        // the legend for the outline colors
        int lx = x;
        String[] names = {"Missing", "Wrong block", "Turned wrong"};
        int[] colors = {Schematics.MISSING, Schematics.WRONG, Schematics.WRONG_STATE};
        for (int i = 0; i < 3; i++) {
            d.roundRect(lx, cy + 2, 6, 6, 1, colors[i]);
            d.text(names[i], lx + 9, cy + 1, Theme.FAINT, false);
            lx += d.width(names[i]) + 20;
        }
        cy += 14;

        // what's still needed
        List<Object[]> miss = Schematics.missing();
        int listTop = cy, listH = y + h - cy;
        int rowH = 18;
        maxScroll = Math.max(0, miss.size() * rowH - listH);
        if (miss.isEmpty()) {
            String t = done > 0 && done == need ? "All built. Nice work!" : "Missing blocks show up here";
            d.text(t, x + (w - d.width(t)) / 2, listTop + Math.min(30, listH / 2), done > 0 && done == need ? Theme.GOOD : Theme.FAINT, false);
            return;
        }
        menu.clipArea(d, x, listTop, w + 6, listH);
        for (int i = 0; i < miss.size(); i++) {
            int ry = listTop + i * rowH - Math.round(smooth);
            if (ry + rowH < listTop || ry > listTop + listH) {
                continue;
            }
            String block = (String) miss.get(i)[0];
            int count = (Integer) miss.get(i)[1];
            String item = Schematics.itemFor(block);
            int have = p.countItem(item);
            if (i % 2 == 0) {
                d.roundRect(x, ry, w, rowH, 3, 0x10FFFFFF);
            }
            Object stack = p.icon(item);
            if (stack == null) {
                stack = p.icon(block);
            }
            if (stack != null) {
                d.item(stack, x + 3, ry + 1);
            }
            d.text(d.trim(Schematics.pretty(block), w - 120), x + 24, ry + 5, Theme.TEXT, false);
            String need2 = count + " needed";
            String has = have + " in inventory";
            int hc = have >= count ? Theme.GOOD : have > 0 ? Theme.OK : Theme.FAINT;
            d.text(has, x + w - 8 - d.width(has), ry + 5, hc, false);
            d.text(need2, x + w - 16 - d.width(has) - d.width(need2), ry + 5, Theme.MUTED, false);
        }
        menu.unclipArea(d);
        menu.scrollbar(d, listTop, listH, scroll, maxScroll, smooth, a);
    }

    // ------------------------------------------------------------------ clicks

    @Override
    boolean clicked(String id, int mx, int my, int button) {
        Platform p = pc.platform();
        if (id.equals("open")) {
            Library.openFolder(Schematics.folder());
            return true;
        }
        if (id.startsWith("load:") && lastShown != null) {
            int i = Integer.parseInt(id.substring(5));
            if (i < lastShown.size()) {
                Schematics.load(lastShown.get(i), p);
                shownAt = System.currentTimeMillis();
            }
            return true;
        }
        Schematics.Placement pl = Schematics.current();
        if (pl == null) {
            return false;
        }
        if (id.equals("unload")) {
            Schematics.unload();
            shownAt = System.currentTimeMillis();
            listedAt = 0;
        } else if (id.equals("visible")) {
            pl.visible = !pl.visible;
        } else if (id.equals("ghosts")) {
            net.peregrine.client.core.modules.SchematicHud hud = hud();
            if (hud != null) {
                hud.ghosts.value = !hud.ghosts.value;
                pc.markDirty();
            }
        } else if (id.startsWith("move:")) {
            int step = id.endsWith("+") ? 1 : -1;
            char axis = id.charAt(5);
            if (axis == 'x') {
                pl.ox += step;
            } else if (axis == 'y') {
                pl.oy += step;
            } else {
                pl.oz += step;
            }
        } else if (id.equals("rotate")) {
            pl.rotation = (pl.rotation + 1) & 3;
        } else if (id.equals("mirror")) {
            pl.mirror = !pl.mirror;
        } else if (id.equals("tome")) {
            pl.ox = (int) Math.floor(p.x());
            pl.oy = (int) Math.floor(p.y());
            pl.oz = (int) Math.floor(p.z());
            String w2 = p.worldKey();
            pl.world = w2 == null ? "" : w2;
        } else if (id.startsWith("mode:")) {
            pl.layerMode = Integer.parseInt(id.substring(5));
            if (pl.layerMode != 0) {  // start at the layer you're standing on
                pl.layer = Math.max(0, Math.min(pl.s.sy - 1, (int) Math.floor(p.y()) - pl.oy));
            }
        } else if (id.equals("layer-")) {
            pl.layer = Math.max(0, pl.layer - 1);
        } else if (id.equals("layer+")) {
            pl.layer = Math.min(pl.s.sy - 1, pl.layer + 1);
        } else {
            return false;
        }
        Schematics.changed();
        return true;
    }

    private net.peregrine.client.core.modules.SchematicHud hud() {
        Module m = pc.module("schematic");
        return m instanceof net.peregrine.client.core.modules.SchematicHud ? (net.peregrine.client.core.modules.SchematicHud) m : null;
    }
}
