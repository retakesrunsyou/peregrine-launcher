package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.SkinFaces;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;

/** Your head and name, with your ping on a server. */
public final class ProfileHud extends HudModule {

    private final BoolSetting ping = add(new BoolSetting("ping", "Show ping", true));

    public ProfileHud() {
        super("profile", "Profile", "Your head and name, with your ping", false, 0f, 0.09f);
    }

    private String name(Platform p) {
        String n = net.peregrine.client.core.Hooks.nick;
        return n != null ? n : p.playerName();
    }

    private String sub(Platform p) {
        int ms = p.ping();
        return ping.value && ms >= 0 ? ms + " ms" : p.serverAddress() == null ? "Singleplayer" : "Online";
    }

    @Override
    public int width(Draw d, Platform p) {
        return 26 + Math.max(d.width(name(p)), d.width(sub(p))) + 6;
    }

    @Override
    public int height(Draw d, Platform p) {
        return 24;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), 24);
        int[] face = net.peregrine.client.core.Hooks.nick != null ? null : SkinFaces.get(p.playerUuid());
        if (face != null) {
            SkinFaces.draw(d, face, x + 4, y + 4, 16);
        } else {
            d.roundRect(x + 4, y + 4, 16, 16, Theme.withAlpha(Peregrine.get().accent(), 0x60));
            String l = name(p).isEmpty() ? "?" : name(p).substring(0, 1).toUpperCase();
            d.text(l, x + 12 - d.width(l) / 2, y + 8, Theme.TEXT, false);
        }
        d.text(name(p), x + 26, y + 3, Theme.TEXT, true);
        d.text(sub(p), x + 26, y + 13, Theme.MUTED, true);
    }
}
