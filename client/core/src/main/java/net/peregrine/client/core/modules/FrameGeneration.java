package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Frame generation (beta): between the frames Minecraft draws, Peregrine shows the last frame
 * again, turned to where your view is heading, so turning looks smoother when your FPS is below
 * your screen's refresh rate. Your HUD and hand stay put. Turning it on or off takes effect the
 * next time the game starts (it changes how frames reach the screen).
 */
public final class FrameGeneration extends Module {

    public static final String[] TIMES = {"2x", "3x", "4x"};

    public final ChoiceSetting times = add(new ChoiceSetting("times", "Frames shown per frame drawn", 0, TIMES));

    private static volatile FrameGeneration instance;

    public FrameGeneration() {
        super("frame_gen", "Frame generation", "Shows 2x to 4x your frames: the frames in between are your last one turned to where "
                + "you're looking. Smoother turning; fast flicks can blur. Beta, restart the game after switching it",
                Category.PERFORMANCE, false);
        instance = this;
    }

    /** Frames shown for each frame drawn (1 when off). */
    public static int multiplier() {
        FrameGeneration m = instance;
        return m == null || !m.enabled() ? 1 : m.times.index + 2;
    }
}
