package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Flight boost: fly faster in creative and spectator. Only ever while you're allowed to fly
 * anyway (creative or spectator), never in survival. Spectator's own scroll-wheel speed still
 * works: the boost multiplies whatever speed Minecraft has set.
 */
public final class FlightBoost extends Module {

    static final float NORMAL = 0.05f;

    private final SliderSetting creative = add(new SliderSetting("creative", "Creative speed", 1f, 10f, 0.25f, 2.5f, "%.2fx"));
    private final SliderSetting spectator = add(new SliderSetting("spectator", "Spectator speed", 1f, 10f, 0.25f, 2f, "%.2fx"));

    private float base = NORMAL;   // Minecraft's own speed, before the boost
    private float applied = -1;    // what we set last (anything else means Minecraft changed it)

    public FlightBoost() {
        super("flight_boost", "Flight boost", "Fly faster in creative and spectator", Category.UTILITY, false);
    }

    @Override
    public void tick(Platform p) {
        int mode = p.gameMode();
        boolean allowed = mode == 1 || mode == 3;
        float now = p.flySpeed();
        if (applied < 0 || Math.abs(now - applied) > 1e-5f) {
            base = now;  // Minecraft (or the spectator scroll wheel) set a new speed
        }
        float mult = !allowed ? 1f : (mode == 3 ? spectator.value : creative.value);
        float want = base * mult;
        if (Math.abs(now - want) > 1e-5f) {
            p.setFlySpeed(want);
        }
        applied = want;
    }

    @Override
    protected void onDisable() {
        Platform p = platform();
        if (p != null && applied >= 0 && Math.abs(p.flySpeed() - applied) < 1e-5f) {
            p.setFlySpeed(base);
        }
        applied = -1;
    }

    /** For tests: what the speed should be. */
    public float boosted(int mode, float minecraftSpeed) {
        return mode == 1 ? minecraftSpeed * creative.value : mode == 3 ? minecraftSpeed * spectator.value : minecraftSpeed;
    }
}
