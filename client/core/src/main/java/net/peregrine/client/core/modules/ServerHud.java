package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.ServerIcons;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;

/** The server you're on: its icon, then its name (hidden in singleplayer). */
public final class ServerHud extends HudModule {

    private final BoolSetting showIcon = add(new BoolSetting("icon", "Show server icon", true));
    private final ChoiceSetting show = add(new ChoiceSetting("show", "Show", 0, "Name", "Address", "Name and address"));
    private final BoolSetting divider = add(new BoolSetting("divider", "Line between icon and name", true));

    public ServerHud() {
        super("server", "Server", "The server you're on: its icon and name (hidden in singleplayer)", false, 0f, 0.315f);
    }

    private static String nameOf(Platform p) {
        String name = p.serverName();
        String address = p.serverAddress();
        // direct connect gets the game's placeholder name: the address says more
        if (name == null || name.trim().isEmpty() || name.equals("Minecraft Server")) {
            return address;
        }
        return name;
    }

    private String text(Platform p) {
        String address = p.serverAddress();
        if (address == null) {
            return null;
        }
        switch (show.index) {
            case 1: return address;
            case 2: {
                String name = nameOf(p);
                return name.equals(address) ? address : name + " §7" + address;
            }
            default: return nameOf(p);
        }
    }

    private int[] icon(Platform p) {
        return showIcon.value ? ServerIcons.get(p.serverAddress(), p.serverIcon()) : null;
    }

    private int iconSize(Draw d) {
        return d.lineHeight() + 2;
    }

    /** Space the icon (and divider) take before the text. */
    private int lead(Draw d, Platform p) {
        if (icon(p) == null) {
            return 0;
        }
        return iconSize(d) + (divider.value ? 9 : 4);
    }

    @Override
    public boolean hasContent(Platform p) {
        return p.serverAddress() != null;
    }

    @Override
    public int width(Draw d, Platform p) {
        String t = text(p);
        return d.width(t == null ? "-" : t) + lead(d, p) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return Math.max(d.lineHeight() + 6, icon(p) != null ? iconSize(d) + 4 : 0);
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        String t = text(p);
        if (t == null) {
            return;
        }
        int w = width(d, p), h = height(d, p);
        panel(d, x, y, w, h);
        int tx = x + 4;
        int[] icon = icon(p);
        if (icon != null) {
            int size = iconSize(d);
            int iy = y + (h - size) / 2;
            // drawn from half-size cells, so it stays sharp at every GUI scale
            d.pushScale(tx, iy, size / (float) ServerIcons.CELLS);
            for (int cy = 0; cy < ServerIcons.CELLS; cy++) {
                for (int cx = 0; cx < ServerIcons.CELLS; cx++) {
                    int c = icon[cy * ServerIcons.CELLS + cx];
                    if ((c >>> 24) != 0) {
                        d.rect(cx, cy, 1, 1, c);
                    }
                }
            }
            d.popScale();
            tx += size + 4;
            if (divider.value) {
                d.rect(tx, y + 3, 1, h - 6, 0x60FFFFFF);
                tx += 5;
            }
        }
        d.text(t, tx, y + (h - d.lineHeight()) / 2 + 1, Theme.TEXT, true);
    }
}
