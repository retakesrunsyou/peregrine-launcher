package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Recolors the enchantment shine on items and armor. The new texture is made once
 * when the color changes (a quick reload when you close the menu), so it costs
 * nothing while you play.
 */
public final class GlintColor extends Module {

    static final String[] NAMES = {"Red", "Orange", "Gold", "Lime", "Green", "Aqua", "Blue", "Purple", "Pink", "White"};
    static final int[] COLORS = {0xFF3B3B, 0xFF8A2E, 0xFFD23C, 0xA6FF3B, 0x3BE070, 0x3BE8E8, 0x3B7BFF, 0xA25BFF, 0xFF4FB4, 0xFFFFFF};

    private final ChoiceSetting color = add(new ChoiceSetting("color", "Color", 8, NAMES));
    private final SliderSetting strength = add(new SliderSetting("strength", "Brightness", 50f, 200f, 10f, 120f, "%.0f%%"));

    private int applied;
    private float appliedStrength = -1f;

    public GlintColor() {
        super("glint_color", "Glint color", "Pick the color of the enchantment shine on items and armor",
                Category.VISUALS, false);
    }

    private int wanted() {
        return enabled() ? COLORS[color.index] : 0;
    }

    @Override
    public void tick(Platform p) {
        sync(p);
    }

    @Override
    protected void onDisable() {
        sync(platform());
    }

    /** Applies a changed color once you're back in the game (a reload, so not mid-menu). */
    private void sync(Platform p) {
        int w = wanted();
        float s = strength.value / 100f;
        if ((w == applied && (w == 0 || s == appliedStrength)) || p == null || !p.showing(Platform.Screen.NONE)) {
            return;
        }
        applied = w;
        appliedStrength = s;
        Hooks.glintColor = w;
        Hooks.glintStrength = s;
        // Just the shine's textures are read again (not a full resource reload).
        p.refreshTextures("textures/misc/enchanted_glint_item.png", "textures/misc/enchanted_glint_armor.png",
                "textures/misc/enchanted_glint_entity.png");
    }
}
