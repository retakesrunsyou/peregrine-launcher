package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Stretched resolution: the game looks like a 4:3 (or 5:4, 16:10...) resolution stretched
 * across your screen, the way many PvP players play: everything is wider, the HUD too.
 */
public final class StretchedView extends Module {

    public static final String[] RESOLUTIONS = {"4:3 (1440 x 1080)", "5:4 (1350 x 1080)", "16:10 (1728 x 1080)",
        "3:2 (1620 x 1080)", "Custom"};
    static final float[] ASPECT = {4f / 3f, 5f / 4f, 16f / 10f, 3f / 2f, 0f};

    private final ChoiceSetting res = add(new ChoiceSetting("res", "Resolution", 0, RESOLUTIONS));
    private final SliderSetting strength = add(new SliderSetting("strength", "Strength", 0f, 100f, 5f, 100f, "%.0f%%"));
    private final SliderSetting amount = add(new SliderSetting("amount", "Custom stretch", 105f, 160f, 1f, 133f, "%.0f%%"));
    private final BoolSetting hud = add(new BoolSetting("hud", "Stretch the HUD too", false));

    public StretchedView() {
        super("stretched", "Stretched resolution", "Play like a stretched 4:3 resolution: everything drawn wider",
                Category.VISUALS, false);
    }

    /** How much wider to draw, for a screen of this shape. */
    static float factor(int index, float custom, float strength, int screenW, int screenH) {
        float full = ASPECT[index] == 0f || screenW <= 0 || screenH <= 0 ? custom / 100f
                : Math.max(1f, (screenW / (float) screenH) / ASPECT[index]);
        return 1f + (full - 1f) * strength / 100f;  // strength: part of the way there
    }

    private void apply(Platform p) {
        Hooks.stretch = enabled() ? factor(res.index, amount.value, strength.value, p.screenWidth(), p.screenHeight()) : 1f;
        Hooks.stretchHud = enabled() && hud.value;
    }

    @Override
    public void tick(Platform p) {
        apply(p);
    }

    @Override
    protected void onDisable() {
        Hooks.stretch = 1f;
        Hooks.stretchHud = false;
    }
}
