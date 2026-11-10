package net.peregrine.client.v1_21_2;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Peregrine;

/**
 * Toggle sneak stays on, whatever let go of it: while a menu is open or the game is in the
 * background (tabbed out), a crouch you toggled is put back each tick if anything stood you up,
 * and for a moment after you come back. Pressing sneak in the game still stands you up.
 */
final class SneakKeeper {

    private static boolean want;
    private static Object player;
    private static int grace;

    private SneakKeeper() {
    }

    static void tick(Minecraft mc, boolean screenOpen) {
        try {
            Peregrine pc = Peregrine.get();
            if (mc.player == null || mc.options == null || pc == null || !pc.on("toggle_sneak") || !mc.options.toggleCrouch().get()) {
                want = false;
                player = mc.player;
                return;
            }
            if (mc.player != player) {   // respawned or a new world: start standing, like the game does
                player = mc.player;
                want = false;
                grace = 0;
            }
            KeyMapping k = mc.options.keyShift;
            if (screenOpen || !mc.isWindowActive()) {
                grace = 6;
                if (want && !k.isDown()) {
                    k.setDown(true);   // a toggle key: this crouches again
                }
                return;
            }
            if (grace > 0) {   // just back in the game: things that let go of keys on the way back
                grace--;
                if (want && !k.isDown()) {
                    k.setDown(true);
                }
                return;
            }
            want = k.isDown();
        } catch (RuntimeException e) {
            // never get in the way of the game
        }
    }
}
