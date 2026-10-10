package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Block outline: the color and thickness of the box around the block you're
 * looking at, and colored outlines on ores you can already see (never through walls).
 */
public final class BlockOutline extends Module {

    static final String[] COLORS = {"Accent", "Minecraft's", "White", "Black", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink"};
    static final int[] RGB = {-1, 0, 0xFFFFFFFF, 0xFF000000, 0xFFE04848, 0xFFF0903C, 0xFFF2D24A, 0xFF5ED17A,
        0xFF4FD8E0, 0xFF4C7CF0, 0xFF9C62F0, 0xFFF07AC8};

    private final ChoiceSetting color = add(new ChoiceSetting("color", "Outline color", 0, COLORS));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 20f, 100f, 5f, 85f, "%.0f%%"));
    private final SliderSetting width = add(new SliderSetting("width", "Thickness (1.21.11 and newer)", 1f, 6f, 0.5f, 2f, "%.1fx"));
    private final BoolSetting ores = add(new BoolSetting("ores", "Outline ores you can see", true));
    private final SliderSetting range = add(new SliderSetting("range", "Ore outline range", 4f, 24f, 1f, 12f, "%.0f blocks"));

    public BlockOutline() {
        super("block_outline", "Block outline", "Color the block outline and outline ores you can see",
                Category.VISUALS, false);
    }

    private void apply() {
        boolean on = enabled();
        int c = RGB[color.index];
        if (c == -1) {
            c = Peregrine.get().accent();
        }
        if (c != 0) {
            c = (Math.round(opacity.value / 100f * 255) << 24) | (c & 0xFFFFFF);
        }
        Hooks.outlineColor = on ? c : 0;
        Hooks.outlineWidth = on ? width.value : 1f;
        Hooks.oreOutlines = on && ores.value;
        Hooks.oreRange = Math.round(range.value);
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
