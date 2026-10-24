package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.knife.KnifeModels;
import net.peregrine.client.core.knife.KnifeSkins;
import net.peregrine.client.core.knife.KnifeView;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.settings.KeySetting;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * Knives: your sword (first person) becomes a knife held in your own arm, shooter style, with
 * its own moves: a flourish when you pull it out, slashes that switch sides, a stab on crits, an
 * inspect key and little fidgets when you stand still. Only on your screen.
 */
public final class Knives extends Module {

    public final ChoiceSetting knife = add(new ChoiceSetting("knife", "Knife", 2, KnifeModels.NAMES));
    public final ChoiceSetting skin = add(new ChoiceSetting("skin", "Finish", 1, KnifeSkins.NAMES));
    public final SliderSetting color1 = add(new SliderSetting("color1", "Your colors: blade base", 0f, 360f, 5f, 350f, "%.0f")
            .withText(Knives::hueName));
    public final SliderSetting color2 = add(new SliderSetting("color2", "Your colors: blade tip", 0f, 360f, 5f, 220f, "%.0f")
            .withText(Knives::hueName));
    public final ChoiceSetting arm = add(new ChoiceSetting("arm", "Your arm", 0, KnifeView.ARM_NAMES));
    public final SliderSetting armSize = add(new SliderSetting("arm_size", "Arm size", 40f, 140f, 5f, 70f, "%.0f%%"));
    public final SliderSetting size = add(new SliderSetting("size", "Knife size", 0.5f, 2.5f, 0.05f, 1f, "%.2fx"));
    public final SliderSetting moveX = add(new SliderSetting("move_x", "Knife left / right", -8f, 8f, 0.5f, 0f, "%.1f"));
    public final SliderSetting moveY = add(new SliderSetting("move_y", "Knife up / down", -8f, 8f, 0.5f, 0f, "%.1f"));
    public final SliderSetting moveZ = add(new SliderSetting("move_z", "Knife toward / away", -8f, 8f, 0.5f, 0f, "%.1f"));
    public final SliderSetting tilt = add(new SliderSetting("tilt", "Knife tilt", -180f, 180f, 5f, 0f, "%.0f°"));
    public final SliderSetting turn = add(new SliderSetting("turn", "Knife turn", -180f, 180f, 5f, 0f, "%.0f°"));
    public final SliderSetting roll = add(new SliderSetting("roll", "Knife roll", -180f, 180f, 5f, 0f, "%.0f°"));
    public final BoolSetting fidgets = add(new BoolSetting("fidgets", "Fidget when you stand still", true));
    public final KeySetting inspect = add(new KeySetting("inspect", "Inspect key"));

    public Knives() {
        super("knives", "Knives", "Your sword becomes a knife in your hand, with skins, slashes, stabs and an inspect key (F)",
                Category.VISUALS, false);
        inspect.key = 70;   // F (Minecraft's swap-hands key: while you hold a knife it inspects instead)
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
        KnifeView.INSTANCE.enabled = false;
    }

    @Override
    public void keyPressed(int key, Platform p) {
        // handled by the game hook (so the key can be kept from swapping your hands); this
        // catches the case where the hook isn't there
    }

    private void apply() {
        KnifeView v = KnifeView.INSTANCE;
        v.knife = knife.index;
        v.skin = skin.index;
        v.armMode = arm.index;
        v.size = size.value;
        v.armSize = armSize.value / 100f;
        v.moveX = moveX.value;
        v.moveY = moveY.value;
        v.moveZ = moveZ.value;
        v.tilt = tilt.value;
        v.turn = turn.value;
        v.roll = roll.value;
        v.fidgets = fidgets.value;
        v.inspectKey = inspect.key;
        v.custom1 = hue(color1.value);
        v.custom2 = hue(color2.value);
        v.enabled = enabled();
    }

    /** A bright colour for a hue in degrees. */
    public static int hue(float deg) {
        float h = ((deg % 360) + 360) % 360 / 60f;
        float x = 1 - Math.abs(h % 2 - 1);
        float r, g, b;
        if (h < 1) { r = 1; g = x; b = 0; } else if (h < 2) { r = x; g = 1; b = 0; } else if (h < 3) { r = 0; g = 1; b = x; }
        else if (h < 4) { r = 0; g = x; b = 1; } else if (h < 5) { r = x; g = 0; b = 1; } else { r = 1; g = 0; b = x; }
        float s = 0.85f;
        r = 1 - s + s * r;
        g = 1 - s + s * g;
        b = 1 - s + s * b;
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    static String hueName(float deg) {
        String[] names = {"Red", "Orange", "Yellow", "Lime", "Green", "Teal", "Cyan", "Sky", "Blue", "Violet", "Purple", "Pink", "Red"};
        return names[Math.round(((deg % 360) + 360) % 360 / 30f)];
    }
}
