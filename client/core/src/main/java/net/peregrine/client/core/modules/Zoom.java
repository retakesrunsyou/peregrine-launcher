package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Hold the zoom key to look closer; scroll while holding it to zoom further in or out. */
public final class Zoom extends Module {

    static final double MAX = 50.0, MIN = 1.1;

    private final SliderSetting level = add(new SliderSetting("level", "Zoom level", 2f, 50f, 0.5f, 4f, "%.1fx"));
    private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth zoom", true));
    private final BoolSetting scrollZoom = add(new BoolSetting("scroll", "Scroll to zoom in and out", true));
    private double current = 1.0;
    /** Scrolling while zoomed multiplies the level by this; back to 1 when you let go. */
    private double scrolled = 1.0;
    private boolean held;
    private long last;

    public Zoom() {
        super("zoom", "Zoom", "Hold C to zoom in (scroll to zoom further), like a spyglass",
                Category.UTILITY, false);
    }

    double target(Platform p) {
        held = enabled() && p.inWorld() && p.zoomKeyDown();
        if (!held) {
            scrolled = 1.0;
            return 1.0;
        }
        return Math.max(MIN, Math.min(MAX, level.value * scrolled));
    }

    /** A scroll while holding the zoom key: zoom in (up) or out (down). True if it was used. */
    public boolean scroll(double amount) {
        if (!held || !scrollZoom.value || amount == 0) {
            return false;
        }
        double next = scrolled * Math.pow(1.18, amount);
        double lvl = level.value * next;
        if (lvl > MAX) {
            next = MAX / level.value;
        } else if (lvl < MIN) {
            next = MIN / level.value;
        }
        scrolled = next;
        return true;
    }

    public double divisor(Platform p) {
        double target = target(p);
        if (!smooth.value) {
            current = target;
            return current;
        }
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        // eases in and out, in steps that feel the same at 2x and at 40x
        double lc = Math.log(current), lt = Math.log(target);
        lc += (lt - lc) * Math.min(1.0, dt * 14);
        current = Math.exp(lc);
        if (Math.abs(target - current) < 0.005 * target) {
            current = target;
        }
        return current;
    }
}
