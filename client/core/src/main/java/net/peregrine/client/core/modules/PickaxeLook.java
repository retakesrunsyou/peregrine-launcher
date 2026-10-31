package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.settings.ChoiceSetting;
import net.peregrine.client.core.skeleton.ToolLooks;

/**
 * Pickaxe look: your pickaxe, in your hand, drawn as a glowing star wand or a peppermint candy
 * axe. Only on your screen; it still mines exactly like the pickaxe it is.
 */
public final class PickaxeLook extends Module {

    public final ChoiceSetting look = add(new ChoiceSetting("look", "Looks like", 0,
            java.util.Arrays.copyOfRange(ToolLooks.NAMES, 1, ToolLooks.NAMES.length)));
    public final BoolSetting glow = add(new BoolSetting("glow", "Glow and sparkles", true));

    private static volatile PickaxeLook instance;

    public PickaxeLook() {
        super("pickaxe_look", "Pickaxe look", "Your pickaxe drawn as a glowing star wand or a peppermint candy axe",
                Category.VISUALS, false);
        instance = this;
    }

    /** ToolLooks.STAR_WAND / PEPPERMINT_AXE, or NORMAL when off. */
    public static int current() {
        PickaxeLook m = instance;
        return m == null || !m.enabled() ? ToolLooks.NORMAL : m.look.index + 1;
    }

    public static boolean glows() {
        PickaxeLook m = instance;
        return m != null && m.glow.value;
    }
}
