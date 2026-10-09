package net.peregrine.client.v1_21_1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.GameType;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;

/** Shared helpers for the batch-5 hooks (called from mixins). */
public final class Extras {

    private Extras() {
    }

    /** Returns true when Peregrine's crosshair replaces Minecraft's this frame. */
    public static boolean crosshair(GuiGraphics graphics) {
        Peregrine pc = Peregrine.get();
        if (pc == null || !Hooks.customCrosshair) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getDebugOverlay().showDebugScreen()) {
            return false;  // keep F3's direction crosshair
        }
        boolean spectator = mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR;
        if (mc.options.getCameraType().isFirstPerson() && !spectator) {
            pc.renderCrosshair(new GuiDraw(graphics));
        }
        return true;
    }
}
