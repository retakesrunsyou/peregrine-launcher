package net.peregrine.client.core.modules;

import net.peregrine.client.core.Platform;
import net.peregrine.client.core.Theme;

/** Name, health and distance of whatever is under your crosshair. */
public final class TargetHud extends LineHud {

    public TargetHud() {
        super("target", "Target info", "Name, health and distance of the mob or player you're aiming at",
                "Target", 0.5f, 0.62f);
    }

    @Override
    String value(Platform p) {
        String name = p.targetName();
        if (name.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(name);
        if (p.targetHealth() >= 0) {
            sb.append("  ").append(String.format("%.1f", p.targetHealth() / 2f)).append(" hp");
        }
        if (p.targetDistance() >= 0) {
            sb.append("  ").append(String.format("%.1fm", p.targetDistance()));
        }
        return sb.toString();
    }

    @Override
    int labelColor(Platform p) {
        if (p.targetHealth() < 0) {
            return 0;
        }
        float f = p.targetHealth() / Math.max(1f, p.targetMaxHealth());
        return f > 0.6f ? Theme.GOOD : f > 0.3f ? Theme.OK : Theme.BAD;
    }
}
