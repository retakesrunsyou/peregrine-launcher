package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/** Hide grass, flowers and other plants: a clearer view and a few more FPS. Only for you. */
public final class HidePlants extends Module {

    private final BoolSetting[] kinds = new BoolSetting[Hooks.PLANT_KINDS.length];
    private int applied;

    public HidePlants() {
        super("hide_plants", "Hide plants", "Hide grass, flowers, leaf litter, seagrass or kelp (only on your screen)",
                Category.PERFORMANCE, false);
        boolean[] def = {true, false, true, false, false};
        for (int i = 0; i < kinds.length; i++) {
            kinds[i] = add(new BoolSetting("kind" + i, Hooks.PLANT_KINDS[i], def[i]));
        }
    }

    private void apply(Platform p) {
        int mask = 0;
        if (enabled()) {
            for (int i = 0; i < kinds.length; i++) {
                if (kinds[i].value) {
                    mask |= 1 << i;
                }
            }
        }
        if (mask != applied) {
            applied = mask;
            Hooks.plantMask = mask;
            if (p != null && p.inWorld()) {
                p.reloadChunks();  // chunks are rebuilt with or without them
            }
        }
    }

    @Override
    public void tick(Platform p) {
        apply(p);
    }

    @Override
    protected void onEnable() {
        apply(platform());
    }

    @Override
    protected void onDisable() {
        apply(platform());
    }
}
