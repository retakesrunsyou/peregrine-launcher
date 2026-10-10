package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class ServerHud extends LineHud {

    public ServerHud() {
        super("server", "Server address", "Which server you're on (hidden in singleplayer)", "Server", 0f, 0.315f);
    }

    @Override
    String value(Platform p) {
        return p.serverAddress();
    }
}
