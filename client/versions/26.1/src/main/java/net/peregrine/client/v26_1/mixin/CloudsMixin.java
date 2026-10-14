package net.peregrine.client.v26_1.mixin;

import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Static sky: the clouds stop drifting. */
@Mixin(targets = {"net.minecraft.client.renderer.CloudRenderer"})
public abstract class CloudsMixin {

    @Unique
    private static float peregrine$time = Float.NaN;

    /** The drifting time (or, with a separate game time, the part-tick) stays put. */
    @ModifyVariable(method = {"render", "prepare"}, at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float peregrine$stillTime(float time) {
        if (!Hooks.staticClouds) {
            peregrine$time = Float.NaN;
            return time;
        }
        if (Float.isNaN(peregrine$time)) {
            peregrine$time = time;
        }
        return peregrine$time;
    }

    @Unique
    private static long peregrine$gameTime = Long.MIN_VALUE;

    @ModifyVariable(method = {"render", "prepare"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private long peregrine$stillGameTime(long gameTime) {
        if (!Hooks.staticClouds) {
            peregrine$gameTime = Long.MIN_VALUE;
            return gameTime;
        }
        if (peregrine$gameTime == Long.MIN_VALUE) {
            peregrine$gameTime = gameTime;
        }
        return peregrine$gameTime;
    }
}
