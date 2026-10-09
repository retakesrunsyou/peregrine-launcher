package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

/** Exact yaw and pitch, for lining up builds and bridges. */
public final class RotationHud extends LineHud {

    public RotationHud() {
        super("rotation", "Rotation", "Your exact yaw and pitch, for lining up builds", "Yaw/Pitch", 0f, 0.92f);
    }

    @Override
    String value(Platform p) {
        float yaw = p.yaw() % 360f;
        if (yaw > 180f) {
            yaw -= 360f;
        } else if (yaw < -180f) {
            yaw += 360f;
        }
        return String.format("%.1f / %.1f", yaw, p.pitch());
    }
}
