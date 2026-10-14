package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** The hotbar selection slides to the new slot instead of jumping. */
public final class SmoothHotbar extends Module {

    private final SliderSetting speed = add(new SliderSetting("speed", "Speed", 5f, 40f, 1f, 18f, "%.0f"));

    public SmoothHotbar() {
        super("smooth_hotbar", "Smooth hotbar", "The hotbar selection slides between slots", Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        Hooks.hotbarSpeed = speed.value;
    }

    @Override
    protected void onEnable() {
        Hooks.hotbarSpeed = speed.value;
    }

    @Override
    protected void onDisable() {
        Hooks.hotbarSpeed = 0f;
    }

    /** Where the selection is drawn this frame, sliding toward target (GUI pixels). */
    private static float shown = Float.NaN;
    private static long last;

    public static int slide(int target) {
        float speed = Hooks.hotbarSpeed;
        long now = System.nanoTime();
        if (speed <= 0f || Float.isNaN(shown) || Math.abs(shown - target) > 400) {
            shown = target;
            last = now;
            return target;
        }
        float dt = Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        shown += (target - shown) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - shown) < 0.5f) {
            shown = target;
        }
        if (Hooks.hotbarHits < 1000) {
            Hooks.hotbarHits++;
        }
        return Math.round(shown);
    }
}
