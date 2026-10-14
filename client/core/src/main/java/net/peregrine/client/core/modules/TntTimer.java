package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;
import net.peregrine.client.core.WorldLabels;
import net.peregrine.client.core.settings.SliderSetting;

/** Lit TNT shows how long until it goes off. */
public final class TntTimer extends Module {

    private final SliderSetting range = add(new SliderSetting("range", "Show within", 8f, 64f, 4f, 32f, "%.0f blocks"));

    public TntTimer() {
        super("tnt_timer", "TNT countdown", "A timer over lit TNT counting down to the blast", Category.VISUALS, false);
    }

    @Override
    public void renderWorld(Draw d, Platform p) {
        double r2 = range.value * range.value;
        for (double[] t : p.primedTnt()) {
            double dx = t[0] - p.x(), dy = t[1] - p.y(), dz = t[2] - p.z();
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            double sec = Math.max(0, t[3]) / 20.0;
            int c = sec > 2 ? Theme.GOOD : sec > 1 ? Theme.OK : Theme.BAD;
            WorldLabels.tag(d, p, t[0], t[1] + 1.4, t[2], String.format("%.1fs", sec), c, 0, 0.75f, false);
        }
    }
}
