package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Performance boost: Peregrine's own speed-ups, each measured with the benchmark. Fewer frames
 * when the game isn't in front of you (cooler, quieter, more for other apps), a cap on
 * particles so explosions and crystal fights don't tank FPS, and chests, signs and other block
 * entities left out past a distance you choose.
 */
public final class Boost extends Module {

    static final String[] BACKGROUND = {"No limit", "15 FPS", "30 FPS", "60 FPS"};
    static final int[] BACKGROUND_FPS = {0, 15, 30, 60};

    static final String[] SCALE = {"100% (sharpest)", "85%", "75%", "66%", "50% (fastest)"};
    static final float[] SCALE_VALUES = {1f, 0.85f, 0.75f, 0.66f, 0.5f};

    public final ChoiceSetting resolution = add(new ChoiceSetting("world_scale", "World resolution (big FPS gain on weaker graphics)", 0, SCALE));
    public final ChoiceSetting background = add(new ChoiceSetting("background", "When the game isn't the window you're using", 2, BACKGROUND));
    public final SliderSetting particles = add(new SliderSetting("particles", "Most particles at once", 500f, 16000f, 500f, 4000f, "%.0f")
            .withText(v -> v >= 16000 ? "No limit" : String.valueOf(Math.round(v))));
    public final SliderSetting blockEntities = add(new SliderSetting("block_entities", "Draw chests, signs and banners up to", 16f, 128f, 8f, 32f, "%.0f blocks")
            .withText(v -> v >= 128 ? "No limit" : Math.round(v) + " blocks"));

    /** Read by the game hooks (0 = off). */
    public static volatile int backgroundFps, maxParticles, blockEntityDistance;
    /** World resolution, 1 = full (read by the game hooks). */
    public static volatile float worldScale = 1f;

    public Boost() {
        super("boost", "Performance boost", "Peregrine's own speed-ups: world resolution, fewer frames in the background, a particle cap, nearer chests and signs",
                Category.PERFORMANCE, true);
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
        backgroundFps = 0;
        maxParticles = 0;
        blockEntityDistance = 0;
        worldScale = 1f;
    }

    private void apply() {
        boolean on = enabled();
        backgroundFps = on ? BACKGROUND_FPS[background.index] : 0;
        maxParticles = on && particles.value < 16000 ? Math.round(particles.value) : 0;
        blockEntityDistance = on && blockEntities.value < 128 ? Math.round(blockEntities.value) : 0;
        worldScale = on ? SCALE_VALUES[resolution.index] : 1f;
    }
}
