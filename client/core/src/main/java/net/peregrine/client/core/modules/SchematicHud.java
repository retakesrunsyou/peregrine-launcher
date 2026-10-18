package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Schematics;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.settings.BoolSetting;

/** While a schematic is placed: how much is built, the layer, and which block goes where you look. */
public final class SchematicHud extends HudModule {

    static final int W = 150;
    private final BoolSetting hint = add(new BoolSetting("hint", "Show the block to place where you look", true));
    private String[] looking;
    private long lookedAt;

    public SchematicHud() {
        super("schematic", "Schematic", "How much of your schematic is built, and what goes where you look", true, 0.5f, 0.86f);
    }

    @Override
    public boolean hasContent(Platform p) {
        Schematics.Placement pl = Schematics.current();
        return pl != null && pl.visible;
    }

    private String[] look(Platform p) {
        long now = System.currentTimeMillis();
        if (now - lookedAt > 150) {
            lookedAt = now;
            looking = hint.value ? Schematics.lookingAt(p) : null;
        }
        return looking;
    }

    @Override
    public int width(Draw d, Platform p) {
        return W;
    }

    @Override
    public int height(Draw d, Platform p) {
        return look(p) != null ? 34 : 22;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        Schematics.Placement pl = Schematics.current();
        if (pl == null) {
            return;
        }
        int a = Peregrine.get().accent();
        panel(d, x, y, W, height(d, p));
        String name = d.trim(pl.s.name, W - 50);
        d.text(name, x + 5, y + 4, Theme.TEXT, true);
        int need = Schematics.needed(), done = Schematics.done();
        String pct = done < 0 || need == 0 ? "" : Math.round(done * 100f / need) + "%";
        d.text(pct, x + W - 5 - d.width(pct), y + 4, a, true);
        d.roundRect(x + 5, y + 15, W - 10, 3, 1, 0x40FFFFFF);
        if (need > 0 && done > 0) {
            d.roundRect(x + 5, y + 15, Math.max(3, Math.round((W - 10) * done / (float) need)), 3, 1, a);
        }
        String[] l = look(p);
        if (l != null) {
            String what = l[1].equals("place") ? "Place " : l[1].equals("wrong") ? "Should be " : "Turn: ";
            int c = l[1].equals("place") ? Schematics.MISSING : l[1].equals("wrong") ? Schematics.WRONG : Schematics.WRONG_STATE;
            d.roundRect(x + 5, y + 23, 6, 6, 1, c);
            d.text(d.trim(what + l[0], W - 20), x + 14, y + 22, Theme.TEXT, true);
        }
    }
}
