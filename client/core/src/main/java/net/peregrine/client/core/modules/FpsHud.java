package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class FpsHud extends LineHud {

    public FpsHud() {
        super("fps", "FPS", "Frames per second, so you can see what your settings do", "FPS", true, 0f, 0f);
    }

    @Override
    String value(Platform p) {
        return String.valueOf(p.fps());
    }
}
