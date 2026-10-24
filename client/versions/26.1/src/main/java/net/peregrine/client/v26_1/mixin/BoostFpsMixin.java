package net.peregrine.client.v26_1.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.modules.Boost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Performance boost: fewer frames while the game isn't the window you're using. */
@Mixin(FramerateLimitTracker.class)
public abstract class BoostFpsMixin {

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true, require = 0)
    private void peregrine$background(CallbackInfoReturnable<Integer> cir) {
        int cap = Boost.backgroundFps;
        if (cap <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null || mc.getWindow().isFocused() || net.peregrine.client.core.Benchmark.INSTANCE.running()) {
            return;
        }
        int limit = cir.getReturnValue();
        if (limit <= 0 || limit > cap) {
            cir.setReturnValue(cap);
        }
    }
}
