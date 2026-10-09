package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Freelook: hold Left Alt to swing the camera around you in third person
 * while you keep walking the way you were facing.
 */
public final class Freelook extends Module {

    private final ChoiceSetting mode = add(new ChoiceSetting("mode", "Key", 0, "Hold", "Toggle"));
    private boolean wasDown;
    private boolean toggled;
    private int savedCamera = -1;

    public Freelook() {
        super("freelook", "Freelook", "Hold Left Alt to look around you without turning (change the key in Controls)",
                Category.UTILITY, false);
    }

    @Override
    public String warning() {
        return "Some servers don't allow this";
    }

    @Override
    public void tick(Platform p) {
        boolean down = p.freelookKeyDown();
        boolean want;
        if (mode.index == 1) {
            if (down && !wasDown) {
                toggled = !toggled;
            }
            want = toggled;
        } else {
            want = down;
        }
        wasDown = down;
        if (want && !Hooks.freelook) {
            Hooks.camYaw = p.yaw();
            Hooks.camPitch = p.pitch();
            savedCamera = p.cameraMode();
            p.setCameraMode(1);  // third person, behind
            Hooks.freelook = true;
        } else if (!want && Hooks.freelook) {
            stop(p);
        }
    }

    private void stop(Platform p) {
        Hooks.freelook = false;
        toggled = false;
        if (savedCamera >= 0) {
            p.setCameraMode(savedCamera);
            savedCamera = -1;
        }
    }

    @Override
    protected void onDisable() {
        if (Hooks.freelook) {
            stop(platform());
        }
    }

    @Override
    public void shutdown(Platform p) {
        if (Hooks.freelook) {
            stop(p);
        }
    }
}
