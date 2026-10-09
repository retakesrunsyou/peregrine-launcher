package net.peregrine.client.core.modules;

import java.text.SimpleDateFormat;
import java.util.Date;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;

public final class ClockHud extends HudModule {

    private final SimpleDateFormat format12 = new SimpleDateFormat("h:mm a");
    private final SimpleDateFormat format24 = new SimpleDateFormat("HH:mm");
    private final net.peregrine.client.core.settings.BoolSetting h24 = add(
            new net.peregrine.client.core.settings.BoolSetting("24h", "24-hour clock", false));

    public ClockHud() {
        super("clock", "Clock", "The real-world time", false, 1f, 0f);
    }

    private String value() {
        return (h24.value ? format24 : format12).format(new Date());
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width(value()) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        d.text(value(), x + 4, y + 4, Theme.TEXT, true);
    }
}
