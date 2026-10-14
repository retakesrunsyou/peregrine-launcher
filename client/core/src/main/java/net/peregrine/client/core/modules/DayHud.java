package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class DayHud extends LineHud {

    public DayHud() {
        super("day", "World clock", "The in-game day and time", "Day", 1f, 0.07f);
    }

    /** Minecraft ticks (0 = 6:00) to a 24-hour clock. */
    static String time(long ticks) {
        long t = (ticks + 6000) % 24000;
        return String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    @Override
    String value(Platform p) {
        return p.worldDay() + "   " + time(p.worldTime());
    }
}
