package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;

public final class ToggleSprint extends Module {

    public ToggleSprint() {
        super("toggle_sprint", "Toggle sprint", "Always sprint when walking forward", Category.UTILITY, false);
    }

    @Override
    public void tick(Platform p) {
        p.setSprintHeld(true);
    }

    @Override
    protected void onDisable() {
        platform().setSprintHeld(false);
    }
}
