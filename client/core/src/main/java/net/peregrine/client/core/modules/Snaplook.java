package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.KeySetting;

/** Snaplook: hold a key to look behind you (the camera swings round to face you), let go to look ahead. */
public final class Snaplook extends Module {

    private final KeySetting key = add(new KeySetting("key", "Hold to look behind", 86));   // V
    private boolean holding;
    private int saved;

    public Snaplook() {
        super("snaplook", "Snaplook", "Hold a key to look behind you, let go to look ahead again", Category.UTILITY, false);
    }

    @Override
    public void tick(Platform p) {
        boolean down = key.key >= 0 && p.inWorld() && p.keyDown(key.key);
        if (down && !holding) {
            holding = true;
            saved = p.cameraMode();
            p.setCameraMode(2);   // third person, in front: looking back at you
        } else if (!down && holding) {
            holding = false;
            p.setCameraMode(saved);
        }
    }

    @Override
    protected void onDisable() {
        if (holding) {
            holding = false;
            platform().setCameraMode(saved);
        }
    }
}
