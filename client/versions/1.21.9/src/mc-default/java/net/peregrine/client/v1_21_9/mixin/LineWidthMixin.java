package net.peregrine.client.v1_21_9.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Block outline thickness before 1.21.11: Minecraft draws its lines (the block outline
 * among them) at one width set here, so the thicker setting widens that width.
 */
@Mixin(RenderSystem.class)
public abstract class LineWidthMixin {

    @ModifyVariable(method = "lineWidth", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private static float peregrine$thicker(float width) {
        return width > 1f && Hooks.outlineColor != 0 ? width * Hooks.outlineWidth : width;
    }
}
