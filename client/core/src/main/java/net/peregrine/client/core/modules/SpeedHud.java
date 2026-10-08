package net.peregrine.client.core.modules;

import net.peregrine.client.core.Draw;
import net.peregrine.client.core.HudModule;
import net.peregrine.client.core.Platform;

public final class SpeedHud extends HudModule {

    private double lastX = Double.NaN;
    private double lastZ;
    private double speed;  // blocks per second, smoothed

    public SpeedHud() {
        super("speed", "Speed", "How fast you're moving, in blocks per second", false, 0f, 0.385f);
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

    private String value() {
        return String.format("%.1f b/s", speed);
    }

    @Override
    public int width(Draw d, Platform p) {
        return d.width("Speed " + value()) + 8;
    }

    @Override
    public int height(Draw d, Platform p) {
        return d.lineHeight() + 6;
    }

    @Override
    public void render(Draw d, Platform p, int x, int y) {
        panel(d, x, y, width(d, p), height(d, p));
        labelled(d, x + 4, y + 4, "Speed", value());
    }
}
