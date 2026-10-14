package net.peregrine.client.core.modules;

import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.SliderSetting;

/** Move, turn and resize what each hand holds (first person only, only for you). */
public final class HandPosition extends Module {

    private final SliderSetting[] main = new SliderSetting[5];
    private final SliderSetting[] off = new SliderSetting[5];

    public HandPosition() {
        super("hand_position", "Hand position", "Move, turn and resize the items in your hands",
                Category.VISUALS, false);
        String[] what = {"left / right", "down / up", "back / forward", "size", "turn"};
        for (int h = 0; h < 2; h++) {
            String hand = h == 0 ? "Main hand " : "Off hand ";
            SliderSetting[] a = h == 0 ? main : off;
            a[0] = add(new SliderSetting((h == 0 ? "m" : "o") + "x", hand + what[0], -0.6f, 0.6f, 0.02f, 0f, "%+.2f"));
            a[1] = add(new SliderSetting((h == 0 ? "m" : "o") + "y", hand + what[1], -0.6f, 0.6f, 0.02f, 0f, "%+.2f"));
            a[2] = add(new SliderSetting((h == 0 ? "m" : "o") + "z", hand + what[2], -0.6f, 0.6f, 0.02f, 0f, "%+.2f"));
            a[3] = add(new SliderSetting((h == 0 ? "m" : "o") + "s", hand + what[3], 30f, 150f, 5f, 100f, "%.0f%%"));
            a[4] = add(new SliderSetting((h == 0 ? "m" : "o") + "r", hand + what[4], -45f, 45f, 1f, 0f, "%+.0f\u00b0"));
        }
    }

    private static float[] values(SliderSetting[] a) {
        return new float[] {a[0].value, a[1].value, -a[2].value, a[3].value / 100f, a[4].value};
    }

    private static boolean neutral(float[] v) {
        return v[0] == 0 && v[1] == 0 && v[2] == 0 && v[3] == 1f && v[4] == 0;
    }

    private void apply() {
        float[] m = values(main), o = values(off);
        Hooks.handMain = enabled() && !neutral(m) ? m : null;
        Hooks.handOff = enabled() && !neutral(o) ? o : null;
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
