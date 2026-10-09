package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class XpHud extends LineHud {

    public XpHud() {
        super("xp", "Experience", "Your level and how far to the next one", "Level", 1f, 0.80f);
    }

    @Override
    String value(Platform p) {
        return p.xpLevel() + "  (" + Math.round(p.xpProgress() * 100) + "%)";
    }
}
