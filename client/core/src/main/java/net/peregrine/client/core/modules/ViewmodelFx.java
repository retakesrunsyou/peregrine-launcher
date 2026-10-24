package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.knife.KnifeView;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Viewmodel effects: your hand and what it holds (first person only) see-through like glass, as
 * a hologram, or glowing, and a trail of afterimages when you swing.
 */
public final class ViewmodelFx extends Module {

    public final ChoiceSetting look = add(new ChoiceSetting("look", "Look", 1, KnifeView.FX_NAMES));
    public final SliderSetting opacity = add(new SliderSetting("opacity", "See-through", 10f, 85f, 5f, 55f, "%.0f%%"));
    public final SliderSetting glow = add(new SliderSetting("glow", "Glow", 0f, 100f, 5f, 60f, "%.0f%%"));
    public final SliderSetting color = add(new SliderSetting("color", "Color", 0f, 360f, 5f, 195f, "%.0f")
            .withText(Knives::hueName));
    public final BoolSetting trails = add(new BoolSetting("trails", "Afterimages when you swing", true));
    public final SliderSetting trailLength = add(new SliderSetting("trail_length", "Afterimage length", 50f, 300f, 10f, 140f, "%.0f ms"));
    public final ChoiceSetting swingFor = add(new ChoiceSetting("swing_for", "Swing is for", 1,
            "Everything you hold", "Swords only", "Swords and axes"));

    public ViewmodelFx() {
        super("viewmodel_fx", "Viewmodel effects", "Your hand and held item see-through like glass or a hologram, with a glow and swing afterimages",
                Category.VISUALS, false);
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
        KnifeView v = KnifeView.INSTANCE;
        v.fx = KnifeView.FX_OFF;
        v.trails = false;
    }

    private void apply() {
        KnifeView v = KnifeView.INSTANCE;
        boolean on = enabled();
        v.fx = on ? look.index : KnifeView.FX_OFF;
        v.fxOpacity = 1f - opacity.value / 100f;
        v.fxGlow = glow.value / 100f;
        v.fxColor = Knives.hue(color.value);
        v.trails = on && trails.value;
        v.trailSeconds = trailLength.value / 1000f;
        v.swingFor = swingFor.index;
    }
}
