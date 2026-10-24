package net.peregrine.client.v26_1.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.peregrine.client.core.knife.KnifeView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While you hold a knife, its inspect key (F) inspects it instead of swapping your hands. */
@Mixin(KeyboardHandler.class)
public abstract class KnifeKeyMixin {

    @Inject(method = "keyPress(JILnet/minecraft/client/input/KeyEvent;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void peregrine$inspect(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (net.peregrine.client.v26_1.Screens.current() != null) {
            return;
        }
        KnifeView kv = KnifeView.INSTANCE;
        if (event.key() != kv.inspectKey || !kv.holding()) {
            return;
        }
        if (action == 1) {   // pressed (not held or let go)
            kv.inspect();
        }
        ci.cancel();
    }
}
