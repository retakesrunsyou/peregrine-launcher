package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class PlayersHud extends LineHud {

    public PlayersHud() {
        super("players", "Players online", "How many players are on the server", "Players", 1f, 0.48f);
    }

    @Override
    String value(Platform p) {
        int n = p.onlinePlayers();
        return n < 0 ? null : String.valueOf(n);
    }
}
