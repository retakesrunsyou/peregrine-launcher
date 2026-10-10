package net.peregrine.client.v1_21_1.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.LevelRenderer;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Static sky: the clouds stop drifting. */
@Mixin(LevelRenderer.class)
public abstract class CloudsMixin {

    @Unique
    private static int peregrine$ticks = Integer.MIN_VALUE;

    @ModifyExpressionValue(method = "renderClouds", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;ticks:I"))
    private int peregrine$still(int ticks) {
        if (!Hooks.staticClouds) {
            peregrine$ticks = Integer.MIN_VALUE;
            return ticks;
        }
        if (peregrine$ticks == Integer.MIN_VALUE) {
            peregrine$ticks = ticks;
        }
        return peregrine$ticks;
    }
}
