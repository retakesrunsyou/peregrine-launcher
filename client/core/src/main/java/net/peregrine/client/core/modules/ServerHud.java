package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

public final class ServerHud extends HudModule {

    public ServerHud() {
        super("server", "Server address", "Which server you're on (hidden in singleplayer)", false, 0f, 0.315f);
    }

    @Override
    public int width(Draw d, Platform p) {
        String s = p.serverAddress();
        return d.width("Server " + (s == null ? "" : s)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        String s = p.serverAddress();
        if (s == null) {
            return;
        }
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "Server", s);
    }

    @Override
    public boolean hasContent(Platform p) {
        return p.serverAddress() != null;
    }
}
