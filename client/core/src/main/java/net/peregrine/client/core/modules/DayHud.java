package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

public final class DayHud extends HudModule {

    public DayHud() {
        super("day", "World clock", "The in-game day and time", false, 1f, 0.07f);
    }

    /** Minecraft ticks (0 = 6:00) to a 24-hour clock. */
    static String time(long ticks) {
        long t = (ticks + 6000) % 24000;
        return String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    private static String value(Platform p) {
        return p.worldDay() + "   " + time(p.worldTime());
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("Day " + value(p)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "Day", value(p));
    }
}
