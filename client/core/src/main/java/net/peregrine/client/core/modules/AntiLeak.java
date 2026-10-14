package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;

/**
 * Anti base leak. Minecraft picks each block's texture variant and rotation
 * (stone, deepslate, tuff, bedrock, grass, sand...) and each plant's position
 * from the block's coordinates, so a screenshot or stream of a few blocks can
 * be matched back to where they are. This draws every block the same way.
 * Only your own screen changes; the world and the server don't.
 */
public final class AntiLeak extends Module {

    private final BoolSetting rotation = add(new BoolSetting("rotation", "Same rotation for every block", true));
    private final BoolSetting plants = add(new BoolSetting("plants", "Plants and grass centred in their block", true));
    private final BoolSetting bedrock = add(new BoolSetting("bedrock", "Bedrock shows as diamond blocks", true));
    private String applied = "";

    public AntiLeak() {
        super("anti_leak", "Anti base leak",
                "Hides block rotations (bedrock, deepslate, tuff, stone...) that can give away your coordinates",
                Category.VISUALS, false);
    }

    private String wanted() {
        boolean on = enabled();
        return (on && rotation.value) + "/" + (on && plants.value) + "/" + (on && bedrock.value);
    }

    private void apply(Platform p) {
        String w = wanted();
        if (w.equals(applied)) {
            return;
        }
        boolean on = enabled();
        Hooks.fixedRotation = on && rotation.value;
        Hooks.fixedOffset = on && plants.value;
        Hooks.diamondBedrock = on && bedrock.value;
        boolean first = applied.isEmpty();
        applied = w;
        if (!first || on) {
            p.reloadChunks();  // chunks are drawn once and kept, so redraw them with the new look
        }
    }

    @Override
    protected void onEnable() {
        apply(platform());
    }

    @Override
    protected void onDisable() {
        apply(platform());
    }

    @Override
    public void tick(Platform p) {
        apply(p);  // picks up changes made on the settings page
    }
}
