package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ActionSetting;

/** A stopwatch, started and stopped from its settings page. */
public final class StopwatchHud extends LineHud {

    private long started;   // when it was last started, or 0 while stopped
    private long elapsed;   // time banked from earlier runs

    public StopwatchHud() {
        super("stopwatch", "Stopwatch", "Time anything; start and stop it from the gear", "Timer", 1f, 0.54f);
        add(new ActionSetting("start", "Stopwatch", () -> started != 0 ? "Stop" : "Start", () -> {
            if (started != 0) {
                elapsed += System.currentTimeMillis() - started;
                started = 0;
            } else {
                started = System.currentTimeMillis();
            }
        }));
        add(new ActionSetting("reset", "Back to zero", () -> "Reset", () -> {
            elapsed = 0;
            started = started != 0 ? System.currentTimeMillis() : 0;
        }));
    }

    long millis() {
        return elapsed + (started != 0 ? System.currentTimeMillis() - started : 0);
    }

    @Override
    String value(Platform p) {
        long ms = millis();
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d.%d", s / 60, s % 60, ms / 100 % 10);
    }
}
