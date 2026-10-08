package net.peregrine.client.core.modules;

import java.util.List;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Platform.EffectInfo;
import net.peregrine.client.core.Theme;

public final class EffectsHud extends HudModule {

    private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    public EffectsHud() {
        super("effects", "Potion effects", "Active effects and how long they last", true, 1f, 0.15f);
    }

    private static String name(EffectInfo e) {
        String level = e.amplifier < ROMAN.length ? ROMAN[e.amplifier] : " " + (e.amplifier + 1);
        return e.name + level;
    }

    private static String time(EffectInfo e) {
        if (e.infinite) {
            return "∞";
        }
        int s = e.durationTicks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    @Override
    public int width(Draw d, Platform p) {
        int w = 60;
        for (EffectInfo e : p.effects()) {
            w = Math.max(w, d.width(name(e) + "  " + time(e)));
        }
        return w + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return Math.max(1, p.effects().size()) * (d.lineHeight() + 2) + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        List<EffectInfo> effects = p.effects();
        if (effects.isEmpty()) {
            return;
        }
        int w = width(d, p);
        panel(d, x, y, w, height(d, p));
        int ry = y + 4;
        for (EffectInfo e : effects) {
            d.text(name(e), x + 4, ry, Theme.TEXT, true);
            String t = time(e);
            boolean ending = !e.infinite && e.durationTicks < 200;
            d.text(t, x + w - 4 - d.width(t), ry, ending ? Theme.BAD : Peregrine.get().accent(), true);
            ry += d.lineHeight() + 2;
        }
    }
}
