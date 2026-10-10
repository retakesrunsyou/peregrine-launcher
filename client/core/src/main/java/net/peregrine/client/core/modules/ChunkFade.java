package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** New chunks fade in smoothly instead of popping in (Minecraft 1.21.11 and newer). */
public final class ChunkFade extends Module {

    private final SliderSetting time = add(new SliderSetting("time", "Fade-in time", 0.1f, 2f, 0.05f, 0.6f, "%.2f s"));
    private float applied = -1f;

    public ChunkFade() {
        super("chunk_fade", "Chunk animation", "New chunks fade in smoothly instead of popping in",
                Category.VISUALS, false);
    }

    @Override
    public void tick(Platform p) {
        if (applied != time.value && p.setChunkFade(time.value)) {
            applied = time.value;
        }
    }

    @Override
    protected void onEnable() {
        applied = -1f;
        tick(platform());
    }

    @Override
    protected void onDisable() {
        platform().setChunkFade(0);  // Peregrine's fast settings keep it off
        applied = -1f;
    }
}
