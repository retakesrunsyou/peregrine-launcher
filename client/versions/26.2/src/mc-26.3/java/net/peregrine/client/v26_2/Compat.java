package net.peregrine.client.v26_2;

/** What differs between the Minecraft versions this adapter covers (this copy: 26.3). */
public final class Compat {

    private Compat() {
    }

    /** How far through its swing the player's arm is (0 to 1). */
    public static float attackAnim(net.minecraft.world.entity.player.Player player, float partial) {
        return 0.0F;  // 26.3 moved the swing into the hand renderer's state; not hooked yet
    }
}
