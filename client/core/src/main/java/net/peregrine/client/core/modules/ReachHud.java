package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/** How far away you were when you last hit something. */
public final class ReachHud extends LineHud {

    private final ChoiceSetting decimals = add(new ChoiceSetting("decimals", "Decimals", 0, "One", "Two"));
    private double last = -1;
    private long lastAt;

    public ReachHud() {
        super("reach", "Reach display", "How far away you were when you last hit something", "Reach", 0f, 0.80f);
    }

    /** Called by the core on each attack. */
    public void hit(double distance) {
        last = distance;
        lastAt = System.currentTimeMillis();
    }

    @Override
    String value(Platform p) {
        if (last < 0 || System.currentTimeMillis() - lastAt > 3000) {
            return "-";
        }
        return String.format(decimals.index == 0 ? "%.1f blocks" : "%.2f blocks", last);
    }
}
