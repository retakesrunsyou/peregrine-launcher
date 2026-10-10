package net.peregrine.client.v1_21_2.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Block outline: the color (and, where Minecraft has it, the thickness) picked in the menu. */
@Mixin(LevelRenderer.class)
public abstract class OutlineColorMixin {

    @ModifyVariable(method = {"renderHitOutline", "submitHitOutline"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int peregrine$color(int color) {
        int c = Hooks.outlineColor;
        if (c == 0 || Hooks.drawingOres || color == 0xFF000000) {
            return color;  // Minecraft's, ores keep theirs, and the high-contrast black backing stays
        }
        if (Hooks.outlineHits < 1000) {
            Hooks.outlineHits++;
        }
        return c;
    }
}
