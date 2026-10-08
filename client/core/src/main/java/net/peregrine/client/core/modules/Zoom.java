package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;

public final class Zoom extends Module {

    public Zoom() {
        super("zoom", "Zoom", "Hold C to zoom in, like a spyglass (change the key in Controls)",
                Category.UTILITY, true);
    }

    public double divisor(Platform p) {
        return enabled() && p.inWorld() && p.zoomKeyDown() ? 4.0 : 1.0;
    }
}
