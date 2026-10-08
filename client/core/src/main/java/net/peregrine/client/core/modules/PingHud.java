package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

public final class PingHud extends HudModule {

    public PingHud() {
        super("ping", "Ping", "Your connection delay to the server (hidden in singleplayer)", false, 0f, 0.245f);
    }

    private String value(Platform p) {
        int ms = p.ping();
        return ms < 0 ? "-" : ms + " ms";
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("Ping " + value(p)) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        if (p.ping() < 0) {
            return;
        }
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "Ping", value(p));
    }

    @Override
    public boolean hasContent(Platform p) {
        return p.ping() >= 0;
    }
}
