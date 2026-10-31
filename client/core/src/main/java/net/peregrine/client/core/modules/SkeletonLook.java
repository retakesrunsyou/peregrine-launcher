package net.peregrine.client.core.modules;

import net.peregrine.client.core.Module;
import net.peregrine.client.core.Platform;
import net.peregrine.client.core.settings.BoolSetting;
import net.peregrine.client.core.skeleton.Skeleton;

/**
 * Skeleton look: every player (you too, in third person) drawn as a realistic human skeleton
 * that moves exactly like the player does, and your own first-person arm as bone. Only on your
 * screen; nobody else sees a change.
 */
public final class SkeletonLook extends Module {

    public final BoolSetting yourArm = add(new BoolSetting("your_arm", "Your first-person arm as bone", true));
    public final BoolSetting armor = add(new BoolSetting("armor", "Show armor on skeletons", false));

    private static volatile SkeletonLook instance;

    public SkeletonLook() {
        super("skeletons", "Skeleton look", "Every player drawn as a realistic 3D human skeleton (only on your screen)",
                Category.VISUALS, false);
        instance = this;
    }

    @Override
    protected void onEnable() {
        Skeleton.prepare();
    }

    @Override
    public void tick(Platform p) {
        if (enabled()) {
            Skeleton.prepare();
        }
    }

    /** Players are skeletons right now (and the bones are built). */
    public static boolean on() {
        SkeletonLook m = instance;
        return m != null && m.enabled() && Skeleton.ready();
    }

    public static boolean arm() {
        SkeletonLook m = instance;
        return on() && m.yourArm.value;
    }

    public static boolean showArmor() {
        SkeletonLook m = instance;
        return m != null && m.armor.value;
    }
}
