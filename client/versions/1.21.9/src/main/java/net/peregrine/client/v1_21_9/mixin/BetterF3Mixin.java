package net.peregrine.client.v1_21_9.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.peregrine.client.core.Hooks;
import net.peregrine.client.core.Peregrine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.gui.components.DebugScreenOverlay;

/** Better F3: Minecraft's own debug text is replaced by Peregrine's panels. */
@Mixin(DebugScreenOverlay.class)
public abstract class BetterF3Mixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void peregrine$replace(CallbackInfo ci) {
        Peregrine pc = Peregrine.get();
        if (pc != null && pc.on("better_f3")) {
            ci.cancel();
        }
    }
}
