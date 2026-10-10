package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

/** How long you've been in this world or server. */
public final class SessionHud extends LineHud {

    private long started;
    private long lastTick;

    public SessionHud() {
        super("session", "Session time", "How long you've been playing in this world", "Session", 1f, 0.36f);
    }

    @Override
    public void tick(Platform p) {
        long now = System.currentTimeMillis();
        if (now - lastTick > 5000) {
            started = now;  // a fresh join (ticks stop while you're out of a world)
        }
        lastTick = now;
    }

    @Override
    String value(Platform p) {
        long s = Math.max(0, (System.currentTimeMillis() - (started == 0 ? System.currentTimeMillis() : started)) / 1000);
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }
}
