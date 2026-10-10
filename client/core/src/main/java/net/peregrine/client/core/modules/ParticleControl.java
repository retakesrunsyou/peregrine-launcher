package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;

/**
 * Particles: how strongly to thin out each kind of particle. "Semi" still shows
 * some of them (you can tell it's there), "Strong" leaves only a few, "Hide" none.
 */
public final class ParticleControl extends Module {

    static final String[] LEVELS = {"Normal", "Semi", "Strong", "Hide"};
    static final float[] KEEP = {1f, 0.35f, 0.1f, 0f};
    // Starting points: the busy, distracting kinds are thinned; hits and blocks stay.
    static final int[] DEFAULTS = {1, 1, 1, 1, 0, 0, 1, 0};

    private final ChoiceSetting[] kinds = new ChoiceSetting[Hooks.PARTICLE_KINDS.length];

    public ParticleControl() {
        super("fewer_particles", "Particles",
                "Thin out potion swirls, splash potions, XP bottles, lava and more: Normal, Semi or Strong",
                Category.PERFORMANCE, false);
        for (int i = 0; i < kinds.length; i++) {
            kinds[i] = add(new ChoiceSetting("kind" + i, Hooks.PARTICLE_KINDS[i], DEFAULTS[i], LEVELS));
        }
    }

    private void apply() {
        float[] keep = new float[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            keep[i] = enabled() ? KEEP[kinds[i].index] : 1f;
        }
        boolean any = false;
        for (float k : keep) {
            any |= k < 1f;
        }
        Hooks.particleKeep = keep;
        Hooks.particlesFiltered = any;
    }

    @Override
    public void tick(Platform p) {
        apply();
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        apply();
    }
}
