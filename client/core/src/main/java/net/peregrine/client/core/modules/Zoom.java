package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.SliderSetting;

public final class Zoom extends Module {

    private final SliderSetting level = add(new SliderSetting("level", "Zoom level", 2f, 10f, 0.5f, 4f, "%.1fx"));
    private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth zoom", true));
    private double current = 1.0;
    private long last;

    public Zoom() {
        super("zoom", "Zoom", "Hold C to zoom in, like a spyglass (change the key in Controls)",
                Category.UTILITY, false);
    }

    public double divisor(Platform p) {
        double target = enabled() && p.inWorld() && p.zoomKeyDown() ? level.value : 1.0;
        if (!smooth.value) {
            current = target;
            return current;
        }
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        current += (target - current) * Math.min(1.0, dt * 14);  // eases in and out
        if (Math.abs(target - current) < 0.01) {
            current = target;
        }
        return current;
    }
}
