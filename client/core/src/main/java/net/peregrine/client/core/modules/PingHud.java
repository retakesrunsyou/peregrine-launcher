package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class PingHud extends LineHud {

    public PingHud() {
        super("ping", "Ping", "Your connection delay to the server (hidden in singleplayer)", "Ping", 0f, 0.245f);
    }

    @Override
    String value(Platform p) {
        int ms = p.ping();
        return ms < 0 ? null : ms + " ms";
    }

    @Override
    boolean rated() {
        return true;
    }

    @Override
    int rate(Platform p) {
        int ms = p.ping();
        return ms < 0 ? -1 : ms <= 80 ? 0 : ms <= 160 ? 1 : 2;
    }
}
