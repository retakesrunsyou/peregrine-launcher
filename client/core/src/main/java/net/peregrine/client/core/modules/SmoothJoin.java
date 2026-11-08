package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;

/**
 * Smooth join: a short loading screen after joining a world, held until the chunks around you
 * have been built, so the first seconds of building happen behind it rather than while you play.
 * (Drawn by Peregrine itself; this module is the switch.)
 */
public final class SmoothJoin extends Module {

    public SmoothJoin() {
        super("smooth_join", "Smooth join",
                "After you join, a loading screen stays up until the world around you is built (a few seconds at most), "
                        + "so the first rush of chunks doesn't stutter while you play",
                Category.PERFORMANCE, true);
    }
}
