package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

/** Java memory in use: if it keeps hitting the max, give the instance more. */
public final class MemoryHud extends HudModule {

    public MemoryHud() {
        super("memory", "Memory", "How much of the game's memory is in use", false, 0f, 0.59f);
    }

    private static String value() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        return used + " / " + max + " MB (" + (used * 100 / Math.max(1, max)) + "%)";
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("Memory 9999 / 99999 MB (100%)") + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "Memory", value());
    }
}
