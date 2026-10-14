package net.peregrine.client.core.modules;

import java.util.ArrayList;
import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Better F3: a clean, readable debug screen with just what you pick, in two
 * tidy panels, instead of Minecraft's wall of text.
 */
public final class BetterF3 extends Module {

    private final BoolSetting position = add(new BoolSetting("position", "Position, block and chunk", true));
    private final BoolSetting facing = add(new BoolSetting("facing", "Facing (with degrees)", true));
    private final BoolSetting world = add(new BoolSetting("world", "Biome, light and dimension", true));
    private final BoolSetting time = add(new BoolSetting("time", "Day and time", true));
    private final BoolSetting target = add(new BoolSetting("target", "What you're looking at", true));
    private final BoolSetting perf = add(new BoolSetting("perf", "FPS and memory", true));
    private final BoolSetting server = add(new BoolSetting("server", "Server, ping and players", true));
    private final BoolSetting game = add(new BoolSetting("game", "Game version", false));
    private final SliderSetting size = add(new SliderSetting("size", "Size", 50f, 120f, 5f, 80f, "%.0f%%"));

    public BetterF3() {
        super("better_f3", "Better F3", "A clean F3 screen with just the lines you pick", Category.UTILITY, false);
    }

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    private List<String[]> left(Platform p) {
        List<String[]> out = new ArrayList<String[]>();
        if (position.value) {
            out.add(new String[] {"XYZ", fmt(p.x()) + "  " + fmt(p.y()) + "  " + fmt(p.z())});
            int bx = (int) Math.floor(p.x()), by = (int) Math.floor(p.y()), bz = (int) Math.floor(p.z());
            out.add(new String[] {"Block", bx + "  " + by + "  " + bz});
            out.add(new String[] {"Chunk", (bx >> 4) + "  " + (bz >> 4) + "   in " + (bx & 15) + " " + (bz & 15)});
        }
        if (facing.value) {
            float yaw = ((p.yaw() % 360) + 360) % 360;
            out.add(new String[] {"Facing", p.facing() + "  (" + Math.round(CompassHud.heading(p.yaw())) + "°, pitch "
                    + Math.round(p.pitch()) + "°)"});
        }
        if (world.value) {
            out.add(new String[] {"Biome", BiomeHud.pretty(p.biome())});
            int light = p.lightLevel();
            if (light >= 0) {
                out.add(new String[] {"Light", String.valueOf(light)});
            }
            String dim = p.dimension();
            out.add(new String[] {"World", dim.isEmpty() ? "Overworld" : dim.substring(dim.indexOf(':') + 1).replace('_', ' ')});
        }
        if (time.value) {
            long t = p.worldTime();
            int hour = (int) ((t / 1000 + 6) % 24), min = (int) ((t % 1000) * 60 / 1000);
            out.add(new String[] {"Day", p.worldDay() + "   " + String.format("%02d:%02d", hour, min)});
        }
        if (target.value) {
            String b = p.lookedAtBlock();
            if (b != null && !b.isEmpty()) {
                out.add(new String[] {"Looking at", b});
            }
            String t = p.targetName();
            if (t != null && !t.isEmpty()) {
                out.add(new String[] {"Target", t + "  " + fmt(p.targetDistance()) + "m"});
            }
        }
        return out;
    }

    private List<String[]> right(Platform p) {
        List<String[]> out = new ArrayList<String[]>();
        if (perf.value) {
            out.add(new String[] {"FPS", String.valueOf(p.fps())});
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
            out.add(new String[] {"Memory", used + " / " + max + " MB  " + (used * 100 / Math.max(1, max)) + "%"});
            int e = p.entityCount();
            if (e >= 0) {
                out.add(new String[] {"Entities", String.valueOf(e)});
            }
        }
        if (server.value) {
            String s = p.serverAddress();
            out.add(new String[] {"Server", s == null ? "Singleplayer" : s});
            if (p.ping() >= 0) {
                out.add(new String[] {"Ping", p.ping() + " ms"});
            }
            if (p.onlinePlayers() >= 0) {
                out.add(new String[] {"Players", String.valueOf(p.onlinePlayers())});
            }
        }
        if (game.value) {
            out.add(new String[] {"Minecraft", p.minecraftVersion()});
            out.add(new String[] {"Java", System.getProperty("java.version")});
        }
        return out;
    }

    private void panel(Draw d, List<String[]> lines, int x, int y, boolean alignRight, int screenW) {
        if (lines.isEmpty()) {
            return;
        }
        int lw = 0, vw = 0;
        for (String[] l : lines) {
            lw = Math.max(lw, d.width(l[0]));
            vw = Math.max(vw, d.width(l[1]));
        }
        int pad = 6, gap = 10, row = d.lineHeight() + 3;
        int w = pad * 2 + lw + gap + vw, h = pad * 2 + lines.size() * row - 3;
        int px = alignRight ? screenW - x - w : x;
        int a = Peregrine.get().accent();
        d.roundRect(px, y, w, h, 5, 0xC0101218);
        d.roundOutline(px, y, w, h, 5, 0x30FFFFFF);
        d.rect(px + 5, y, w - 10, 1, Theme.withAlpha(a, 0xB0));
        int ly = y + pad;
        for (String[] l : lines) {
            d.text(l[0], px + pad, ly, a, false);
            d.text(l[1], px + pad + lw + gap, ly, Theme.TEXT, false);
            ly += row;
        }
    }

    /** Draws the panels (called while F3 is open). */
    public void draw(Draw d, Platform p) {
        float s = size.value / 100f;
        int sw = Math.round(p.screenWidth() / s);
        d.pushScale(0, 0, s);
        panel(d, left(p), 6, 6, false, sw);
        panel(d, right(p), 6, 6, true, sw);
        d.popScale();
        if (Hooks.f3Hits < 1000) {
            Hooks.f3Hits++;
        }
    }
}
