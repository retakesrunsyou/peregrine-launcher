package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Peregrine;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/** Block outline: the color and thickness of the box around the block you're looking at. */
public final class BlockOutline extends Module {

    // (new choices go at the end, so saved choices keep their meaning)
    static final String[] COLORS = {"Accent", "Minecraft's", "White", "Black", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue",
        "Purple", "Pink", "Rainbow"};
    static final int[] RGB = {-1, 0, 0xFFFFFFFF, 0xFF000000, 0xFFE04848, 0xFFF0903C, 0xFFF2D24A, 0xFF5ED17A,
        0xFF4FD8E0, 0xFF4C7CF0, 0xFF9C62F0, 0xFFF07AC8, -2};
    static final int RAINBOW = 12;

    private final ChoiceSetting color = add(new ChoiceSetting("color", "Outline color", RAINBOW, COLORS));
    private final SliderSetting speed = add(new SliderSetting("speed", "Rainbow speed", 1f, 10f, 1f, 4f, "%.0f"));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 20f, 100f, 5f, 85f, "%.0f%%"));
    private final SliderSetting width = add(new SliderSetting("width", "Thickness", 1f, 6f, 0.5f, 1.5f, "%.1fx"));

    public BlockOutline() {
        super("block_outline", "Block outline", "A rainbow (or any color), slightly thicker box around the block you're looking at",
                Category.VISUALS, true);
    }

    private void apply() {
        boolean on = enabled();
        int c = RGB[color.index];
        if (c == -1) {
            c = Peregrine.get().accent();
        } else if (c == -2) {  // the colors flow round, like RGB text
            float hue = (System.currentTimeMillis() % 100000L) / 1000f * speed.value * 0.05f;
            c = java.awt.Color.HSBtoRGB(hue - (float) Math.floor(hue), 0.75f, 1f);
        }
        if (c != 0) {
            c = (Math.round(opacity.value / 100f * 255) << 24) | (c & 0xFFFFFF);
        }
        Hooks.outlineColor = on ? c : 0;
        Hooks.outlineWidth = on ? width.value : 1f;
    }

    @Override
    public void tick(Platform p) {
        apply();
    }

    @Override
    public void renderWorld(net.peregrine.client.core.Draw d, Platform p) {
        if (color.index == RAINBOW) {
            apply();  // every frame, so the colors flow smoothly
        }
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
