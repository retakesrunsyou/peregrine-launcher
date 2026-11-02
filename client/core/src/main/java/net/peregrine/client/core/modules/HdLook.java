package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.settings.SliderSetting;

/**
 * HD look: a crisper picture. Sharpening that adapts to each spot (strong on soft detail, gentle
 * on edges that are already sharp, so no halos), local clarity (a little more contrast in the
 * midtones of textures) and vibrance (dull colors get richer, already-bright ones are left
 * alone). Applied to the world before your hand and HUD, so text stays clean.
 */
public final class HdLook extends Module {

    public final SliderSetting sharpness = add(new SliderSetting("sharpness", "Sharpness", 0f, 100f, 5f, 55f, "%.0f%%"));
    public final SliderSetting clarity = add(new SliderSetting("clarity", "Clarity", 0f, 100f, 5f, 30f, "%.0f%%"));
    public final SliderSetting vibrance = add(new SliderSetting("vibrance", "Vibrance", 0f, 100f, 5f, 25f, "%.0f%%"));

    private static volatile HdLook instance;

    public HdLook() {
        super("hd_look", "HD look", "A crisper, sharper picture like a 4K screen: smart sharpening that leaves edges clean, "
                + "local clarity and vibrance", Category.VISUALS, false);
        instance = this;
    }

    /** {sharpness, clarity, vibrance} from 0 to 1, or null when off. */
    public static float[] amounts() {
        HdLook m = instance;
        if (m == null || !m.enabled()) {
            return null;
        }
        return new float[] {m.sharpness.value / 100f, m.clarity.value / 100f, m.vibrance.value / 100f};
    }
}
