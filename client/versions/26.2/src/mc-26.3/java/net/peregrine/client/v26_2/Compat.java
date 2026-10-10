package net.peregrine.client.v26_2;

/** What differs between the Minecraft versions this adapter covers (this copy: 26.3). */
public final class Compat {

    private Compat() {
    }

    /** How far through its swing the player's arm is (0 to 1). */
    public static float attackAnim(net.minecraft.world.entity.player.Player player, float partial) {
        return 0.0F;  // 26.3: OldAnimationsMixin takes the swing from submitArmWithItem instead
    }

    /** Swings the main hand (anti-AFK). */
    public static void swing(net.minecraft.client.player.LocalPlayer player) {
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, player.getMainHandItem().getSwingAnimation(), false);
    }
}
