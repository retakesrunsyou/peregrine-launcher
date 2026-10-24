package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.Minecraft;
import net.peregrine.client.v26_1.ZeroDelayHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Once a frame: zero delay (crystals put back, GPU input lag) and the benchmark's frame times. */
@Mixin(Minecraft.class)
public abstract class ZeroDelayInputMixin {

    @Inject(method = "runTick", at = @At("TAIL"), require = 0)
    private void peregrine$frame(boolean tick, CallbackInfo ci) {
        ZeroDelayHooks.frame();
        net.peregrine.client.core.Benchmark.INSTANCE.frame(System.nanoTime());
    }
}
