package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;

public final class SpeedHud extends LineHud {

    private double lastX = Double.NaN;
    private double lastZ;
    private double speed;  // blocks per second, smoothed
    private final net.peregrine.client.core.settings.ChoiceSetting unit = add(
            new net.peregrine.client.core.settings.ChoiceSetting("unit", "Unit", 0, "Blocks a second", "km/h"));

    public SpeedHud() {
        super("speed", "Speed", "How fast you're moving, in blocks per second", "Speed", 0f, 0.385f);
    }

    @Override
    public void tick(Platform p) {
        double x = p.x();
        double z = p.z();
        if (!Double.isNaN(lastX)) {
            double now = Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) * 20;
            if (now > 200) {
                now = 0;  // teleported
            }
            speed = speed * 0.7 + now * 0.3;
        }
        lastX = x;
        lastZ = z;
    }

    @Override
    protected void onDisable() {
        lastX = Double.NaN;
    }

    @Override
    String value(Platform p) {
        return unit.index == 1 ? String.format("%.1f km/h", speed * 3.6) : String.format("%.1f b/s", speed);
    }
}
