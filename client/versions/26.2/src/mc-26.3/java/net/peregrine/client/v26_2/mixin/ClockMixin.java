package net.peregrine.client.v26_2.mixin;

import net.minecraft.client.ClientClockManager;
import net.peregrine.client.core.Hooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Time changer: your own time of day (26.1+ keeps time on clocks). */
@Mixin(ClientClockManager.ClientClockInstance.class)
public abstract class ClockMixin {

    @Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true, require = 0)
    private void peregrine$time(CallbackInfoReturnable<Long> cir) {
        if (Hooks.timeOverride >= 0) {
            cir.setReturnValue(Hooks.clockTicks(cir.getReturnValueJ()));
        }
    }
}
